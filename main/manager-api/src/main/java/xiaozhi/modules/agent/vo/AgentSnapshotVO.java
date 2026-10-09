package xiaozhi.modules.agent.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.agent.dto.AgentSnapshotDataDTO;

@Data
@Schema(description = "Agent configuration snapshot")
public class AgentSnapshotVO {
    private String id;
    private String agentId;
    @Schema(description = "Owner user ID, i.e. the owner of the agent this snapshot belongs to")
    private Long userId;
    private Integer versionNo;
    private List<String> changedFields;
    private List<String> fieldOrder;
    private String source;
    @Schema(description = "Source snapshot ID of the restore; only set on versions produced by a restore")
    private String restoreFromSnapshotId;
    @Schema(description = "Source version number of the restore; only set on versions produced by a restore")
    private Integer restoreFromVersionNo;
    @Schema(description = "Creator, i.e. the operator who triggered this snapshot write")
    private Long creator;
    private Date createdAt;
    private AgentSnapshotDataDTO snapshotData;
    private AgentSnapshotDataDTO afterSnapshotData;
    @Schema(description = "Masked current configuration for the restore preview; only set by the detail endpoint")
    private AgentSnapshotDataDTO currentSnapshotData;
    @Schema(description = "Current configuration state fingerprint for the restore preview; only set by the detail endpoint")
    private String currentStateToken;
}
