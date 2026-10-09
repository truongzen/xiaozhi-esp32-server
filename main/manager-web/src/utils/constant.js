import i18n from '../i18n/index'

// Placeholder shown when there is no value (localized at call time)
const HAVE_NO_RESULT = () => i18n.t('common.noData')
export default {
    get HAVE_NO_RESULT() {
        return HAVE_NO_RESULT()
    }, // Project configuration
    PAGE: {
        LOGIN: '/login',
    },
    STORAGE_KEY: {
        TOKEN: 'TOKEN',
        PUBLIC_KEY: 'PUBLIC_KEY',
        USER_TYPE: 'USER_TYPE'
    },
    Lang: {
        'zh_cn': 'zh_cn', 'zh_tw': 'zh_tw', 'en': 'en'
    },
    FONT_SIZE: {
        'big': 'big',
        'normal': 'normal',
    }, // Get a key from a map
    get(map, key) {
        return map[key] || HAVE_NO_RESULT()
    }
}
