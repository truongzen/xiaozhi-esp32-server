package xiaozhi.modules.agent.dto;

import lombok.Data;

/**
 * DTO for updating an agent voiceprint
 *
 * @author zjy
 */
@Data
public class AgentVoicePrintUpdateDTO {
    /**
     * Agent voiceprint ID
     */
    private String id;
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
