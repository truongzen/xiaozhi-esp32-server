//Feature configuration utility
import Api from "@/apis/api";
import store from "@/store";
import i18n from "@/i18n";

class FeatureManager {
    constructor() {
        this.defaultFeatures = {
            voiceprintRecognition: {
                name: 'feature.voiceprintRecognition.name',
                enabled: false,
                description: 'feature.voiceprintRecognition.description'
            },
            voiceClone: {
                name: 'feature.voiceClone.name',
                enabled: false,
                description: 'feature.voiceClone.description'
            },
            knowledgeBase: {
                name: 'feature.knowledgeBase.name',
                enabled: false,
                description: 'feature.knowledgeBase.description'
            },
            mcpAccessPoint: {
                name: 'feature.mcpAccessPoint.name',
                enabled: false,
                description: 'feature.mcpAccessPoint.description'
            },
            vad: {
                name: 'feature.vad.name',
                enabled: false,
                description: 'feature.vad.description'
            },
            asr: {
                name: 'feature.asr.name',
                enabled: false,
                description: 'feature.asr.description'
            },
            addressBook: {
                name: 'feature.addressBook.name',
                enabled: false,
                description: 'feature.addressBook.description'
            }
        };
        this.currentFeatures = { ...this.defaultFeatures }; // Current in-memory config
        this.initialized = false;
        this.initPromise = null;
    }

    /**
     * Wait for initialization to finish
     */
    async waitForInitialization() {
        if (!this.initPromise) {
            this.initPromise = this.init();
        }
        await this.initPromise;
        return this.initialized;
    }

    /**
     * Initialize feature config
     */
    async init() {
        try {
            // Get config from the pub-config API
            const config = await this.getConfigFromPubConfig();
            if (config) {
                this.currentFeatures = { ...config }; // Keep in memory
                this.initialized = true;
                return;
            }
        } catch (error) {
            console.warn('Failed to get config from the pub-config API:', error);
        }

        // pub-config API failed, use the default config
        this.currentFeatures = { ...this.defaultFeatures }; // Keep the default config in memory
        this.initialized = true;
    }

    /**
     * Update the config cache
     */
    updateConfigCache(config) {
        store.commit('setPubConfig', config);
        localStorage.setItem('pubConfig', JSON.stringify(config));
    }

    /**
     * Get config from the pub-config API
     */
    async getConfigFromPubConfig() {
        return new Promise((resolve) => {
            // Call the pub-config API directly to get the config
            Api.user.getPubConfig((result) => {
                // Check the structure of the result
                if (result && result.status === 200) {
                    // Check whether there is a data field
                    if (result.data) {
                        const configCache = result.data.data || {};
                        // Check whether there is a code field; if so, decide based on code
                        if (result.data.code !== undefined) {
                            if (result.data.code === 0 && result.data.data && result.data.data.systemWebMenu) {
                                try {
                                    let config;
                                    if (typeof result.data.data.systemWebMenu === 'string') {
                                        // If it is a string, parse it as JSON
                                        config = JSON.parse(result.data.data.systemWebMenu);
                                    } else {
                                        // If it is already an object, use it directly
                                        config = result.data.data.systemWebMenu;
                                    }

                                    // Check whether the config contains a features object
                                    if (config && config.features) {
                                        // Make sure the knowledgeBase feature exists and is configured correctly
                                        if (!config.features.knowledgeBase) {
                                            console.warn('knowledgeBase feature missing from config, merging with default config');
                                            config.features = { ...this.defaultFeatures, ...config.features };
                                        }
                                        resolve(config.features);
                                    } else {
                                        console.warn('features object missing from config, using default config');
                                        resolve(this.defaultFeatures);
                                    }
                                    configCache.systemWebMenu = config;
                                } catch (error) {
                                    console.warn('Failed to process systemWebMenu config:', error);
                                    resolve(null);
                                }
                            } else {
                                console.warn('API returned a non-zero code or is missing required data, using default config');
                                resolve(null);
                            }
                        } else {
                            // If there is no code field, check systemWebMenu directly
                            if (result.data && result.data.systemWebMenu) {
                                try {
                                    let config;
                                    if (typeof result.data.systemWebMenu === 'string') {
                                        // If it is a string, parse it as JSON
                                        config = JSON.parse(result.data.systemWebMenu);
                                    } else {
                                        // If it is already an object, use it directly
                                        config = result.data.systemWebMenu;
                                    }

                                    // Check whether the config contains a features object
                                    if (config && config.features) {
                                        // Make sure the knowledgeBase feature exists and is configured correctly
                                        if (!config.features.knowledgeBase) {
                                            console.warn('knowledgeBase feature missing from config, merging with default config');
                                            config.features = { ...this.defaultFeatures, ...config.features };
                                        }
                                        resolve(config.features);
                                    } else {
                                        console.warn('features object missing from config, using default config');
                                        resolve(this.defaultFeatures);
                                    }
                                    configCache.systemWebMenu = config;
                                } catch (error) {
                                    console.warn('Failed to process systemWebMenu config:', error);
                                    resolve(null);
                                }
                            } else {
                                console.warn('API response is missing systemWebMenu data, using default config');
                                resolve(null);
                            }
                        }
                        this.updateConfigCache(configCache)
                    } else {
                        console.warn('API response is missing the data field, using default config');
                        resolve(null);
                    }
                } else {
                    console.warn('pub-config API call failed, using default config');
                    resolve(null);
                }
            });
        });
    }

    /**
     * Get the current config
     */
    getCurrentConfig() {
        // Return the current in-memory config
        return this.currentFeatures;
    }

    /**
     * Save config to the backend API
     */
    async saveConfig(config) {
        try {
            // Update the in-memory config
            this.currentFeatures = { ...config };

            // Save to the backend API asynchronously
            this.saveConfigToAPI(config).catch(error => {
                console.warn('Failed to save config to API:', error);
            }).finally(() => {
                this.init()
            });

            // Dispatch the config change event
            window.dispatchEvent(new CustomEvent('featureConfigChanged', {
                detail: config
            }));
        } catch (error) {
            console.error('Failed to save feature config:', error);
        }
    }

    /**
     * Save config to the backend API
     */
    async saveConfigToAPI(config) {
        return new Promise((resolve) => {
            // Update the parameter directly using the known ID (600)
            Api.admin.updateParam(
                {
                    id: 600,
                    paramCode: 'system-web.menu',
                    paramValue: JSON.stringify({
                        features: config,
                        groups: {
                            featureManagement: ["voiceprintRecognition", "voiceClone", "knowledgeBase", "mcpAccessPoint", "addressBook"],
                            voiceManagement: ["vad", "asr"]
                        }
                    }),
                    valueType: 'json',
                    remark: i18n.t('feature.systemWebMenuRemark')
                },
                (updateResult) => {
                    if (updateResult.code === 0) {
                        resolve();
                    } else {
                        // If the update fails (e.g. the parameter does not exist), log it but do not block saving to localStorage
                        console.warn('Failed to update parameter:', updateResult.msg);
                        resolve(); // Do not block saving to localStorage
                    }
                },
                (error) => {
                    console.warn('Failed to update parameter:', error);
                    resolve(); // Do not block saving to localStorage
                }
            );
        });
    }



    /**
     * Get all feature configs
     */
    getAllFeatures() {
        return this.getCurrentConfig();
    }

    /**
     * Get a simplified config object (for the home page components)
     */
    getConfig() {
        const features = this.getAllFeatures();
        return {
            voiceprintRecognition: features.voiceprintRecognition?.enabled || false,
            voiceClone: features.voiceClone?.enabled || false,
            knowledgeBase: features.knowledgeBase?.enabled || false,
            mcpAccessPoint: features.mcpAccessPoint?.enabled || false,
            vad: features.vad?.enabled || false,
            asr: features.asr?.enabled || false,
            addressBook: features.addressBook?.enabled || false
        };
    }

    /**
     * Get the status of a feature
     */
    getFeatureStatus(featureKey) {
        const features = this.getAllFeatures();
        return features[featureKey]?.enabled || false;
    }

    /**
     * Set feature status
     */
    setFeatureStatus(featureKey, enabled) {
        const features = this.getAllFeatures();
        if (features[featureKey]) {
            features[featureKey].enabled = enabled;
            this.saveConfig(features);
            return true;
        }
        return false;
    }

    /**
     * Enable a feature
     */
    enableFeature(featureKey) {
        return this.setFeatureStatus(featureKey, true);
    }

    /**
     * Disable a feature
     */
    disableFeature(featureKey) {
        return this.setFeatureStatus(featureKey, false);
    }

    /**
     * Toggle feature status
     */
    toggleFeature(featureKey) {
        const currentStatus = this.getFeatureStatus(featureKey);
        return this.setFeatureStatus(featureKey, !currentStatus);
    }

    /**
     * Reset all features to their defaults
     */
    resetToDefault() {
        this.saveConfig(this.defaultFeatures);
    }

    /**
     * Batch update feature status
     */
    updateFeatures(featureUpdates) {
        const features = this.getAllFeatures();
        Object.keys(featureUpdates).forEach(featureKey => {
            if (features[featureKey]) {
                features[featureKey].enabled = featureUpdates[featureKey];
            } else if (this.defaultFeatures[featureKey]) {
                features[featureKey] = { ...this.defaultFeatures[featureKey] };
                features[featureKey].enabled = featureUpdates[featureKey];
            }
        });
        this.saveConfig(features);
    }

    /**
     * Get the list of enabled features
     */
    getEnabledFeatures() {
        const features = this.getAllFeatures();
        return Object.keys(features).filter(key => features[key].enabled);
    }

    /**
     * Check whether a feature is enabled
     */
    isFeatureEnabled(featureKey) {
        return this.getFeatureStatus(featureKey);
    }
}

// Create the singleton instance
const featureManager = new FeatureManager();

export default featureManager;