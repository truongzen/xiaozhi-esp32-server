package xiaozhi.modules.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Agent snapshot pagination query parameters")
public class AgentSnapshotPageDTO {
    @Schema(description = "Current page number, starting from 1", example = "1")
    private Integer page = 1;

    @Schema(description = "Items per page", example = "10")
    private Integer limit = 10;

    @Schema(description = "Version anchor; only query historical snapshots with a version number less than or equal to this one", example = "20")
    private Integer maxVersionNo;

    public int pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    public int limitOrDefault() {
        if (limit == null || limit < 1) {
            return 10;
        }
        return limit;
    }
}
