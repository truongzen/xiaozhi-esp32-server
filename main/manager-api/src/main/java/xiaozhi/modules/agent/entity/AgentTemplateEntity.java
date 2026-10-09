package xiaozhi.modules.agent.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * Agent configuration template table
 * 
 * @TableName ai_agent_template
 */
@TableName(value = "ai_agent_template")
@Data
public class AgentTemplateEntity implements Serializable {
    /**
     * Agent unique identifier
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * Agent code
     */
    private String agentCode;

    /**
     * Agent name
     */
    private String agentName;

    /**
     * Speech recognition (ASR) model identifier
     */
    private String asrModelId;

    /**
     * Voice activity detection (VAD) identifier
     */
    private String vadModelId;

    /**
     * LLM identifier
     */
    private String llmModelId;

    /**
     * VLLM identifier
     */
    private String vllmModelId;

    /**
     * TTS model identifier
     */
    private String ttsModelId;

    /**
     * Voice identifier
     */
    private String ttsVoiceId;

    /**
     * Voice language
     */
    private String ttsLanguage;

    /**
     * TTS volume
     */
    private Integer ttsVolume;

    /**
     * TTS speech rate
     */
    private Integer ttsRate;

    /**
     * TTS pitch
     */
    private Integer ttsPitch;

    /**
     * Memory model identifier
     */
    private String memModelId;

    /**
     * Intent model identifier
     */
    private String intentModelId;

    /**
     * Chat history setting (0 = do not record, 1 = record text only, 2 = record text and audio)
     */
    private Integer chatHistoryConf;

    /**
     * Role settings
     */
    private String systemPrompt;

    /**
     * Memory summary
     */
    private String summaryMemory;
    /**
     * Language code
     */
    private String langCode;

    /**
     * Interaction language
     */
    private String language;

    /**
     * Sort weight
     */
    private Integer sort;

    /**
     * Creator ID
     */
    private Long creator;

    /**
     * Creation time
     */
    private Date createdAt;

    /**
     * Updater ID
     */
    private Long updater;

    /**
     * Update time
     */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}