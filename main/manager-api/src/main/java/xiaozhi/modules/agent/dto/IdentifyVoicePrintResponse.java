package xiaozhi.modules.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Response object of the voiceprint identification API
 */
@Data
public class IdentifyVoicePrintResponse {
    /**
     * ID of the best-matching voiceprint
     */
    @JsonProperty("speaker_id")
    private String speakerId;
    /**
     * Voiceprint score
     */
    private Double score;
}
