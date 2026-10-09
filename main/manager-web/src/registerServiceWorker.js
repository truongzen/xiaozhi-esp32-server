/* eslint-disable no-console */
import i18n from './i18n';

export const register = () => {
  if (process.env.NODE_ENV === 'production' && 'serviceWorker' in navigator) {
    window.addEventListener('load', () => {
      const swUrl = `${process.env.BASE_URL}service-worker.js`;
      
      console.info(`[Xiaozhi] Trying to register the Service Worker, URL: ${swUrl}`);
      
      // First check whether a Service Worker is already registered
      navigator.serviceWorker.getRegistrations().then(registrations => {
        if (registrations.length > 0) {
          console.info('[Xiaozhi] Existing Service Worker registration found, checking for updates');
        }
        
        // Continue registering the Service Worker
        navigator.serviceWorker
          .register(swUrl)
          .then(registration => {
            console.info('[Xiaozhi] Service Worker registered successfully');
            
            // Update handling
            registration.onupdatefound = () => {
              const installingWorker = registration.installing;
              if (installingWorker == null) {
                return;
              }
              installingWorker.onstatechange = () => {
                if (installingWorker.state === 'installed') {
                  if (navigator.serviceWorker.controller) {
                    // Updated content has been cached, notify the user to refresh
                    console.log('[Xiaozhi] New content is available, please refresh the page');
                    // Show the update notification
                    const updateNotification = document.createElement('div');
                    updateNotification.style.cssText = `
                      position: fixed;
                      bottom: 20px;
                      right: 20px;
                      background: #409EFF;
                      color: white;
                      padding: 12px 20px;
                      border-radius: 4px;
                      box-shadow: 0 2px 12px 0 rgba(0,0,0,.1);
                      z-index: 9999;
                    `;
                    updateNotification.innerHTML = `
                      <div style="display: flex; align-items: center;">
                        <span style="margin-right: 10px;">${i18n.t('cache.newVersionAvailable')}</span>
                        <button style="background: white; color: #409EFF; border: none; padding: 5px 10px; border-radius: 3px; cursor: pointer;">${i18n.t('cache.refreshPage')}</button>
                      </div>
                    `;
                    document.body.appendChild(updateNotification);
                    updateNotification.querySelector('button').addEventListener('click', () => {
                      window.location.reload();
                    });
                  } else {
                    // All good, the Service Worker was installed successfully
                    console.log('[Xiaozhi] Content has been cached for offline use');
                    
                    // Initialize caches here
                    setTimeout(() => {
                      // Warm up the CDN cache
                      const cdnUrls = [
                        'https://unpkg.com/element-ui@2.15.14/lib/theme-chalk/index.css',
                        'https://cdnjs.cloudflare.com/ajax/libs/normalize/8.0.1/normalize.min.css',
                        'https://unpkg.com/vue@2.6.14/dist/vue.min.js',
                        'https://unpkg.com/vue-router@3.6.5/dist/vue-router.min.js',
                        'https://unpkg.com/vuex@3.6.2/dist/vuex.min.js',
                        'https://unpkg.com/element-ui@2.15.14/lib/index.js',
                        'https://unpkg.com/axios@0.27.2/dist/axios.min.js',
                        'https://unpkg.com/opus-decoder@0.7.7/dist/opus-decoder.min.js'
                      ];
                      
                      // Warm up the cache
                      cdnUrls.forEach(url => {
                        fetch(url, { mode: 'no-cors' }).catch(err => {
                          console.log(`Failed to warm up cache for ${url}`, err);
                        });
                      });
                    }, 2000);
                  }
                }
              };
            };
          })
          .catch(error => {
            console.error('Service Worker registration failed:', error);
            
            if (error.name === 'TypeError' && error.message.includes('Failed to register a ServiceWorker')) {
              console.warn('[Xiaozhi] Network error while registering the Service Worker, CDN resources may not be cached');
              if (process.env.NODE_ENV === 'production') {
                console.info(
                  'Possible causes: 1. the server is not configured with the correct MIME type 2. server SSL certificate problem 3. the server did not return the service-worker.js file'
                );
              }
            }
          });
      });
    });
  }
};

export const unregister = () => {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.ready
      .then(registration => {
        registration.unregister();
      })
      .catch(error => {
        console.error(error.message);
      });
  }
}; 