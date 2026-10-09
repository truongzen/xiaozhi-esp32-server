package xiaozhi.modules.agent.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;

import xiaozhi.modules.agent.entity.AgentChatAudioEntity;

/**
 * Service for the agent chat audio data table
 *
 * @author Goody
 * @version 1.0, 2025/5/8
 * @since 1.0.0
 */
public interface AgentChatAudioService extends IRepository<AgentChatAudioEntity> {
    /**
     * Save audio data
     *
     * @param audioData audio data
     * @return audio ID
     */
    String saveAudio(byte[] audioData);

    /**
     * Get audio data
     *
     * @param audioId audio ID
     * @return audio data
     */
    byte[] getAudio(String audioId);
}
