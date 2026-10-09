package xiaozhi.modules.knowledge.rag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;

/**
 * Knowledge base adapter factory
 * Creates and manages knowledge base API adapters of different types
 */
@Slf4j
public class KnowledgeBaseAdapterFactory {

    // Registered adapter type mapping
    private static final Map<String, Class<? extends KnowledgeBaseAdapter>> adapterRegistry = new HashMap<>();

    // Adapter instance cache
    private static final Map<String, KnowledgeBaseAdapter> adapterCache = new ConcurrentHashMap<>();

    // Maximum number of cached instances, to prevent memory leaks (Issue 9)
    private static final int MAX_CACHE_SIZE = 50;

    static {
        // Register built-in adapter types
        registerAdapter("ragflow", xiaozhi.modules.knowledge.rag.impl.RAGFlowAdapter.class);
        // More adapter types can be registered here
    }

    /**
     * Register a new adapter type
     * 
     * @param adapterType  adapter type identifier
     * @param adapterClass adapter class
     */
    public static void registerAdapter(String adapterType, Class<? extends KnowledgeBaseAdapter> adapterClass) {
        if (adapterRegistry.containsKey(adapterType)) {
            log.warn("Adapter type '{}' already exists and will be overwritten", adapterType);
        }
        adapterRegistry.put(adapterType, adapterClass);
        log.info("Registered adapter type: {} -> {}", adapterType, adapterClass.getSimpleName());
    }

    /**
     * Get an adapter instance
     * 
     * @param adapterType adapter type
     * @param config      configuration parameters
     * @return adapter instance
     */
    public static KnowledgeBaseAdapter getAdapter(String adapterType, Map<String, Object> config) {
        String cacheKey = buildCacheKey(adapterType, config);

        // Check whether an instance already exists in the cache
        if (adapterCache.containsKey(cacheKey)) {
            log.debug("Got adapter instance from cache: {}", cacheKey);
            return adapterCache.get(cacheKey);
        }

        // Create a new adapter instance
        KnowledgeBaseAdapter adapter = createAdapter(adapterType, config);

        // Cache the adapter instance (with capacity check)
        if (adapterCache.size() >= MAX_CACHE_SIZE) {
            log.warn("Adapter cache reached its limit ({}), clearing it to protect memory", MAX_CACHE_SIZE);
            // Simple approach: clear everything; an LRU is recommended in production
            adapterCache.clear();
        }

        adapterCache.put(cacheKey, adapter);
        log.info("Created and cached adapter instance: {}", cacheKey);

        return adapter;
    }

    /**
     * Get an adapter instance (without configuration)
     * 
     * @param adapterType adapter type
     * @return adapter instance
     */
    public static KnowledgeBaseAdapter getAdapter(String adapterType) {
        return getAdapter(adapterType, null);
    }

    /**
     * Get all registered adapter types
     * 
     * @return set of adapter types
     */
    public static Set<String> getRegisteredAdapterTypes() {
        return adapterRegistry.keySet();
    }

    /**
     * Check whether an adapter type is registered
     * 
     * @param adapterType adapter type
     * @return whether it is registered
     */
    public static boolean isAdapterTypeRegistered(String adapterType) {
        return adapterRegistry.containsKey(adapterType);
    }

    /**
     * Clear the adapter cache
     */
    public static void clearCache() {
        int cacheSize = adapterCache.size();
        adapterCache.clear();
        log.info("Cleared adapter cache, {} instances removed", cacheSize);
    }

    /**
     * Remove the cache for a specific adapter type
     * 
     * @param adapterType adapter type
     */
    public static void removeCacheByType(String adapterType) {
        int removedCount = 0;
        for (String cacheKey : adapterCache.keySet()) {
            if (cacheKey.startsWith(adapterType + "@")) {
                adapterCache.remove(cacheKey);
                removedCount++;
            }
        }
        log.info("Removed cache for adapter type '{}', {} instances removed", adapterType, removedCount);
    }

    /**
     * Get adapter factory status information
     * 
     * @return status information
     */
    public static Map<String, Object> getFactoryStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("registeredAdapterTypes", adapterRegistry.keySet());
        status.put("cachedAdapterCount", adapterCache.size());
        status.put("cacheKeys", adapterCache.keySet());
        return status;
    }

    /**
     * Create an adapter instance
     * 
     * @param adapterType adapter type
     * @param config      configuration parameters
     * @return adapter instance
     */
    private static KnowledgeBaseAdapter createAdapter(String adapterType, Map<String, Object> config) {
        if (!adapterRegistry.containsKey(adapterType)) {
            throw new RenException(ErrorCode.RAG_ADAPTER_TYPE_NOT_SUPPORTED,
                    "Unsupported adapter type: " + adapterType);
        }

        try {
            Class<? extends KnowledgeBaseAdapter> adapterClass = adapterRegistry.get(adapterType);
            KnowledgeBaseAdapter adapter = adapterClass.getDeclaredConstructor().newInstance();

            // Initialize the adapter
            if (config != null) {
                adapter.initialize(config);

                // Validate the configuration
                if (!adapter.validateConfig(config)) {
                    throw new RenException(ErrorCode.RAG_CONFIG_VALIDATION_FAILED,
                            "Adapter configuration validation failed: " + adapterType);
                }
            }

            log.info("Successfully created adapter instance: {}", adapterType);
            return adapter;

        } catch (Exception e) {
            log.error("Failed to create adapter instance: {}", adapterType, e);
            throw new RenException(ErrorCode.RAG_ADAPTER_CREATION_FAILED,
                    "Failed to create adapter: " + adapterType + ", error: " + e.getMessage());
        }
    }

    /**
     * Build the cache key
     * 
     * @param adapterType adapter type
     * @param config      configuration parameters
     * @return cache key
     */
    private static String buildCacheKey(String adapterType, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return adapterType + "@default";
        }

        // Generate the cache key from the configuration parameters
        StringBuilder keyBuilder = new StringBuilder(adapterType + "@");

        // Use the configuration hash as part of the cache key
        int configHash = config.hashCode();
        keyBuilder.append(configHash);

        return keyBuilder.toString();
    }
}