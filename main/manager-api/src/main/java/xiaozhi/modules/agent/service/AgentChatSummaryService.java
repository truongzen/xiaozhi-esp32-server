package xiaozhi.modules.agent.service;

/**
 * Agent chat history summary service interface
 */
public interface AgentChatSummaryService {

    /**
     * Generate a chat history summary by session ID and save it to the agent memory
     * 
     * @param sessionId session ID
     * @return save result
     */
    boolean generateAndSaveChatSummary(String sessionId);

    /**
     * Generate a chat title by session ID and save it
     *
     * @param sessionId session ID
     * @return whether it succeeded
     */
    boolean generateAndSaveChatTitle(String sessionId);
}