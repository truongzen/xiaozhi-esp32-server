const { defineConfig } = require('@vue/cli-service');
const dotenv = require('dotenv');
// TerserPlugin minifies JavaScript
const TerserPlugin = require('terser-webpack-plugin');
// CompressionPlugin enables Gzip compression
const CompressionPlugin = require('compression-webpack-plugin')
// BundleAnalyzerPlugin analyzes the built bundle
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
// WorkboxPlugin generates the Service Worker
const { InjectManifest } = require('workbox-webpack-plugin');
// path module

const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

// Make sure the .env file is loaded
dotenv.config();

// CDN resource list, also made available to the Service Worker
const cdnResources = {
  css: [
    'https://unpkg.com/element-ui@2.15.14/lib/theme-chalk/index.css',
    'https://cdnjs.cloudflare.com/ajax/libs/normalize/8.0.1/normalize.min.css'
  ],
  js: [
    'https://unpkg.com/vue@2.6.14/dist/vue.min.js',
    'https://unpkg.com/vue-router@3.6.5/dist/vue-router.min.js',
    'https://unpkg.com/vuex@3.6.2/dist/vuex.min.js',
    'https://unpkg.com/element-ui@2.15.14/lib/index.js',
    'https://unpkg.com/axios@0.27.2/dist/axios.min.js',
    'https://unpkg.com/opus-decoder@0.7.7/dist/opus-decoder.min.js'
  ]
};

// Whether to use the CDN
const useCDN = process.env.VUE_APP_USE_CDN === 'true';

module.exports = defineConfig({
  productionSourceMap: process.env.NODE_ENV !== 'production', // No source maps in production
  devServer: {
    port: 8001, // Use port 8001
    proxy: {
      '/xiaozhi': {
        target: 'http://127.0.0.1:8002',
        changeOrigin: true
      }
    },
    client: {
      overlay: false, // Do not show the webpack error overlay
    },
  },
  publicPath: process.env.VUE_APP_PUBLIC_PATH || "/",
  chainWebpack: config => {

    // Adjust the HTML plugin config to inject CDN links
    config.plugin('html')
      .tap(args => {
        // Use the CDN depending on configuration
        if (process.env.NODE_ENV === 'production' && useCDN) {
          args[0].cdn = cdnResources;
        }
        return args;
      });

    // Code splitting
    config.optimization.splitChunks({
      chunks: 'all',
      minSize: 20000,
      maxSize: 250000,
      cacheGroups: {
        vendors: {
          name: 'chunk-vendors',
          test: /[\\/]node_modules[\\/]/,
          priority: -10,
          chunks: 'initial',
        },
        common: {
          name: 'chunk-common',
          minChunks: 2,
          priority: -20,
          chunks: 'initial',
          reuseExistingChunk: true,
        },
      }
    });

    // Enable optimizations
    config.optimization.usedExports(true);
    config.optimization.concatenateModules(true);
    config.optimization.minimize(true);
  },
  configureWebpack: config => {
    if (process.env.NODE_ENV === 'production') {
      // Enable multi-threaded compilation
      config.optimization = {
        minimize: true,
        minimizer: [
          new TerserPlugin({
            parallel: true,
            terserOptions: {
              compress: {
                drop_console: true,
                drop_debugger: true,
                pure_funcs: ['console.log']
              }
            }
          })
        ]
      };
      config.plugins.push(
        new CompressionPlugin({
          algorithm: 'gzip',
          test: /\.(js|css|html|svg)$/,
          threshold: 20480,
          minRatio: 0.8
        })
      );

      // Add the Service Worker only when the CDN is used
      config.plugins.push(
        new InjectManifest({
          swSrc: path.resolve(__dirname, 'src/service-worker.js'),
          swDest: 'service-worker.js',
          exclude: [/\.map$/, /asset-manifest\.json$/],
          maximumFileSizeToCacheInBytes: 5 * 1024 * 1024, // 5MB
          // Custom Service Worker injection point
          injectionPoint: 'self.__WB_MANIFEST',
          // Extra data passed to the Service Worker
          additionalManifestEntries: useCDN ?
            [{ url: 'cdn-mode', revision: 'enabled' }] :
            [{ url: 'cdn-mode', revision: 'disabled' }]
        })
      );

      // When using the CDN, exclude dependencies via externals
      if (useCDN) {
        config.externals = {
          'vue': 'Vue',
          'vue-router': 'VueRouter',
          'vuex': 'Vuex',
          'element-ui': 'ELEMENT',
          'axios': 'axios',
          'opus-decoder': 'OpusDecoder'
        };
      } else {
        // Without the CDN, leave externals unset so webpack bundles all dependencies
        config.externals = {};
      }

      if (process.env.ANALYZE === 'true') {  // Controlled by an environment variable
        config.plugins.push(
          new BundleAnalyzerPlugin({
            analyzerMode: 'server',    // Local server mode
            openAnalyzer: true,        // Open the browser automatically
            analyzerPort: 8888         // Port number
          })
        );
      }
      config.cache = {
        type: 'filesystem',  // Filesystem cache
        cacheDirectory: path.resolve(__dirname, '.webpack_cache'),  // Custom cache directory
        allowCollectingMemory: true,  // Allow memory collection
        compression: 'gzip',  // Gzip-compress the cache
        maxAge: 5184000000, // Cache lifetime: about 2 months
        buildDependencies: {
          config: [__filename]  // Invalidate the cache when this config file changes
        }
      };
    }
  },
  // Expose CDN resource info to service-worker.js
  pwa: {
    workboxOptions: {
      skipWaiting: true,
      clientsClaim: true
    }
  }
});
