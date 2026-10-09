// Import the request modules
import admin from './module/admin.js'
import agent from './module/agent.js'
import device from './module/device.js'
import dict from './module/dict.js'
import model from './module/model.js'
import ota from './module/ota.js'
import timbre from "./module/timbre.js"
import user from './module/user.js'
import voiceClone from './module/voiceClone.js'
import voiceResource from './module/voiceResource.js'
import knowledgeBase from './module/knowledgeBase.js'
import correctWord from './module/correctWord.js'
import addressBook from './module/addressBook.js'



/**
 * API base URL
 * In development it is read from .env.development
 * In production builds it is read from .env.production
 */
const DEV_API_SERVICE = process.env.VUE_APP_API_BASE_URL

/**
 * Return the API URL for the current environment
 * @returns {string}
 */
export function getServiceUrl() {
    return DEV_API_SERVICE
}

/** Request service wrapper */
export default {
    getServiceUrl,
    user,
    admin,
    agent,
    device,
    model,
    timbre,
    ota,
    dict,
    voiceResource,
    voiceClone,
    knowledgeBase,
    correctWord,
    addressBook
  }
