package xiaozhi.modules.agent.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Agent memory update DTO
 */
@Data
@Schema(description = "Agent memory update object")
public class AgentMemoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Memory summary", example = "Build a growable dynamic memory network that keeps key information within limited space while intelligently tracking how the information evolves\n" +
            "Based on the conversation history, summarize the user's important information to provide more personalized service in future conversations", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String summaryMemory;
}
