package xiaozhi.modules.timbre.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Voice paging parameters DTO
 * 
 * @author zjy
 * @since 2025-3-21
 */
@Data
@Schema(description = "Voice paging parameters")
public class TimbrePageDTO {

    @Schema(description = "Primary key of the corresponding TTS model")
    @NotBlank(message = "{timbre.ttsModelId.require}")
    private String ttsModelId;

    @Schema(description = "Voice name")
    private String name;

    @Schema(description = "Page number")
    private String page;

    @Schema(description = "Number of records per page")
    private String limit;
}
