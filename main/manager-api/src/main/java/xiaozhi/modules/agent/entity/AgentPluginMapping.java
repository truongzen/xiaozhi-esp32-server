package xiaozhi.modules.agent.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Unique agent-plugin mapping table
 * 
 * @TableName ai_agent_plugin_mapping
 */
@Data
@TableName(value = "ai_agent_plugin_mapping")
@Schema(description = "Unique agent-plugin mapping table")
public class AgentPluginMapping implements Serializable {
    /**
     * Primary key
     */
    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "Mapping primary key ID")
    private Long id;

    /**
     * Agent ID
     */
    @Schema(description = "Agent ID")
    private String agentId;

    /**
     * Plugin ID
     */
    @Schema(description = "Plugin ID")
    private String pluginId;

    /**
     * Plugin parameters (JSON format)
     */
    @Schema(description = "Plugin parameters (JSON format)")
    private String paramInfo;

    // Redundant field, used to look up the plugin's Provider_code when querying plugins by ID; see the DAO XML file
    @TableField(exist = false)
    @Schema(description = "Plugin provider_code, corresponding to table ai_model_provider")
    private String providerCode;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}