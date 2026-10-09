package xiaozhi.modules.correctword.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Compact replacement word VO (for devices)")
public class CorrectWordSimpleVO {

    @Schema(description = "Original word")
    private String sourceWord;

    @Schema(description = "Replacement word")
    private String targetWord;
}
