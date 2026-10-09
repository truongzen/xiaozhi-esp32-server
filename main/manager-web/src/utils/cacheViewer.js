/**
 * Cache viewer utility - checks whether CDN resources have been cached by the Service Worker
 */

/**
 * Get the names of all Service Worker caches
 * @returns {Promise<string[]>} List of cache names
 */
export const getCacheNames = async () => {
  if (!('caches' in window)) {
    return [];
  }
  
  try {
    return await caches.keys();
  } catch (error) {
    console.error('Failed to get cache names:', error);
    return [];
  }
};

/**
 * Get all URLs in the given cache
 * @param {string} cacheName Cache name
 * @returns {Promise<string[]>} List of cached URLs
 */
export const getCacheUrls = async (cacheName) => {
  if (!('caches' in window)) {
    return [];
  }
  
  try {
    const cache = await caches.open(cacheName);
    const requests = await cache.keys();
    return requests.map(request => request.url);
  } catch (error) {
    console.error(`Failed to get URLs of cache ${cacheName}:`, error);
    return [];
  }
};

/**
 * Check whether a specific URL is cached
 * @param {string} url URL to check
 * @returns {Promise<boolean>} Whether it is cached
 */
export const isUrlCached = async (url) => {
  if (!('caches' in window)) {
    return false;
  }
  
  try {
    const cacheNames = await getCacheNames();
    for (const cacheName of cacheNames) {
      const cache = await caches.open(cacheName);
      const match = await cache.match(url);
      if (match) {
        return true;
      }
    }
    return false;
  } catch (error) {
    console.error(`Failed to check whether URL ${url} is cached:`, error);
    return false;
  }
};

/**
 * Get the cache status of all CDN resources on the current page
 * @returns {Promise<Object>} Cache status object
 */
export const checkCdnCacheStatus = async () => {
  // Look up resources in the CDN caches
  const cdnCaches = ['cdn-stylesheets', 'cdn-scripts'];
  const results = {
    css: [],
    js: [],
    totalCached: 0,
    totalNotCached: 0
  };
  
  for (const cacheName of cdnCaches) {
    try {
      const urls = await getCacheUrls(cacheName);
      
      // Separate CSS and JS resources
      for (const url of urls) {
        if (url.endsWith('.css')) {
          results.css.push({ url, cached: true });
        } else if (url.endsWith('.js')) {
          results.js.push({ url, cached: true });
        }
        results.totalCached++;
      }
    } catch (error) {
      console.error(`Failed to get info for cache ${cacheName}:`, error);
    }
  }
  
  return results;
};

/**
 * Clear all Service Worker caches
 * @returns {Promise<boolean>} Whether clearing succeeded
 */
export const clearAllCaches = async () => {
  if (!('caches' in window)) {
    return false;
  }
  
  try {
    const cacheNames = await getCacheNames();
    for (const cacheName of cacheNames) {
      await caches.delete(cacheName);
    }
    return true;
  } catch (error) {
    console.error('Failed to clear all caches:', error);
    return false;
  }
};

/**
 * Print the cache status to the console
 */
export const logCacheStatus = async () => {
  console.group('Service Worker cache status');
  
  const cacheNames = await getCacheNames();
  console.log('Caches found:', cacheNames);
  
  for (const cacheName of cacheNames) {
    const urls = await getCacheUrls(cacheName);
    console.group(`Cache: ${cacheName} (${urls.length} items)`);
    urls.forEach(url => console.log(url));
    console.groupEnd();
  }
  
  console.groupEnd();
  return cacheNames.length > 0;
}; 