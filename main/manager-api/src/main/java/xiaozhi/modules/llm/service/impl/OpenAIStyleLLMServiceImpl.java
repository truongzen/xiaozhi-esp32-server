package xiaozhi.modules.llm.service.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.llm.service.LLMService;
import xiaozhi.modules.model.entity.ModelConfigEntity;
import xiaozhi.modules.model.service.ModelConfigService;

/**
 * LLM service implementation for OpenAI-style APIs
 * Supports OpenAI-API-compatible models such as Alibaba Cloud, DeepSeek and ChatGLM
 */
@Slf4j
@Service
public class OpenAIStyleLLMServiceImpl implements LLMService {

    // Platform domains that require thinking mode to be disabled, with the corresponding parameters
    private static final Map<String, Map<String, Object>> THINKING_DISABLED_DOMAINS = new LinkedHashMap<>();
    static {
        THINKING_DISABLED_DOMAINS.put("aliyuncs.com", Map.of("enable_thinking", false));
        Map<String, Object> thinkingDisabled = Map.of("thinking", Map.of("type", "disabled"));
        THINKING_DISABLED_DOMAINS.put("deepseek.com", thinkingDisabled);
        THINKING_DISABLED_DOMAINS.put("bigmodel.cn", thinkingDisabled);
        THINKING_DISABLED_DOMAINS.put("moonshot.cn", thinkingDisabled);
        THINKING_DISABLED_DOMAINS.put("volces.com", thinkingDisabled);
    }

    @Autowired
    private ModelConfigService modelConfigService;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Automatically disable thinking mode based on the domain
     */
    private void applyThinkingDisabled(String baseUrl, Map<String, Object> requestBody) {
        for (Map.Entry<String, Map<String, Object>> entry : THINKING_DISABLED_DOMAINS.entrySet()) {
            if (baseUrl.contains(entry.getKey())) {
                requestBody.putAll(entry.getValue());
                log.info("Disabling thinking mode for domain {}, params: {}", baseUrl, entry.getValue());
                break;
            }
        }
    }

    private static final String DEFAULT_SUMMARY_PROMPT = "你是一个经验丰富的记忆总结者，擅长将对话内容进行总结摘要，遵循以下规则：\n1、总结用户的重要信息，以便在未来的对话中提供更个性化的服务\n2、不要重复总结，不要遗忘之前记忆，除非原来的记忆超过了1800字，否则不要遗忘、不要压缩用户的历史记忆\n3、用户操控的设备音量、播放音乐、天气、退出、不想对话等和用户本身无关的内容，这些信息不需要加入到总结中\n4、聊天内容中的今天的日期时间、今天的天气情况与用户事件无关的数据，这些信息如果当成记忆存储会影响后续对话，这些信息不需要加入到总结中\n5、不要把设备操控的成果结果和失败结果加入到总结中，也不要把用户的一些废话加入到总结中\n6、不要为了总结而总结，如果用户的聊天没有意义，请返回原来的历史记录也是可以的\n7、只需要返回总结摘要，严格控制在1800字内\n8、不要包含代码、xml，不需要解释、注释和说明，保存记忆时仅从对话提取信息，不要混入示例内容\n9、如果提供了历史记忆，请将新对话内容与历史记忆进行智能合并，保留有价值的历史信息，同时添加新的重要信息\n\n历史记忆：\n{history_memory}\n\n新对话内容：\n{conversation}";

    private static final String DEFAULT_TITLE_PROMPT = "请根据以下对话内容，生成一个简洁的会话标题（约15字以内），只返回标题，不要包含任何解释或标点符号：\n{conversation}";

    @Override
    public String generateSummary(String conversation) {
        return generateSummary(conversation, null, null);
    }

    @Override
    public String generateSummaryWithModel(String conversation, String modelId) {
        return generateSummary(conversation, null, modelId);
    }

    @Override
    public String generateSummary(String conversation, String promptTemplate, String modelId) {
        if (!isAvailable()) {
            log.warn("LLM service unavailable, cannot generate summary");
            return "LLM service unavailable, cannot generate summary";
        }

        try {
            // Get the LLM model configuration from the control console
            ModelConfigEntity llmConfig;
            if (modelId != null && !modelId.trim().isEmpty()) {
                // Get the configuration by specific model ID
                llmConfig = modelConfigService.getModelByIdFromCache(modelId);
            } else {
                // Keep backward compatibility by using the default configuration
                llmConfig = getDefaultLLMConfig();
            }

            if (llmConfig == null || llmConfig.getConfigJson() == null) {
                log.error("No available LLM model configuration found, modelId: {}", modelId);
                return "No available LLM model configuration found";
            }

            JSONObject configJson = llmConfig.getConfigJson();
            String baseUrl = configJson.getStr("base_url");
            String model = configJson.getStr("model_name");
            String apiKey = configJson.getStr("api_key");
            Double temperature = configJson.getDouble("temperature");
            Integer maxTokens = configJson.getInt("max_tokens");

            if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(apiKey)) {
                log.error("Incomplete LLM configuration: baseUrl or apiKey is empty");
                return "Incomplete LLM configuration, cannot generate summary";
            }

            // Build the prompt
            String prompt = (promptTemplate != null ? promptTemplate : DEFAULT_SUMMARY_PROMPT).replace("{conversation}",
                    conversation);

            // Build the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "gpt-3.5-turbo");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            requestBody.put("messages", List.of(message));
            requestBody.put("temperature", temperature != null ? temperature : 0.7);
            requestBody.put("max_tokens", maxTokens != null ? maxTokens : 2000);

            // Disable thinking mode
            applyThinkingDisabled(baseUrl, requestBody);

            // Send the HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Build the full API URL
            String apiUrl = baseUrl;
            if (!apiUrl.endsWith("/chat/completions")) {
                if (!apiUrl.endsWith("/")) {
                    apiUrl += "/";
                }
                apiUrl += "chat/completions";
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices != null && choices.size() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    return messageObj.getStr("content");
                }
            } else {
                log.error("LLM API call failed, status code: {}, response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception while calling LLM service to generate summary, modelId: {}", modelId, e);
        }

        return "Failed to generate summary, please try again later";
    }

    @Override
    public String generateSummary(String conversation, String promptTemplate) {
        return generateSummary(conversation, promptTemplate, null);
    }

    @Override
    public String generateSummaryWithHistory(String conversation, String historyMemory, String promptTemplate,
            String modelId) {
        if (!isAvailable()) {
            log.warn("LLM service unavailable, cannot generate summary");
            return "LLM service unavailable, cannot generate summary";
        }

        try {
            // Get the LLM model configuration from the control console
            ModelConfigEntity llmConfig;
            if (modelId != null && !modelId.trim().isEmpty()) {
                llmConfig = modelConfigService.getModelByIdFromCache(modelId);
            } else {
                llmConfig = getDefaultLLMConfig();
            }

            if (llmConfig == null || llmConfig.getConfigJson() == null) {
                log.error("No available LLM model configuration found, modelId: {}", modelId);
                return "No available LLM model configuration found";
            }

            JSONObject configJson = llmConfig.getConfigJson();
            String baseUrl = configJson.getStr("base_url");
            String model = configJson.getStr("model_name");
            String apiKey = configJson.getStr("api_key");

            if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(apiKey)) {
                log.error("Incomplete LLM configuration: baseUrl or apiKey is empty");
                return "Incomplete LLM configuration, cannot generate summary";
            }

            // Build the prompt, including historical memory
            String prompt = (promptTemplate != null ? promptTemplate : DEFAULT_SUMMARY_PROMPT)
                    .replace("{history_memory}", historyMemory != null ? historyMemory : "无历史记忆")
                    .replace("{conversation}", conversation);

            // Build the request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "gpt-3.5-turbo");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            requestBody.put("messages", List.of(message));
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 2000);

            // Disable thinking mode
            applyThinkingDisabled(baseUrl, requestBody);

            // Send the HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Build the full API URL
            String apiUrl = baseUrl;
            if (!apiUrl.endsWith("/chat/completions")) {
                if (!apiUrl.endsWith("/")) {
                    apiUrl += "/";
                }
                apiUrl += "chat/completions";
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices != null && choices.size() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    return messageObj.getStr("content");
                }
            } else {
                log.error("LLM API call failed, status code: {}, response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception while calling LLM service to generate summary, modelId: {}", modelId, e);
        }

        return "Failed to generate summary, please try again later";
    }

    @Override
    public boolean isAvailable() {
        try {
            ModelConfigEntity defaultLLMConfig = getDefaultLLMConfig();
            if (defaultLLMConfig == null || defaultLLMConfig.getConfigJson() == null) {
                return false;
            }

            JSONObject configJson = defaultLLMConfig.getConfigJson();
            String baseUrl = configJson.getStr("base_url");
            String apiKey = configJson.getStr("api_key");

            return baseUrl != null && !baseUrl.trim().isEmpty() &&
                    apiKey != null && !apiKey.trim().isEmpty();
        } catch (Exception e) {
            log.error("Exception while checking LLM service availability:", e);
            return false;
        }
    }

    @Override
    public boolean isAvailable(String modelId) {
        try {
            if (modelId == null || modelId.trim().isEmpty()) {
                return isAvailable();
            }

            // Get the configuration by specific model ID
            ModelConfigEntity modelConfig = modelConfigService.getModelByIdFromCache(modelId);
            if (modelConfig == null || modelConfig.getConfigJson() == null) {
                log.warn("Specified LLM model configuration not found, modelId: {}", modelId);
                return false;
            }

            JSONObject configJson = modelConfig.getConfigJson();
            String baseUrl = configJson.getStr("base_url");
            String apiKey = configJson.getStr("api_key");

            return baseUrl != null && !baseUrl.trim().isEmpty() &&
                    apiKey != null && !apiKey.trim().isEmpty();
        } catch (Exception e) {
            log.error("Exception while checking LLM service availability, modelId: {}", modelId, e);
            return false;
        }
    }

    /**
     * Get the default LLM model configuration from the control console
     */
    private ModelConfigEntity getDefaultLLMConfig() {
        try {
            // Get all enabled LLM model configurations
            List<ModelConfigEntity> llmConfigs = modelConfigService.getEnabledModelsByType("LLM");
            if (llmConfigs == null || llmConfigs.isEmpty()) {
                return null;
            }

            // Prefer the default configuration; if there is none, return the first enabled configuration
            for (ModelConfigEntity config : llmConfigs) {
                if (config.getIsDefault() != null && config.getIsDefault() == 1) {
                    return config;
                }
            }

            return llmConfigs.get(0);
        } catch (Exception e) {
            log.error("Exception while getting LLM model configuration:", e);
            return null;
        }
    }

    @Override
    public String generateTitle(String conversation, String modelId) {
        if (!isAvailable()) {
            log.warn("LLM service unavailable, cannot generate title");
            return null;
        }

        try {
            ModelConfigEntity llmConfig;
            if (modelId != null && !modelId.trim().isEmpty()) {
                llmConfig = modelConfigService.getModelByIdFromCache(modelId);
            } else {
                llmConfig = getDefaultLLMConfig();
            }

            if (llmConfig == null || llmConfig.getConfigJson() == null) {
                log.error("No available LLM model configuration found, modelId: {}", modelId);
                return null;
            }

            JSONObject configJson = llmConfig.getConfigJson();
            String baseUrl = configJson.getStr("base_url");
            String model = configJson.getStr("model_name");
            String apiKey = configJson.getStr("api_key");

            if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(apiKey)) {
                log.error("Incomplete LLM configuration: baseUrl or apiKey is empty");
                return null;
            }

            String prompt = DEFAULT_TITLE_PROMPT.replace("{conversation}", conversation);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model != null ? model : "gpt-3.5-turbo");

            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            requestBody.put("messages", List.of(message));
            requestBody.put("temperature", 0.3);
            requestBody.put("max_tokens", 50);

            // Disable thinking mode
            applyThinkingDisabled(baseUrl, requestBody);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String apiUrl = baseUrl;
            if (!apiUrl.endsWith("/chat/completions")) {
                if (!apiUrl.endsWith("/")) {
                    apiUrl += "/";
                }
                apiUrl += "chat/completions";
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject responseJson = JSONUtil.parseObj(response.getBody());
                JSONArray choices = responseJson.getJSONArray("choices");
                if (choices != null && choices.size() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject messageObj = choice.getJSONObject("message");
                    String title = messageObj.getStr("content");
                    if (StringUtils.isNotBlank(title)) {
                        title = title.trim().replaceAll("[，。！？、：；''\"\"【】（）]", "");
                        if (title.length() > 15) {
                            title = title.substring(0, 15);
                        }
                        return title;
                    }
                }
            } else {
                log.error("LLM API call failed, status code: {}, response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Exception while calling LLM service to generate title, modelId: {}", modelId, e);
        }

        return null;
    }
}
