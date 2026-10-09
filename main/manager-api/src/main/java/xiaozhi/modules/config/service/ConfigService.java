package xiaozhi.modules.config.service;

import java.util.List;
import java.util.Map;

public interface ConfigService {
    /**
     * Get server configuration
     *
     * @param isCache whether to cache
     * @return configuration info
     */
    Map<String, Object> getConfig(Boolean isCache);

    /**
     * Get agent model configuration
     *
     * @param macAddress     MAC address
     * @param selectedModule models already instantiated on the client
     * @return model configuration info
     */
    Map<String, Object> getAgentModels(String macAddress, Map<String, String> selectedModule);

    /**
     * Get agent replacement words
     *
     * @param macAddress device MAC address
     * @return replacement word list, formatted like ["word1|word01", "word2|word02"]
     */
    List<String> getCorrectWords(String macAddress);
}
