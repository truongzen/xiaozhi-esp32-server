package xiaozhi.modules.agent.dto;

import lombok.Data;

/**
 * DTO for saving an agent voiceprint
 *
 * @author zjy
 */
@Data
public class AgentVoicePrintSaveDTO {
    /**
     * Associated agent ID
     */
    private String agentId;
    /**
     * Audio file ID
     */
    private String audioId;
    /**
     * Name of the person the voiceprint belongs to
     */
    private String sourceName;
    /**
     * Description of the person the voiceprint belongs to
     */
    private String introduce;
}
