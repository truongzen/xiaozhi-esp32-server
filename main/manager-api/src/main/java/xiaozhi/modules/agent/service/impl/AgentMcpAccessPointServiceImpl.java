package xiaozhi.modules.agent.service.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.utils.AESUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.Enums.XiaoZhiMcpJsonRpcJson;
import xiaozhi.modules.agent.service.AgentMcpAccessPointService;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.utils.WebSocketClientManager;

@AllArgsConstructor
@Service
@Slf4j
public class AgentMcpAccessPointServiceImpl implements AgentMcpAccessPointService {
    private SysParamsService sysParamsService;

    @Override
    public String getAgentMcpAccessAddress(String id) {
        // Get the MCP address
        String url = sysParamsService.getValue(Constant.SERVER_MCP_ENDPOINT, true);
        if (StringUtils.isBlank(url) || "null".equals(url)) {
            return null;
        }
        URI uri = getURI(url);
        // Get the agent MCP URL prefix
        String agentMcpUrl = getAgentMcpUrl(uri);
        // Get the key
        String key = getSecretKey(uri);
        // Get the encrypted token
        String encryptToken = encryptToken(id, key);
        // URL-encode the token
        String encodedToken = URLEncoder.encode(encryptToken, StandardCharsets.UTF_8);
        // Return the agent MCP path format
        agentMcpUrl = "%s/mcp/?token=%s".formatted(agentMcpUrl, encodedToken);
        return agentMcpUrl;
    }

    @Override
    public List<String> getAgentMcpToolsList(String id) {
        String wsUrl = getAgentMcpAccessAddress(id);
        if (StringUtils.isBlank(wsUrl)) {
            return List.of();
        }

        // Replace /mcp with /call
        wsUrl = wsUrl.replace("/mcp/", "/call/");

        try {
            // Create the WebSocket connection, with the timeout increased to 15 seconds
            try (WebSocketClientManager client = WebSocketClientManager.build(
                    new WebSocketClientManager.Builder()
                            .uri(wsUrl)
                            .bufferSize(1024 * 1024)
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .maxSessionDuration(10, TimeUnit.SECONDS))) {

                // Step 1: send the initialize message and wait for the response
                log.info("Sending MCP initialize message, agent ID: {}", id);
                client.sendText(XiaoZhiMcpJsonRpcJson.getInitializeJson());

                // Wait for the initialize response (id=1) - fixed delay removed, now response-driven
                List<String> initResponses = client.listenerWithoutClose(response -> {
                    try {
                        Map<String, Object> jsonMap = JsonUtils.parseMap(response);
                        if (jsonMap != null && Integer.valueOf(1).equals(jsonMap.get("id"))) {
                            // Check for a result field, which indicates successful initialization
                            return jsonMap.containsKey("result") && !jsonMap.containsKey("error");
                        }
                        return false;
                    } catch (Exception e) {
                        log.warn("Failed to parse initialize response: {}", response, e);
                        return false;
                    }
                });

                // Validate the initialize response
                boolean initSucceeded = false;
                for (String response : initResponses) {
                    try {
                        Map<String, Object> jsonMap = JsonUtils.parseMap(response);
                        if (jsonMap != null && Integer.valueOf(1).equals(jsonMap.get("id"))) {
                            if (jsonMap.containsKey("result")) {
                                log.info("MCP initialized successfully, agent ID: {}", id);
                                initSucceeded = true;
                                break;
                            } else if (jsonMap.containsKey("error")) {
                                log.error("MCP initialization failed, agent ID: {}, error: {}", id, jsonMap.get("error"));
                                return List.of();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process initialize response: {}", response, e);
                    }
                }

                if (!initSucceeded) {
                    log.error("No valid MCP initialize response received, agent ID: {}", id);
                    return List.of();
                }

                // Step 2: send the initialized notification - only sent after receiving the initialize response
                log.info("Sending MCP initialized notification, agent ID: {}", id);
                client.sendText(XiaoZhiMcpJsonRpcJson.getNotificationsInitializedJson());
                // Step 3: send the tools list request - sent immediately, no extra delay needed
                log.info("Sending MCP tools list request, agent ID: {}", id);
                client.sendText(XiaoZhiMcpJsonRpcJson.getToolsListJson());

                // Wait for the tools list response (id=2)
                List<String> toolsResponses = client.listener(response -> {
                    try {
                        Map<String, Object> jsonMap = JsonUtils.parseMap(response);
                        return jsonMap != null && Integer.valueOf(2).equals(jsonMap.get("id"));
                    } catch (Exception e) {
                        log.warn("Failed to parse tools list response: {}", response, e);
                        return false;
                    }
                });

                // Process the tools list response
                for (String response : toolsResponses) {
                    try {
                        Map<String, Object> jsonMap = JsonUtils.parseMap(response);
                        if (jsonMap != null && Integer.valueOf(2).equals(jsonMap.get("id"))) {
                            // Check for a result field
                            Object resultObj = jsonMap.get("result");
                            if (resultObj instanceof Map<?, ?>) {
                                Map<String, Object> resultMap = JsonUtils.toStringObjectMap(resultObj);
                                Object toolsObj = resultMap.get("tools");
                                if (toolsObj instanceof List<?>) {
                                    List<Map<String, Object>> toolsList = JsonUtils.toStringObjectMapList(toolsObj);
                                    // Extract the list of tool names
                                    List<String> result = toolsList.stream()
                                            .map(tool -> String.class.cast(tool.get("name")))
                                            .filter(name -> name != null)
                                            .sorted()
                                            .collect(Collectors.toList());
                                    log.info("Got MCP tools list, agent ID: {}, tool count: {}", id, result.size());
                                    return result;
                                }
                            } else if (jsonMap.containsKey("error")) {
                                log.error("Failed to get tools list, agent ID: {}, error: {}", id, jsonMap.get("error"));
                                return List.of();
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process tools list response: {}", response, e);
                    }
                }

                log.warn("No valid tools list response found, agent ID: {}", id);
                return List.of();

            }
        } catch (Exception e) {
            log.error("Failed to get agent MCP tools list, agent ID: {}, reason: {}", id, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get the URI object
     * 
     * @param url path
     * @return URI object
     */
    private static URI getURI(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            log.error("Invalid path format: {},\nerror: {}", url, e.getMessage());
            throw new RuntimeException("The MCP address is invalid, please go to Parameter Management to fix the MCP access point address");
        }
    }

    /**
     * Get the key
     *
     * @param uri MCP address
     * @return key
     */
    private static String getSecretKey(URI uri) {
        // Get parameters
        String query = uri.getQuery();
        // Get the AES encryption key
        String str = "key=";
        return query.substring(query.indexOf(str) + str.length());
    }

    /**
     * Get the agent MCP access point URL
     *
     * @param uri MCP address
     * @return agent MCP access point URL
     */
    private String getAgentMcpUrl(URI uri) {
        // Get the scheme
        String wsScheme = (uri.getScheme().equals("https")) ? "wss" : "ws";
        // Get the host, port and path
        String path = uri.getSchemeSpecificPart();
        // Get the path before the last /
        path = path.substring(0, path.lastIndexOf("/"));
        return wsScheme + ":" + path;
    }

    /**
     * Get the token encrypted from the agent ID
     *
     * @param agentId agent ID
     * @param key     encryption key
     * @return encrypted token
     */
    private static String encryptToken(String agentId, String key) {
        // Hash the agent ID with MD5
        String md5 = DigestUtil.md5Hex(agentId);
        // Text to be AES-encrypted
        String json = "{\"agentId\": \"%s\"}".formatted(md5);
        // Encrypt to get the token value
        return AESUtils.encrypt(key, json);
    }
}
