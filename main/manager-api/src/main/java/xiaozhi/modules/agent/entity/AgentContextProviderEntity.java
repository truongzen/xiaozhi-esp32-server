package xiaozhi.modules.agent.entity;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.modules.agent.dto.ContextProviderDTO;
import xiaozhi.modules.agent.typehandler.ContextProviderListTypeHandler;

@Data
@TableName(value = "ai_agent_context_provider", autoResultMap = true)
@Schema(description = "Agent context provider configuration")
public class AgentContextProviderEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "Primary key")
    private String id;

    @Schema(description = "Agent ID")
    private String agentId;

    @Schema(description = "Context provider configuration")
    @TableField(typeHandler = ContextProviderListTypeHandler.class)
    private List<ContextProviderDTO> contextProviders;

    @Schema(description = "Creator")
    private Long creator;

    @Schema(description = "Creation time")
    private Date createdAt;

    @Schema(description = "Updater")
    private Long updater;

    @Schema(description = "Update time")
    private Date updatedAt;
}
