package xiaozhi.modules.agent.dto;

import java.io.Serializable;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Context provider configuration DTO")
public class ContextProviderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "URL")
    private String url;

    @Schema(description = "Request headers")
    private Map<String, Object> headers;
}
