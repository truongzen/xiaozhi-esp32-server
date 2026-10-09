import 'element-ui/lib/theme-chalk/index.css';
import 'normalize.css/normalize.css'; // A modern alternative to CSS resets
import Vue from 'vue';
import ElementUI from 'element-ui';
import App from './App.vue';
import router from './router';
import store from './store';
import i18n from './i18n';
import locale from 'element-ui/lib/locale'
import './styles/global.scss';
import { register as registerServiceWorker } from './registerServiceWorker';
import featureManager from './utils/featureManager';

// Event bus for communication between components
Vue.prototype.$eventBus = new Vue();

Vue.use(ElementUI);
locale.i18n((key, value) => i18n.t(key, value))

Vue.config.productionTip = false

// Register the Service Worker
registerServiceWorker();

// Create the Vue instance
new Vue({
  router,
  store,
  i18n,
  render: function (h) { return h(App) }
}).$mount('#app')
