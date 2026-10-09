import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

/**
 * Get the auth token
 */
function getAuthToken() {
  return localStorage.getItem('token') || '';
}

/**
 * Generic API request wrapper
 * @param {Object} config - Request config
 * @param {string} config.url - Request URL
 * @param {string} config.method - Request method
 * @param {Object} [config.data] - Request data
 * @param {Object} [config.headers] - Extra request headers
 * @param {Function} config.callback - Success callback
 * @param {Function} [config.errorCallback] - Error callback
 * @param {string} [config.errorMessage] - Error message (logged to the console)
 * @param {Function} [config.retryFunction] - Retry function
 */
function makeApiRequest(config) {
  const token = getAuthToken();
  const { url, method, data, headers, callback, errorCallback, errorMessage, retryFunction } = config;

  const requestBuilder = RequestService.sendRequest()
    .url(url)
    .method(method)
    .header({
      'Authorization': `Bearer ${token}`,
      ...headers
    });

  if (data) {
    requestBuilder.data(data);
  }

  requestBuilder
    .success((res) => {
      RequestService.clearRequestTime();
      callback(res);
    })
    .fail((err) => {
      console.error(errorMessage || 'Operation failed', err);
      if (errorCallback) {
        errorCallback(err);
      }
    })
    .networkFail(() => {
      if (retryFunction) {
        RequestService.reAjaxFun(() => {
          retryFunction();
        });
      }
    }).send();
}

/**
 * Knowledge base management APIs
 */
export default {
  /**
   * Get knowledge base list
   * @param {Object} params - Query parameters
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  getKnowledgeBaseList(params, callback, errorCallback) {
    const queryParams = new URLSearchParams({
      page: params.page,
      page_size: params.page_size,
      name: params.name || ''
    }).toString();

    makeApiRequest({
      url: `${getServiceUrl()}/datasets?${queryParams}`,
      method: 'GET',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to get knowledge base list',
      retryFunction: () => this.getKnowledgeBaseList(params, callback, errorCallback)
    });
  },

  /**
   * Create knowledge base
   * @param {Object} data - Knowledge base data
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  createKnowledgeBase(data, callback, errorCallback) {
    console.log('createKnowledgeBase called with data:', data);
    console.log('API URL:', `${getServiceUrl()}/datasets`);

    makeApiRequest({
      url: `${getServiceUrl()}/datasets`,
      method: 'POST',
      data: data,
      headers: { 'Content-Type': 'application/json' },
      callback: (res) => {
        console.log('createKnowledgeBase success response:', res);
        callback(res);
      },
      errorCallback: (err) => {
        console.error('Failed to create knowledge base:', err);
        if (err.response) {
          console.error('Error response data:', err.response.data);
          console.error('Error response status:', err.response.status);
        }
        if (errorCallback) {
          errorCallback(err);
        }
      },
      errorMessage: 'Failed to create knowledge base',
      retryFunction: () => this.createKnowledgeBase(data, callback, errorCallback)
    });
  },

  /**
   * Update knowledge base
   * @param {string} datasetId - Knowledge base ID
   * @param {Object} data - Update data
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  updateKnowledgeBase(datasetId, data, callback, errorCallback) {
    console.log('updateKnowledgeBase called with datasetId:', datasetId, 'data:', data);
    console.log('API URL:', `${getServiceUrl()}/datasets/${datasetId}`);

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}`,
      method: 'PUT',
      data: data,
      headers: { 'Content-Type': 'application/json' },
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to update knowledge base',
      retryFunction: () => this.updateKnowledgeBase(datasetId, data, callback, errorCallback)
    });
  },

  /**
   * Delete a single knowledge base
   * @param {string} datasetId - Knowledge base ID
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  deleteKnowledgeBase(datasetId, callback, errorCallback) {
    console.log('deleteKnowledgeBase called with datasetId:', datasetId);
    console.log('API URL:', `${getServiceUrl()}/datasets/${datasetId}`);

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}`,
      method: 'DELETE',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to delete knowledge base',
      retryFunction: () => this.deleteKnowledgeBase(datasetId, callback, errorCallback)
    });
  },

  /**
   * Batch delete knowledge bases
   * @param {string|Array} ids - Knowledge base ID string or array
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  deleteKnowledgeBases(ids, callback, errorCallback) {
    // Make sure ids is a correctly formatted string
    const idsStr = Array.isArray(ids) ? ids.join(',') : ids;

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/batch?ids=${idsStr}`,
      method: 'DELETE',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to batch delete knowledge bases',
      retryFunction: () => this.deleteKnowledgeBases(ids, callback, errorCallback)
    });
  },

  /**
   * Get document list
   * @param {string} datasetId - Knowledge base ID
   * @param {Object} params - Query parameters
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  getDocumentList(datasetId, params, callback, errorCallback) {
    const queryParams = new URLSearchParams({
      page: params.page,
      page_size: params.page_size,
      name: params.name || ''
    }).toString();

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/documents?${queryParams}`,
      method: 'GET',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to get document list',
      retryFunction: () => this.getDocumentList(datasetId, params, callback, errorCallback)
    });
  },

  /**
   * Upload document
   * @param {string} datasetId - Knowledge base ID
   * @param {Object} formData - Form data
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  uploadDocument(datasetId, formData, callback, errorCallback) {
    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/documents`,
      method: 'POST',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to upload document',
      retryFunction: () => this.uploadDocument(datasetId, formData, callback, errorCallback)
    });
  },

  /**
   * Parse document
   * @param {string} datasetId - Knowledge base ID
   * @param {string} documentId - Document ID
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  parseDocument(datasetId, documentId, callback, errorCallback) {
    const requestBody = {
      document_ids: [documentId]
    };

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/chunks`,
      method: 'POST',
      data: requestBody,
      headers: { 'Content-Type': 'application/json' },
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to parse document',
      retryFunction: () => this.parseDocument(datasetId, documentId, callback, errorCallback)
    });
  },

  /**
   * Delete document
   * @param {string} datasetId - Knowledge base ID
   * @param {string} documentId - Document ID
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  deleteDocument(datasetId, documentId, callback, errorCallback) {
    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/documents/${documentId}`,
      method: 'DELETE',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to delete document',
      retryFunction: () => this.deleteDocument(datasetId, documentId, callback, errorCallback)
    });
  },

  /**
   * Get document chunk list
   * @param {string} datasetId - Knowledge base ID
   * @param {string} documentId - Document ID
   * @param {Object} params - Query parameters
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  listChunks(datasetId, documentId, params, callback, errorCallback) {
    let queryParams = new URLSearchParams({
      page: params.page || 1,
      page_size: params.page_size || 10
    }).toString();

    // Add the keyword search parameter
    if (params.keywords) {
      queryParams += `&keywords=${encodeURIComponent(params.keywords)}`;
    }

    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/documents/${documentId}/chunks?${queryParams}`,
      method: 'GET',
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Failed to get chunk list',
      retryFunction: () => this.listChunks(datasetId, documentId, params, callback, errorCallback)
    });
  },

  /**
   * Retrieval test
   * @param {string} datasetId - Knowledge base ID
   * @param {Object} data - Retrieval test parameters
   * @param {Function} callback - Callback function
   * @param {Function} errorCallback - Error callback
   */
  retrievalTest(datasetId, data, callback, errorCallback) {
    makeApiRequest({
      url: `${getServiceUrl()}/datasets/${datasetId}/retrieval-test`,
      method: 'POST',
      data: data,
      headers: { 'Content-Type': 'application/json' },
      callback: callback,
      errorCallback: errorCallback,
      errorMessage: 'Retrieval test failed',
      retryFunction: () => this.retrievalTest(datasetId, data, callback, errorCallback)
    });
  }

};