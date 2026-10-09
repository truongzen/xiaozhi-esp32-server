package xiaozhi.modules.knowledge.rag;

import java.util.List;
import java.util.Map;

import xiaozhi.modules.knowledge.dto.dataset.DatasetDTO;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.knowledge.dto.KnowledgeFilesDTO;
import xiaozhi.modules.knowledge.dto.document.DocumentDTO;
import xiaozhi.modules.knowledge.dto.document.ChunkDTO;
import xiaozhi.modules.knowledge.dto.document.RetrievalDTO;
import java.util.function.Consumer;

/**
 * Abstract base class for knowledge base API adapters
 * Defines common knowledge base operations, supporting multiple backend API implementations
 */
public abstract class KnowledgeBaseAdapter {

        /**
         * Get the adapter type identifier
         * 
         * @return adapter type (e.g. ragflow, milvus, pinecone)
         */
        public abstract String getAdapterType();

        /**
         * Initialize the adapter configuration
         * 
         * @param config configuration parameters
         */
        public abstract void initialize(Map<String, Object> config);

        /**
         * Validate whether the configuration is valid
         * 
         * @param config configuration parameters
         * @return validation result
         */
        public abstract boolean validateConfig(Map<String, Object> config);

        /**
         * Paged query of the document list
         * 
         * @param datasetId   knowledge base ID
         * @param queryParams query parameters
         * @param page        page number
         * @param limit       page size
         * @return paged data
         */
        public abstract PageData<KnowledgeFilesDTO> getDocumentList(String datasetId,
                        DocumentDTO.ListReq req);

        /**
         * Get document details by document ID
         * 
         * @param datasetId  knowledge base ID
         * @param documentId document ID
         * @return document details (strongly typed InfoVO)
         */
        public abstract DocumentDTO.InfoVO getDocumentById(String datasetId, String documentId);

        /**
         * Upload a document to the knowledge base
         * 
         * @param req upload request parameters
         * @return uploaded document information
         */
        public abstract KnowledgeFilesDTO uploadDocument(DocumentDTO.UploadReq req);

        /**
         * Paged query of the document list by status
         * 
         * @param datasetId knowledge base ID
         * @param status    document parsing status
         * @param page      page number
         * @param limit     page size
         * @return paged data
         */
        public abstract PageData<KnowledgeFilesDTO> getDocumentListByStatus(String datasetId,
                        Integer status,
                        Integer page,
                        Integer limit);

        /**
         * Delete documents (supports batch delete)
         * 
         * @param datasetId knowledge base ID
         * @param req       request object containing the document ID list
         */
        public abstract void deleteDocument(String datasetId, DocumentDTO.BatchIdReq req);

        /**
         * Parse documents (chunking)
         * 
         * @param datasetId   knowledge base ID
         * @param documentIds document ID list
         * @return parse result
         */
        public abstract boolean parseDocuments(String datasetId, List<String> documentIds);

        /**
         * List the chunks of the specified document
         * 
         * @param datasetId  knowledge base ID
         * @param documentId document ID
         * @param req        list request parameters (paging, keywords, etc.)
         * @return chunk list VO
         */
        public abstract ChunkDTO.ListVO listChunks(String datasetId,
                        String documentId,
                        ChunkDTO.ListReq req);

        /**
         * Retrieval test - retrieve relevant chunks from the knowledge base
         * 
         * @param req retrieval test request parameters
         * @return retrieval test result
         */
        public abstract RetrievalDTO.ResultVO retrievalTest(
                        RetrievalDTO.TestReq req);

        /**
         * Test the connection
         * 
         * @return connection test result
         */
        public abstract boolean testConnection();

        /**
         * Get adapter status information
         * 
         * @return status information
         */
        public abstract Map<String, Object> getStatus();

        /**
         * Get the supported configuration parameters
         * 
         * @return configuration parameter descriptions
         */
        public abstract Map<String, Object> getSupportedConfig();

        /**
         * Get the default configuration
         * 
         * @return default configuration
         */
        public abstract Map<String, Object> getDefaultConfig();

        /**
         * Create a dataset
         * 
         * @param req creation parameters
         * @return dataset details
         */
        public abstract DatasetDTO.InfoVO createDataset(DatasetDTO.CreateReq req);

        /**
         * Update a dataset
         * 
         * @param datasetId dataset ID
         * @param req       update parameters
         * @return dataset details
         */
        public abstract DatasetDTO.InfoVO updateDataset(String datasetId, DatasetDTO.UpdateReq req);

        /**
         * Delete datasets
         * 
         * @param req delete request parameters (including the ID list)
         * @return batch operation result
         */
        public abstract DatasetDTO.BatchOperationVO deleteDataset(DatasetDTO.BatchIdReq req);

        /**
         * Get the number of documents in a dataset
         *
         * @param datasetId dataset ID
         * @return number of documents
         */
        public abstract Integer getDocumentCount(String datasetId);

        /**
         * Get full dataset information (name, description, document count, etc.)
         * Used to detect deletion on RAGFlow and to sync name/description changes
         *
         * @param datasetId dataset ID
         * @return dataset details, or null if it does not exist on RAGFlow
         */
        public abstract DatasetDTO.InfoVO getDatasetInfo(String datasetId);

        /**
         * Send a streaming request (SSE)
         * 
         * @param endpoint API endpoint
         * @param body     request body
         * @param onData   data callback
         */
        public abstract void postStream(String endpoint, Object body, Consumer<String> onData);

        /**
         * SearchBot query
         *
         * @param config RAG configuration
         * @param body   request body
         * @param onData data callback
         * @return response object
         */
        public abstract Object postSearchBotAsk(Map<String, Object> config, Object body,
                        Consumer<String> onData);

        /**
         * AgentBot conversation
         *
         * @param config  RAG configuration
         * @param agentId Agent ID
         * @param body    request body
         * @param onData  data callback
         */
        public abstract void postAgentBotCompletion(Map<String, Object> config, String agentId, Object body,
                        Consumer<String> onData);
}