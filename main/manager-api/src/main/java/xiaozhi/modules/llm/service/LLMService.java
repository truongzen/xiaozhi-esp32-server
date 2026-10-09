package xiaozhi.modules.llm.service;

/**
 * LLM service interface
 * Supports calling multiple large language models
 */
public interface LLMService {

    /**
     * Generate a chat history summary
     * 
     * @param conversation   conversation content
     * @param promptTemplate prompt template
     * @return summary result
     */
    String generateSummary(String conversation, String promptTemplate);

    /**
     * Generate a chat history summary (using the default prompt)
     * 
     * @param conversation conversation content
     * @return summary result
     */
    String generateSummary(String conversation);

    /**
     * Generate a chat history summary (with the specified model ID)
     * 
     * @param conversation conversation content
     * @param modelId      model ID
     * @return summary result
     */
    String generateSummaryWithModel(String conversation, String modelId);

    /**
     * Generate a chat history summary (with the specified model ID and prompt template)
     * 
     * @param conversation   conversation content
     * @param promptTemplate prompt template
     * @param modelId        model ID
     * @return summary result
     */
    String generateSummary(String conversation, String promptTemplate, String modelId);

    /**
     * Generate a chat history summary (merged with historical memory)
     * 
     * @param conversation   conversation content
     * @param historyMemory  historical memory
     * @param promptTemplate prompt template
     * @param modelId        model ID
     * @return summary result
     */
    String generateSummaryWithHistory(String conversation, String historyMemory, String promptTemplate, String modelId);

    /**
     * Check whether the service is available
     * 
     * @return whether it is available
     */
    boolean isAvailable();

    /**
     * Check whether the service for the specified model is available
     * 
     * @param modelId model ID
     * @return whether it is available
     */
    boolean isAvailable(String modelId);

    /**
     * Generate a conversation title
     * 
     * @param conversation conversation content
     * @param modelId      model ID
     * @return title (about 15 characters)
     */
    String generateTitle(String conversation, String modelId);
}