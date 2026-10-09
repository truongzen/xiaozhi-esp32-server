import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';
import i18n from '../../i18n/index';

export default {
    // Get dictionary type list
    getDictTypeList(params, callback) {
        const queryParams = new URLSearchParams({
            dictType: params.dictType || '',
            dictName: params.dictName || '',
            page: params.page || 1,
            limit: params.limit || 10
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/type/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to get dictionary type list:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.getDictTypeListFailed'))
                RequestService.reAjaxFun(() => {
                    this.getDictTypeList(params, callback)
                })
            }).send()
    },

    // Get dictionary type details
    getDictTypeDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/type/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to get dictionary type details:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.getDictTypeDetailFailed'))
                RequestService.reAjaxFun(() => {
                    this.getDictTypeDetail(id, callback)
                })
            }).send()
    },

    // Add dictionary type
    addDictType(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/type/save`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to add dictionary type:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.addDictTypeFailed'))
                RequestService.reAjaxFun(() => {
                    this.addDictType(data, callback)
                })
            }).send()
    },

    // Update dictionary type
    updateDictType(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/type/update`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to update dictionary type:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.updateDictTypeFailed'))
                RequestService.reAjaxFun(() => {
                    this.updateDictType(data, callback)
                })
            }).send()
    },

    // Delete dictionary type
    deleteDictType(ids, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/type/delete`)
            .method('POST')
            .data(ids)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to delete dictionary type:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.deleteDictTypeFailed'))
                RequestService.reAjaxFun(() => {
                    this.deleteDictType(ids, callback)
                })
            }).send()
    },

    // Get dictionary data list
    getDictDataList(params, callback) {
        const queryParams = new URLSearchParams({
            dictTypeId: params.dictTypeId,
            dictLabel: params.dictLabel || '',
            dictValue: params.dictValue || '',
            page: params.page || 1,
            limit: params.limit || 10
        }).toString();

        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/data/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to get dictionary data list:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.getDictDataListFailed'))
                RequestService.reAjaxFun(() => {
                    this.getDictDataList(params, callback)
                })
            }).send()
    },

    // Get dictionary data details
    getDictDataDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/data/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to get dictionary data details:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.getDictDataDetailFailed'))
                RequestService.reAjaxFun(() => {
                    this.getDictDataDetail(id, callback)
                })
            }).send()
    },

    // Add dictionary data
    addDictData(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/data/save`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to add dictionary data:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.addDictDataFailed'))
                RequestService.reAjaxFun(() => {
                    this.addDictData(data, callback)
                })
            }).send()
    },

    // Update dictionary data
    updateDictData(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/data/update`)
            .method('PUT')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to update dictionary data:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.updateDictDataFailed'))
                RequestService.reAjaxFun(() => {
                    this.updateDictData(data, callback)
                })
            }).send()
    },

    // Delete dictionary data
    deleteDictData(ids, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/admin/dict/data/delete`)
            .method('POST')
            .data(ids)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('Failed to delete dictionary data:', err)
                this.$message.error(err.msg || i18n.t('dictManagement.deleteDictDataFailed'))
                RequestService.reAjaxFun(() => {
                    this.deleteDictData(ids, callback)
                })
            }).send()
    },

    // Get dictionary data list
    getDictDataByType(dictType) {
        return new Promise((resolve, reject) => {
            RequestService.sendRequest()
                .url(`${getServiceUrl()}/admin/dict/data/type/${dictType}`)
                .method('GET')
                .success((res) => {
                    RequestService.clearRequestTime()
                    if (res.data && res.data.code === 0) {
                        resolve(res.data)
                    } else {
                        reject(new Error(res.data?.msg || i18n.t('dictManagement.getDictDataListFailed')))
                    }
                })
                .networkFail((err) => {
                    console.error('Failed to get dictionary data list:', err)
                    reject(err)
                }).send()
        })
    }

} 