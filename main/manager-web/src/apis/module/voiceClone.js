import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

export default {
    // Paged query of voice resources
    getVoiceCloneList(params, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/voiceClone`)
            .method('GET')
            .data(params)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .networkFail((err) => {
                console.error('Failed to get voice list:', err);
                RequestService.reAjaxFun(() => {
                    this.getVoiceCloneList(params, callback);
                });
            }).send();
    },

    // Upload audio file
    uploadVoice(formData, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/voiceClone/upload`)
            .method('POST')
            .data(formData)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .networkFail((err) => {
                console.error('Failed to upload audio:', err);
                RequestService.reAjaxFun(() => {
                    this.uploadVoice(formData, callback);
                });
            }).send();
    },

    // Update voice name
    updateName(params, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/voiceClone/updateName`)
            .method('POST')
            .data(params)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .networkFail((err) => {
                console.error('Failed to update name:', err);
                RequestService.reAjaxFun(() => {
                    this.updateName(params, callback);
                });
            }).send();
    },

    // Get audio download ID
    getAudioId(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/voiceClone/audio/${id}`)
            .method('POST')
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .networkFail((err) => {
                console.error('Failed to get audio ID:', err);
                RequestService.reAjaxFun(() => {
                    this.getAudioId(id, callback);
                });
            }).send();
    },

    // Get audio playback URL
    getPlayVoiceUrl(uuid) {
        return `${getServiceUrl()}/voiceClone/play/${uuid}`;
    },

    // Clone voice from audio
    cloneAudio(params, callback, errorCallback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/voiceClone/cloneAudio`)
            .method('POST')
            .data(params)
            .success((res) => {
                RequestService.clearRequestTime();
                callback(res);
            })
            .fail((res) => {
                // Business failure callback
                RequestService.clearRequestTime();
                if (errorCallback) {
                    errorCallback(res);
                } else {
                    callback(res);
                }
            })
            .networkFail((err) => {
                console.error('Upload failed:', err);
                RequestService.reAjaxFun(() => {
                    this.cloneAudio(params, callback, errorCallback);
                });
            }).send();
    }
}
