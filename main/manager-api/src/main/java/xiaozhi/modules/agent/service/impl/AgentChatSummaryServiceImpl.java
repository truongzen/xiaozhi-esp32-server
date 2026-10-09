package xiaozhi.modules.agent.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.modules.agent.dto.AgentChatHistoryDTO;
import xiaozhi.modules.agent.dto.AgentChatSummaryDTO;
import xiaozhi.modules.agent.dto.AgentMemoryDTO;
import xiaozhi.modules.agent.dto.AgentUpdateDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.service.AgentChatSummaryService;
import xiaozhi.modules.agent.service.AgentChatTitleService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.vo.AgentInfoVO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;

/**
 * Agent chat history summary service implementation
 * Implements the summary logic from mem_local_short.py on the Python side
 */
@Service
@RequiredArgsConstructor
public class AgentChatSummaryServiceImpl implements AgentChatSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatSummaryServiceImpl.class);

    private final AgentChatHistoryService agentChatHistoryService;
    private final AgentService agentService;
    private final AgentChatTitleService agentChatTitleService;
    private final DeviceService deviceService;
    private final LLMService llmService;
    private final ModelConfigService modelConfigService;

    // Summary rule constants
    private static final int MAX_SUMMARY_LENGTH = 1800; // Maximum summary length
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
    private static final Pattern DEVICE_CONTROL_PATTERN = Pattern.compile("设备控制|设备操作|控制设备|设备状态",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WEATHER_PATTERN = Pattern.compile("天气|温度|湿度|降雨|气象", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("日期|时间|星期|月份|年份", Pattern.CASE_INSENSITIVE);

    private AgentChatSummaryDTO generateChatSummary(String sessionId) {
        try {
            System.out.println("Start generating chat history summary for session " + sessionId);

            // 1. Get chat history by sessionId
            List<AgentChatHistoryDTO> chatHistory = getChatHistoryBySessionId(sessionId);
            if (chatHistory == null || chatHistory.isEmpty()) {
                return new AgentChatSummaryDTO(sessionId, "No chat history found for this session");
            }

            // 2. Get agent info
            String agentId = getAgentIdFromSession(sessionId, chatHistory);
            if (StringUtils.isBlank(agentId)) {
                return new AgentChatSummaryDTO(sessionId, "Unable to get agent info");
            }

            // 3. Extract key conversation content
            List<String> meaningfulMessages = extractMeaningfulMessages(chatHistory);
            if (meaningfulMessages.isEmpty()) {
                return new AgentChatSummaryDTO(sessionId, "No valid conversation content to summarize");
            }

            // 4. Generate the summary (generateSummaryFromMessages already includes the length limit logic)
            String summary = generateSummaryFromMessages(meaningfulMessages, agentId);

            log.info("Generated chat history summary for session {}, length: {} characters", sessionId, summary.length());
            return new AgentChatSummaryDTO(sessionId, agentId, summary);

        } catch (Exception e) {
            log.error("Error generating chat history summary for session {}: {}", sessionId, e.getMessage());
            return new AgentChatSummaryDTO(sessionId, "Error generating summary: " + e.getMessage());
        }
    }

    @Override
    public boolean generateAndSaveChatSummary(String sessionId) {
        try {
            DeviceEntity device = getDeviceBySessionId(sessionId);
            if (device == null) {
                log.info("No device associated with session {} found", sessionId);
                return false;
            }

            String agentId = device.getAgentId();
            String memModelId = agentService.getAgentById(agentId).getMemModelId();

            if (memModelId == null || memModelId.equals(Constant.MEMORY_MEM_REPORT_ONLY)) {
                log.info("Session {} uses report-chat-history-only mode, skipping memory summary", sessionId);
                return true;
            }

            boolean shouldSummarizeMemory = !memModelId.equals(Constant.MEMORY_NO_MEM)
                    && !memModelId.equals(Constant.MEMORY_MEM0AI)
                    && !memModelId.equals(Constant.MEMORY_POWERMEM);

            if (shouldSummarizeMemory) {
                AgentChatSummaryDTO summaryDTO = generateChatSummary(sessionId);
                if (summaryDTO.isSuccess()) {
                    agentService.updateAgentById(agentId, new AgentUpdateDTO() {
                        {
                            setSummaryMemory(summaryDTO.getSummary());
                        }
                    }, false);
                    log.info("Saved chat history summary of session {} to agent {}", sessionId, agentId);
                } else {
                    log.info("Failed to generate summary: {}", summaryDTO.getErrorMessage());
                }
            } else {
                log.info("Session {} uses {} mode, skipping memory summary", sessionId, memModelId);
            }

            return true;

        } catch (Exception e) {
            log.error("Error saving chat history summary for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean generateAndSaveChatTitle(String sessionId) {
        try {
            // Get agentId automatically
            String agentId = findAgentIdBySessionId(sessionId);
            if (StringUtils.isBlank(agentId)) {
                log.warn("Unable to get agent info for session {}, skipping title generation", sessionId);
                return false;
            }

            List<AgentChatHistoryDTO> chatHistory = getChatHistoryBySessionId(sessionId);
            if (chatHistory == null || chatHistory.isEmpty()) {
                return false;
            }

            List<String> meaningfulMessages = extractMeaningfulMessages(chatHistory);
            if (meaningfulMessages.isEmpty()) {
                return false;
            }

            StringBuilder conversation = new StringBuilder();
            for (int i = 0; i < meaningfulMessages.size(); i++) {
                conversation.append("消息").append(i + 1).append(": ").append(meaningfulMessages.get(i)).append("\n");
            }

            String slmModelId = getSlmModelId(agentId);
            String title = llmService.generateTitle(conversation.toString(), slmModelId);

            if (StringUtils.isNotBlank(title)) {
                agentChatTitleService.saveOrUpdateTitle(sessionId, title);
                log.info("Saved title for session {}: {}", sessionId, title);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error generating title for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }

    private String getSlmModelId(String agentId) {
        try {
            if (StringUtils.isBlank(agentId)) {
                return null;
            }

            AgentInfoVO agentInfo = agentService.getAgentById(agentId);
            if (agentInfo == null) {
                return null;
            }

            String slmModelId = agentInfo.getSlmModelId();
            if (StringUtils.isNotBlank(slmModelId)) {
                log.info("Session {} uses SLM model: {}", agentId, slmModelId);
                return slmModelId;
            }

            ModelConfigEntity defaultLlmConfig = getDefaultLLMConfig();
            if (defaultLlmConfig != null) {
                log.info("Session {} uses default LLM model: {}", agentId, defaultLlmConfig.getId());
                return defaultLlmConfig.getId();
            }

            String llmModelId = agentInfo.getLlmModelId();
            log.info("Session {} uses LLM model (final fallback): {}", agentId, llmModelId);
            return llmModelId;
        } catch (Exception e) {
            log.error("Failed to get agent SLM model ID, agentId: {}, error: {}", agentId, e.getMessage());
            return null;
        }
    }

    private ModelConfigEntity getDefaultLLMConfig() {
        try {
            List<ModelConfigEntity> llmConfigs = modelConfigService.getEnabledModelsByType("LLM");
            if (llmConfigs == null || llmConfigs.isEmpty()) {
                return null;
            }

            for (ModelConfigEntity config : llmConfigs) {
                if (config.getIsDefault() != null && config.getIsDefault() == 1) {
                    return config;
                }
            }

            return llmConfigs.get(0);
        } catch (Exception e) {
            log.error("Failed to get default LLM configuration: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get chat history by session ID
     */
    private List<AgentChatHistoryDTO> getChatHistoryBySessionId(String sessionId) {
        try {
            // Chat history needs to be fetched by sessionId here
            // Since the existing API requires agentId, we need to find the associated agentId first
            String agentId = findAgentIdBySessionId(sessionId);
            if (StringUtils.isBlank(agentId)) {
                return null;
            }
            return agentChatHistoryService.getChatHistoryBySessionId(agentId, sessionId);
        } catch (Exception e) {
            log.error("Failed to get chat history for session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Find the associated agent ID by session ID
     */
    private String findAgentIdBySessionId(String sessionId) {
        try {
            // Query the first record of this session to get agentId
            QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
            wrapper.select("agent_id")
                    .eq("session_id", sessionId)
                    .last("LIMIT 1");

            AgentChatHistoryEntity entity = agentChatHistoryService.getOne(wrapper);
            return entity != null ? entity.getAgentId() : null;
        } catch (Exception e) {
            log.error("Failed to find agent ID by session ID {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /**
     * Get the agent ID from the session
     */
    private String getAgentIdFromSession(String sessionId, List<AgentChatHistoryDTO> chatHistory) {
        // Query the agent ID directly from the database
        return findAgentIdBySessionId(sessionId);
    }

    /**
     * Extract meaningful conversation content (user messages only, excluding AI replies)
     */
    private List<String> extractMeaningfulMessages(List<AgentChatHistoryDTO> chatHistory) {
        List<String> meaningfulMessages = new ArrayList<>();

        for (AgentChatHistoryDTO message : chatHistory) {
            // Only process user messages (chatType = 1)
            if (message.getChatType() != null && message.getChatType() == 1) {
                String content = extractContentFromMessage(message);
                if (isMeaningfulMessage(content)) {
                    meaningfulMessages.add(content);
                }
            }
        }

        return meaningfulMessages;
    }

    /**
     * Extract content from a message (handles JSON format)
     */
    private String extractContentFromMessage(AgentChatHistoryDTO message) {
        String content = message.getContent();
        if (StringUtils.isBlank(content)) {
            return "";
        }

        // Handle JSON content (consistent with the frontend ChatHistoryDialog.vue logic)
        Matcher matcher = JSON_PATTERN.matcher(content);
        if (matcher.find()) {
            String jsonContent = matcher.group();
            // Simplified handling: extract the text content from the JSON
            return extractTextFromJson(jsonContent);
        }

        return content;
    }

    /**
     * Extract text content from JSON
     */
    private String extractTextFromJson(String jsonContent) {
        // Simplified handling: extract the value of the "content" field
        Pattern contentPattern = Pattern.compile("\"content\"\s*:\s*\"([^\"]*)\"");
        Matcher matcher = contentPattern.matcher(jsonContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return jsonContent;
    }

    /**
     * Determine whether the message is meaningful
     */
    private boolean isMeaningfulMessage(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }

        // Exclude device control messages
        if (DEVICE_CONTROL_PATTERN.matcher(content).find()) {
            return false;
        }

        // Exclude irrelevant content such as date and weather
        if (WEATHER_PATTERN.matcher(content).find() || DATE_PATTERN.matcher(content).find()) {
            return false;
        }

        // Exclude messages that are too short
        return content.length() >= 5;
    }

    /**
     * Generate a summary from messages
     */
    private String generateSummaryFromMessages(List<String> messages, String agentId) {
        if (messages.isEmpty()) {
            return "本次对话内容较少，没有需要总结的重要信息。";
        }

        // Build the full conversation content
        StringBuilder conversation = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            conversation.append("消息").append(i + 1).append(": ").append(messages.get(i)).append("\n");
        }

        try {
            // Get the current agent's historical memory
            String historyMemory = getCurrentAgentMemory(agentId);

            // Call the LLM service for intelligent summarization, passing agentId to get the correct model configuration
            String summary = callJavaLLMForSummaryWithHistory(conversation.toString(), historyMemory, agentId);

            // Apply summary rules: limit the maximum length
            if (summary.length() > MAX_SUMMARY_LENGTH) {
                summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
            }

            return summary;
        } catch (Exception e) {
            log.error("Failed to call the Java-side LLM service: {}", e.getMessage());
            throw new RuntimeException("LLM service unavailable, unable to generate chat summary");
        }
    }

    /**
     * Get the current agent's historical memory
     */
    private String getCurrentAgentMemory(String agentId) {
        try {
            if (StringUtils.isBlank(agentId)) {
                return null;
            }

            // Get agent info
            AgentInfoVO agentInfo = agentService.getAgentById(agentId);
            if (agentInfo == null) {
                return null;
            }

            // Return the agent's current memory summary
            return agentInfo.getSummaryMemory();
        } catch (Exception e) {
            log.error("Failed to get agent historical memory, agentId: {}, error: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * Call the Java-side LLM service for intelligent summarization (supports merging historical memory)
     */
    private String callJavaLLMForSummaryWithHistory(String conversation, String historyMemory, String agentId) {
        try {
            String modelId = getSlmModelId(agentId);

            if (StringUtils.isBlank(modelId)) {
                log.info("No SLM model found, using the default LLM service");
                return llmService.generateSummaryWithHistory(conversation, historyMemory, null, null);
            }

            String summary = llmService.generateSummaryWithHistory(conversation, historyMemory, null, modelId);

            if (StringUtils.isNotBlank(summary) && !summary.equals("服务暂不可用") && !summary.equals("总结生成失败")) {
                return summary;
            }

            throw new RuntimeException("Java-side LLM service returned an error: " + summary);

        } catch (Exception e) {
            log.error("Exception calling the Java-side LLM service, agentId: {}, error: {}", agentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Call the Java-side LLM service for intelligent summarization
     */
    private String callJavaLLMForSummary(String conversation, String agentId) {
        try {
            String modelId = getSlmModelId(agentId);

            if (StringUtils.isBlank(modelId)) {
                log.info("No SLM model found, using the default LLM service");
                return llmService.generateSummary(conversation);
            }

            String summary = llmService.generateSummaryWithModel(conversation, modelId);

            if (StringUtils.isNotBlank(summary) && !summary.equals("服务暂不可用") && !summary.equals("总结生成失败")) {
                return summary;
            }

            throw new RuntimeException("Java-side LLM service returned an error: " + summary);

        } catch (Exception e) {
            log.error("Exception calling the Java-side LLM service, agentId: {}, error: {}", agentId, e.getMessage());
            throw e;
        }
    }

    /**
     * Get the LLM model ID for memory summarization
     */
    private String getMemorySummaryModelId(String agentId) {
        try {
            if (StringUtils.isBlank(agentId)) {
                return null;
            }

            // Get agent info
            AgentInfoVO agentInfo = agentService.getAgentById(agentId);
            if (agentInfo == null) {
                return null;
            }

            // Get the agent's memory model ID
            String memModelId = agentInfo.getMemModelId();
            if (StringUtils.isBlank(memModelId)) {
                return null;
            }

            // Get the memory model configuration
            ModelConfigEntity memModelConfig = modelConfigService.getModelByIdFromCache(memModelId);
            if (memModelConfig == null || memModelConfig.getConfigJson() == null) {
                return null;
            }

            // Extract the corresponding LLM model ID from the memory model configuration
            Map<String, Object> configMap = memModelConfig.getConfigJson();
            String llmModelId = (String) configMap.get("llm");

            if (StringUtils.isBlank(llmModelId)) {
                // If the memory model has no dedicated LLM configured, use the agent's default LLM model
                return agentInfo.getLlmModelId();
            }

            return llmModelId;
        } catch (Exception e) {
            log.error("Failed to get memory summary LLM model ID, agentId: {}, error: {}", agentId, e.getMessage());
            return null;
        }
    }

    /**
     * Get device info by session ID
     */
    private DeviceEntity getDeviceBySessionId(String sessionId) {
        try {
            // Query the first record of this session to get macAddress
            QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
            wrapper.select("mac_address")
                    .eq("session_id", sessionId)
                    .last("LIMIT 1");

            AgentChatHistoryEntity entity = agentChatHistoryService.getOne(wrapper);
            if (entity != null && StringUtils.isNotBlank(entity.getMacAddress())) {
                return deviceService.getDeviceByMacAddress(entity.getMacAddress());
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to find device info by session ID {}: {}", sessionId, e.getMessage());
            return null;
        }
    }
}
