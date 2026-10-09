package xiaozhi.modules.agent.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent creation DTO
 * Used only for creating agents; does not contain the id, agentCode and sort fields, which are generated or defaulted by the system
 */
@Data
@Schema(description = "Agent creation object")
public class AgentCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Agent name", example = "Customer service assistant")
    @NotBlank(message = "Agent name cannot be empty")
    private String agentName;
}