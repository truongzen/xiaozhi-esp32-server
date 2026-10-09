<template>
  <div id="app">
    <router-view />
    <cache-viewer v-if="isCDNEnabled" :visible.sync="showCacheViewer" />
  </div>
</template>

<style lang="scss">
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}

nav {
  padding: 30px;

  a {
    font-weight: bold;
    color: #2c3e50;

    &.router-link-exact-active {
      color: #42b983;
    }
  }
}

.copyright {
  padding: 0 !important;
  color: rgb(0, 0, 0);
  font-size: 12px;
  font-weight: 400;
  margin-top: auto;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.el-message {
  top: 70px !important;
}
</style>
<script>
import CacheViewer from '@/components/CacheViewer.vue';
import { logCacheStatus } from '@/utils/cacheViewer';

export default {
  name: 'App',
  components: {
    CacheViewer
  },
  data() {
    return {
      showCacheViewer: false,
      isCDNEnabled: process.env.VUE_APP_USE_CDN === 'true'
    };
  },
  created() {
    // Hydrate store state
    this.$store.commit('setUserInfo', JSON.parse(localStorage.getItem('userInfo') || '{}'));
    this.$store.commit('setPubConfig', JSON.parse(localStorage.getItem('pubConfig') || '{}'));
    this.updateDocumentTitle();
  },
  watch: {
    // Keep the browser tab title in the current UI language
    '$i18n.locale'() {
      this.updateDocumentTitle();
    }
  },
  mounted() {
    // If this is a mobile device and VUE_APP_H5_URL is set, redirect to the H5 page
    if (this.isMobileDevice() && process.env.VUE_APP_H5_URL) {
      window.location.href = process.env.VUE_APP_H5_URL;
      return;
    }
    
    // Only add these events and features when CDN is enabled
    if (this.isCDNEnabled) {
      // Global shortcut Alt+C opens the cache viewer
      document.addEventListener('keydown', this.handleKeyDown);

      // Expose a cache check method on window for debugging
      window.checkCDNCacheStatus = () => {
        this.showCacheViewer = true;
      };

      // Print a hint to the console
      console.info(
        '%c[' + this.$t('system.name') + '] ' + this.$t('cache.cdnEnabled'),
        'color: #409EFF; font-weight: bold;'
      );
      console.info(
        'Press Alt+C or run checkCDNCacheStatus() in the console to view the CDN cache status'
      );

      // Check Service Worker status
      this.checkServiceWorkerStatus();
    } else {
      console.info(
        '%c[' + this.$t('system.name') + '] ' + this.$t('cache.cdnDisabled'),
        'color: #67C23A; font-weight: bold;'
      );
    }
  },
  beforeDestroy() {
    // Only remove the listener when CDN is enabled
    if (this.isCDNEnabled) {
      document.removeEventListener('keydown', this.handleKeyDown);
    }
  },
  methods: {
    updateDocumentTitle() {
      document.title = this.$t('system.consoleTitle');
    },
    handleKeyDown(e) {
      // Alt+C shortcut
      if (e.altKey && e.key === 'c') {
        this.showCacheViewer = true;
      }
    },
    isMobileDevice() {
      // Detect a mobile device
      return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    },
    
    async checkServiceWorkerStatus() {
      // Check whether a Service Worker is registered
      if ('serviceWorker' in navigator) {
        try {
          const registrations = await navigator.serviceWorker.getRegistrations();
          if (registrations.length > 0) {
            console.info(
              '%c[' + this.$t('system.name') + '] ' + this.$t('cache.serviceWorkerRegistered'),
              'color: #67C23A; font-weight: bold;'
            );

            // Print cache status to the console
            setTimeout(async () => {
              const hasCaches = await logCacheStatus();
              if (!hasCaches) {
                console.info(
                '%c[' + this.$t('system.name') + '] ' + this.$t('cache.noCacheDetected'),
                'color: #E6A23C; font-weight: bold;'
              );

              // Extra hints in development
              if (process.env.NODE_ENV === 'development') {
                console.info(
                  '%c[' + this.$t('system.name') + '] ' + this.$t('cache.swDevEnvWarning'),
                  'color: #E6A23C; font-weight: bold;'
                );
                console.info(this.$t('cache.swCheckMethods'));
                console.info('1. ' + this.$t('cache.swCheckMethod1'));
                console.info('2. ' + this.$t('cache.swCheckMethod2'));
                console.info('3. ' + this.$t('cache.swCheckMethod3'));
              }
              }
            }, 2000);
          } else {
            console.info(
                  '%c[' + this.$t('system.name') + '] ' + this.$t('cache.serviceWorkerNotRegistered'),
                  'color: #F56C6C; font-weight: bold;'
                );

                if (process.env.NODE_ENV === 'development') {
                  console.info(
                    '%c[' + this.$t('system.name') + '] ' + this.$t('cache.swDevEnvNormal'),
                    'color: #E6A23C; font-weight: bold;'
                  );
                  console.info(this.$t('cache.swProdOnly'));
                  console.info(this.$t('cache.swTestingTitle'));
                  console.info('1. ' + this.$t('cache.swTestingStep1'));
                  console.info('2. ' + this.$t('cache.swTestingStep2'));
                }
          }
        } catch (error) {
          console.error('Failed to check Service Worker status:', error);
        }
      } else {
          console.warn(this.$t('cache.swNotSupported'));
        }
    }
  }
};
</script>