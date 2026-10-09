package xiaozhi.modules.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Basic display data for LLM models
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LlmModelBasicInfoDTO extends ModelBasicInfoDTO{
    private String type;
}