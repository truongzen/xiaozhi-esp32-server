import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';
import i18n from '../../i18n/index';

const CALLBACK_RETRY_LIMIT = 10;
const CALLBACK_RETRY_DELAY_MS = 2000;
const CALLBACK_RETRY_WINDOW_MS = CALLBACK_RETRY_LIMIT * CALLBACK_RETRY_DELAY_MS;

function retryCallbackRequest(retry, retryCount, onTerminalFailure, error, retryStartedAt) {
  if (!onTerminalFailure) {
    RequestService.reAjaxFun(() => retry(retryCount + 1));
    return;
  }
  const startedAt = retryStartedAt || Date.now();
  if (retryCount >= CALLBACK_RETRY_LIMIT || Date.now() - startedAt >= CALLBACK_RETRY_WINDOW_MS) {
    RequestService.clearRequestTime();
    if (onTerminalFailure) {
      onTerminalFailure(error);
    }
    return;
  }
  setTimeout(() => retry(retryCount + 1, startedAt), CALLBACK_RETRY_DELAY_MS);
}

export default {
  // Get model config list
  getModelList(params, callback) {
    const queryParams = new URLSearchParams({
      modelType: params.modelType,
      modelName: params.modelName || '',
      page: params.page || 0,
      limit: params.limit || 10
    }).toString();

    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/list?${queryParams}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to get model list:', err)
        RequestService.reAjaxFun(() => {
          this.getModelList(params, callback)
        })
      }).send()
  },
  // Get model provider list
  getModelProviders(modelType, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${modelType}/provideTypes`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res.data?.data || [])
      })
      .networkFail((err) => {
        console.error('Failed to get provider list:', err)
        this.$message.error(i18n.t('modelConfig.fetchProvidersFailed'))
        RequestService.reAjaxFun(() => {
          this.getModelProviders(modelType, callback)
        })
      }).send()
  },

  // Add model config
  addModel(params, callback) {
    const { modelType, provideCode, formData } = params;
    const postData = {
      id: formData.id,
      modelCode: formData.modelCode,
      modelName: formData.modelName,
      isDefault: formData.isDefault ? 1 : 0,
      isEnabled: formData.isEnabled ? 1 : 0,
      configJson: formData.configJson,
      docLink: formData.docLink,
      remark: formData.remark,
      sort: formData.sort || 0
    };

    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${modelType}/${provideCode}`)
      .method('POST')
      .data(postData)
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to add model:', err)
        this.$message.error(err.msg || i18n.t('modelConfig.addModelFailed'))
        RequestService.reAjaxFun(() => {
          this.addModel(params, callback)
        })
      }).send()
  },
  // Delete model config
  deleteModel(id, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${id}`)
      .method('DELETE')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to delete model:', err)
        this.$message.error(err.msg || i18n.t('modelConfig.deleteModelFailed'))
        RequestService.reAjaxFun(() => {
          this.deleteModel(id, callback)
        })
      }).send()
  },
  // Get model name list
  getModelNames(modelType, modelName, callback, onTerminalFailure, retryCount = 0, retryStartedAt = 0) {
    const retryWindowStartedAt = retryStartedAt || Date.now();
    const request = RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/names`)
      .method('GET')
      .data({ modelType, modelName })
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((error) => {
        retryCallbackRequest(
          (nextRetryCount, nextRetryStartedAt) => this.getModelNames(
            modelType,
            modelName,
            callback,
            onTerminalFailure,
            nextRetryCount,
            nextRetryStartedAt
          ),
          retryCount,
          onTerminalFailure,
          error,
          retryWindowStartedAt
        );
      });
    if (onTerminalFailure) {
      request.fail((error) => {
        RequestService.clearRequestTime();
        onTerminalFailure(error);
      });
    }
    request.send();
  },
  // Get LLM model name list
  getLlmModelCodeList(modelName, callback, onTerminalFailure, retryCount = 0, retryStartedAt = 0) {
    const retryWindowStartedAt = retryStartedAt || Date.now();
    const request = RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/llm/names`)
      .method('GET')
      .data({ modelName })
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((error) => {
        retryCallbackRequest(
          (nextRetryCount, nextRetryStartedAt) => this.getLlmModelCodeList(
            modelName,
            callback,
            onTerminalFailure,
            nextRetryCount,
            nextRetryStartedAt
          ),
          retryCount,
          onTerminalFailure,
          error,
          retryWindowStartedAt
        );
      });
    if (onTerminalFailure) {
      request.fail((error) => {
        RequestService.clearRequestTime();
        onTerminalFailure(error);
      });
    }
    request.send();
  },
  // Get model voice list
  getModelVoices(modelId, voiceName, callback, onTerminalFailure, retryCount = 0, retryStartedAt = 0) {
    const retryWindowStartedAt = retryStartedAt || Date.now();
    const queryParams = new URLSearchParams({
      voiceName: voiceName || ''
    }).toString();
    const request = RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${modelId}/voices?${queryParams}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((error) => {
        retryCallbackRequest(
          (nextRetryCount, nextRetryStartedAt) => this.getModelVoices(
            modelId,
            voiceName,
            callback,
            onTerminalFailure,
            nextRetryCount,
            nextRetryStartedAt
          ),
          retryCount,
          onTerminalFailure,
          error,
          retryWindowStartedAt
        );
      });
    if (onTerminalFailure) {
      request.fail((error) => {
        RequestService.clearRequestTime();
        onTerminalFailure(error);
      });
    }
    request.send();
  },
  // Get a single model config
  getModelConfig(id, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${id}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to get model config:', err)
        this.$message.error(err.msg || i18n.t('modelConfig.getModelConfigFailed'))
        RequestService.reAjaxFun(() => {
          this.getModelConfig(id, callback)
        })
      }).send()
  },
  // Enable/disable a model
  updateModelStatus(id, status, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/enable/${id}/${status}`)
      .method('PUT')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to update model status:', err)
        this.$message.error(err.msg || i18n.t('modelConfig.updateModelStatusFailed'))
        RequestService.reAjaxFun(() => {
          this.updateModelStatus(id, status, callback)
        })
      }).send()
  },
  // Update model config
  updateModel(params, callback) {
    const { modelType, provideCode, id, formData } = params;
    const payload = {
      ...formData,
      configJson: formData.configJson
    };
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/${modelType}/${provideCode}/${id}`)
      .method('PUT')
      .data(payload)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('Failed to update model:', err);
        this.$message.error(err.msg || i18n.t('modelConfig.updateModelFailed'));
        RequestService.reAjaxFun(() => {
          this.updateModel(params, callback);
        });
      }).send();
  },
  // Set default model
  setDefaultModel(id, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/default/${id}`)
      .method('PUT')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to set default model:', err)
        this.$message.error(err.msg || i18n.t('modelConfig.setDefaultModelFailed'))
        RequestService.reAjaxFun(() => {
          this.setDefaultModel(id, callback)
        })
      }).send()
  },

  /**
   * Get model provider list (supports query parameters)
   * @param {Object} params - Query parameter object, e.g. { name: 'test', modelType: 1 }
   * @param {Function} callback - Callback function
   */
  getModelProvidersPage(params, callback) {
    // Build query parameters
    const queryParams = new URLSearchParams();
    if (params.name) queryParams.append('name', params.name);
    if (params.modelType !== undefined) queryParams.append('modelType', params.modelType);
    if (params.page !== undefined) queryParams.append('page', params.page);
    if (params.limit !== undefined) queryParams.append('limit', params.limit);

    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/provider?${queryParams.toString()}`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        this.$message.error(err.msg || i18n.t('modelConfig.fetchProvidersFailed'));
        RequestService.reAjaxFun(() => {
          this.getModelProviders(params, callback);
        });
      }).send();
  },

  /**
   * Add model provider config
   * @param {Object} params - Request parameter object, e.g. { modelType: '1', providerCode: '1', name: '1', fields: '1', sort: 1 }
   * @param {Function} callback - Success callback
   */
  addModelProvider(params, callback) {
    const postData = {
      modelType: params.modelType || '',
      providerCode: params.providerCode || '',
      name: params.name || '',
      fields: JSON.stringify(params.fields || []),
      sort: params.sort || 0
    };

    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/provider`)
      .method('POST')
      .data(postData)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        console.error('Failed to add model provider:', err)
        this.$message.error(err.msg || i18n.t('providerManagement.addProviderFailed'))
        RequestService.reAjaxFun(() => {
          this.addModelProvider(params, callback);
        });
      }).send();
  },

  /**
   * Update model provider config
   * @param {Object} params - Request parameter object, e.g. { id: '111', modelType: '1', providerCode: '1', name: '1', fields: '1', sort: 1 }
   * @param {Function} callback - Success callback
   */
  updateModelProvider(params, callback) {
    const putData = {
      id: params.id || '',
      modelType: params.modelType || '',
      providerCode: params.providerCode || '',
      name: params.name || '',
      fields: JSON.stringify(params.fields || []),
      sort: params.sort || 0
    };

    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/provider`)
      .method('PUT')
      .data(putData)
      .success((res) => {
        RequestService.clearRequestTime();
        callback(res);
      })
      .networkFail((err) => {
        this.$message.error(err.msg || i18n.t('providerManagement.updateProviderFailed'))
        RequestService.reAjaxFun(() => {
          this.updateModelProvider(params, callback);
        });
      }).send();
  },
  // Delete
  deleteModelProviderByIds(ids, callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/provider/delete`)
      .method('POST')
      .data(ids)
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res);
      })
      .networkFail((err) => {
        this.$message.error(err.msg || i18n.t('providerManagement.deleteProviderFailed'))
        RequestService.reAjaxFun(() => {
          this.deleteModelProviderByIds(ids, callback)
        })
      }).send()
  },
  // Get plugin list
  getPluginFunctionList(params, callback, onTerminalFailure, retryCount = 0, retryStartedAt = 0) {
    const retryWindowStartedAt = retryStartedAt || Date.now();
    const request = RequestService.sendRequest()
      .url(`${getServiceUrl()}/models/provider/plugin/names`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        if (!onTerminalFailure && this.$message) {
          this.$message.error(err.msg || i18n.t('roleConfig.fetchPluginsFailed'));
        }
        retryCallbackRequest(
          (nextRetryCount, nextRetryStartedAt) => this.getPluginFunctionList(
            params,
            callback,
            onTerminalFailure,
            nextRetryCount,
            nextRetryStartedAt
          ),
          retryCount,
          onTerminalFailure,
          err,
          retryWindowStartedAt
        );
      });
    if (onTerminalFailure) {
      request.fail((error) => {
        RequestService.clearRequestTime();
        onTerminalFailure(error);
      });
    }
    request.send()
  },

  // Get RAG model list
  getRAGModels(callback) {
    RequestService.sendRequest()
      .url(`${getServiceUrl()}/datasets/rag-models`)
      .method('GET')
      .success((res) => {
        RequestService.clearRequestTime()
        callback(res)
      })
      .networkFail((err) => {
        console.error('Failed to get RAG model list:', err)
        this.$message.error(err.msg || i18n.t('knowledgeBaseDialog.loadRAGModelsFailed'))
        RequestService.reAjaxFun(() => {
          this.getRAGModels(callback)
        })
      }).send()
  }
}
