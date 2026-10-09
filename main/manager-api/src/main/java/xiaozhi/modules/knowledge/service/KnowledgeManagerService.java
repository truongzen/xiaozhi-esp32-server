package xiaozhi.modules.knowledge.service;

import java.util.List;

/**
 * Domain orchestration service for the knowledge base module
 * Handles complex business flows spanning KnowledgeBase and KnowledgeFiles, eliminating circular dependencies between services.
 */
public interface KnowledgeManagerService {

    /**
     * Cascade delete a knowledge base and all of its documents (both local DB and RAGFlow remote data)
     * 
     * @param datasetId knowledge base ID
     */
    void deleteDatasetWithFiles(String datasetId);

    /**
     * Batch cascade delete knowledge bases
     * 
     * @param datasetIds list of knowledge base IDs
     */
    void batchDeleteDatasetsWithFiles(List<String> datasetIds);
}
