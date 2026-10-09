package xiaozhi.modules.agent.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xiaozhi.common.utils.JsonUtils;

/**
 * Agent update DTO
 * Used only for updating agents; the id field is required to identify the agent to update
 * All other fields are optional; only the provided fields are updated
 */
@Data
@Schema(description = "Agent update object")
public class AgentUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "Agent code", example = "AGT_1234567890", nullable = true)
    private String agentCode;

    @Schema(description = "Agent name", example = "Customer service assistant", nullable = true)
    private String agentName;

    @Schema(description = "Speech recognition (ASR) model identifier", example = "asr_model_02", nullable = true)
    private String asrModelId;

    @Schema(description = "Voice activity detection (VAD) identifier", example = "vad_model_02", nullable = true)
    private String vadModelId;

    @Schema(description = "LLM identifier", example = "llm_model_02", nullable = true)
    private String llmModelId;

    @Schema(description = "Small language model (SLM) identifier", example = "slm_model_02", nullable = true)
    private String slmModelId;

    @Schema(description = "VLLM identifier", example = "vllm_model_02", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String vllmModelId;

    @Schema(description = "TTS model identifier", example = "tts_model_02", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String ttsModelId;

    @Schema(description = "Voice identifier", example = "voice_02", nullable = true)
    private String ttsVoiceId;

    @Schema(description = "Voice language", example = "普通话", nullable = true)
    private String ttsLanguage;

    @Schema(description = "TTS volume", example = "50", nullable = true)
    private Integer ttsVolume;

    @Schema(description = "TTS speech rate", example = "50", nullable = true)
    private Integer ttsRate;

    @Schema(description = "TTS pitch", example = "50", nullable = true)
    private Integer ttsPitch;

    @Schema(description = "Memory model identifier", example = "mem_model_02", nullable = true)
    private String memModelId;

    @Schema(description = "Intent model identifier", example = "intent_model_02", nullable = true)
    private String intentModelId;

    @Schema(description = "Plugin function info", nullable = true)
    private List<FunctionInfo> functions;

    @Schema(description = "Role settings", example = "You are a professional customer service assistant responsible for answering user questions and providing help", nullable = true)
    private String systemPrompt;

    @Schema(description = "Memory summary", example = "Build a growable dynamic memory network that keeps key information within limited space while intelligently tracking how the information evolves\n"
            + "Based on the conversation history, summarize the user's important information to provide more personalized service in future conversations", nullable = true)
    private String summaryMemory;

    @Schema(description = "Chat history setting (0 = do not record, 1 = record text only, 2 = record text and audio)", example = "3", nullable = true)
    private Integer chatHistoryConf;

    @Schema(description = "Language code", example = "zh_CN", nullable = true)
    private String langCode;

    @Schema(description = "Interaction language", example = "中文", nullable = true)
    private String language;

    @Schema(description = "Sort", example = "1", nullable = true)
    private Integer sort;

    @Schema(description = "Context provider configuration", nullable = true)
    private List<ContextProviderDTO> contextProviders;

    @Schema(description = "Replacement word file ID list", nullable = true)
    private List<String> correctWordFileIds;

    @Schema(description = "Tag name list", nullable = true)
    private List<String> tagNames;

    @Schema(description = "Tag ID list", nullable = true)
    private List<String> tagIds;

    @Data
    @Schema(description = "Plugin function info")
    public static class FunctionInfo implements Serializable {
        private static final TypeReference<HashMap<String, Object>> PARAM_INFO_TYPE = new TypeReference<>() {
        };

        @Schema(description = "Plugin ID", example = "plugin_01")
        private String pluginId;

        @Schema(description = "Function parameter info", nullable = true)
        private HashMap<String, Object> paramInfo = new HashMap<>();

        public void setParamInfo(Object paramInfo) {
            this.paramInfo = normalizeParamInfo(paramInfo);
        }

        private static HashMap<String, Object> normalizeParamInfo(Object paramInfo) {
            if (paramInfo == null) {
                return new HashMap<>();
            }
            if (paramInfo instanceof String value) {
                if (value.trim().isEmpty()) {
                    return new HashMap<>();
                }
                return JsonUtils.parseObject(value, PARAM_INFO_TYPE);
            }
            if (paramInfo instanceof Map<?, ?> value) {
                HashMap<String, Object> normalized = new HashMap<>();
                value.forEach((key, val) -> {
                    if (key != null) {
                        normalized.put(String.valueOf(key), val);
                    }
                });
                return normalized;
            }
            return JsonUtils.parseObject(JsonUtils.toJsonString(paramInfo), PARAM_INFO_TYPE);
        }

        private static final long serialVersionUID = 1L;
    }
}
