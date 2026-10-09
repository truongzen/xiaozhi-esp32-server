package xiaozhi.modules.agent.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * Agent session list DTO
 */
@Data
public class AgentChatSessionDTO {
    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Session time
     */
    private LocalDateTime createdAt;

    /**
     * Number of chat messages
     */
    private Integer chatCount;

    /**
     * Session title
     */
    private String title;
}