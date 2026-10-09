package xiaozhi.modules.agent.service.biz;

import xiaozhi.modules.agent.dto.AgentChatHistoryReportDTO;

/**
 * Agent chat history business logic layer
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
public interface AgentChatHistoryBizService {

    /**
     * Chat report method
     *
     * @param agentChatHistoryReportDTO input object with the information needed for the chat report
     *                                  e.g. device MAC address, file type, content
     * @return upload result, true for success, false for failure
     */
    Boolean report(AgentChatHistoryReportDTO agentChatHistoryReportDTO);
}
