import Vue from 'vue';
import VueI18n from 'vue-i18n';
import zhCN from './zh_CN';
import zhTW from './zh_TW';
import en from './en';
import de from './de';
import vi from './vi';
import ptBR from './pt_BR';

import enLocale from 'element-ui/lib/locale/lang/en'
import zhLocale from 'element-ui/lib/locale/lang/zh-CN'
import twLocale from 'element-ui/lib/locale/lang/zh-TW'
import deLocale from 'element-ui/lib/locale/lang/de'
import viLocale from 'element-ui/lib/locale/lang/vi'
import ptBRLocale from 'element-ui/lib/locale/lang/pt-br'


Vue.use(VueI18n);

// Get the language setting from local storage; otherwise use the browser language or the default
const getDefaultLanguage = () => {
  const savedLang = localStorage.getItem('userLanguage');
  if (savedLang) {
    return savedLang;
  }
  const browserLang = navigator.language || navigator.userLanguage;
  if (browserLang.indexOf('zh') === 0) {
    if (browserLang === 'zh-TW' || browserLang === 'zh-HK' || browserLang === 'zh-MO') {
      return 'zh_TW';
    }
    return 'zh_CN';
  }
  if (browserLang.indexOf('de') === 0) {
    return 'de';
  }
  if (browserLang.indexOf('vi') === 0) {
    return 'vi';
  }
  if (browserLang === 'pt-BR' || browserLang === 'pt') {
    return 'pt_BR';
  }
  return 'en';
};

const i18n = new VueI18n({
  locale: getDefaultLanguage(),
  fallbackLocale: 'en',
  messages: {
    'zh_CN': { ...zhLocale, ...zhCN },
    'zh_TW': { ...twLocale, ...zhTW },
    'en': { ...en, ...enLocale },
    'de': { ...de, ...deLocale },
    'vi': { ...vi, ...viLocale },
    'pt_BR': { ...ptBR, ...ptBRLocale }
  }
});

export default i18n;

// Provide a method to switch the language
export const changeLanguage = (lang) => {
  i18n.locale = lang;
  localStorage.setItem('userLanguage', lang);
  // Notify components that the language has changed
  Vue.prototype.$eventBus.$emit('languageChanged', lang);
};