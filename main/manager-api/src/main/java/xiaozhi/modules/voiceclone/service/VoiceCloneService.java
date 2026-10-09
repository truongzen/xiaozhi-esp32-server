package xiaozhi.modules.voiceclone.service;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.voiceclone.dto.VoiceCloneDTO;
import xiaozhi.modules.voiceclone.dto.VoiceCloneResponseDTO;
import xiaozhi.modules.voiceclone.entity.VoiceCloneEntity;

/**
 * Voice clone management
 */
public interface VoiceCloneService extends BaseService<VoiceCloneEntity> {

    /**
     * Paged query
     */
    PageData<VoiceCloneEntity> page(Map<String, Object> params);

    /**
     * Save voice clone
     */
    void save(VoiceCloneDTO dto);

    /**
     * Batch delete
     */
    void delete(String[] ids);

    /**
     * Get the list of voice clones by user ID
     * 
     * @param userId user ID
     * @return list of voice clones
     */
    List<VoiceCloneEntity> getByUserId(Long userId);

    /**
     * Paged query of voice clones with model name and user name
     */
    PageData<VoiceCloneResponseDTO> pageWithNames(Map<String, Object> params);

    /**
     * Get voice clone info with model name and user name by ID
     */
    VoiceCloneResponseDTO getByIdWithNames(String id);

    /**
     * Get the list of voice clones with model name by user ID
     */
    List<VoiceCloneResponseDTO> getByUserIdWithNames(Long userId);

    /**
     * Upload an audio file
     */
    void uploadVoice(String id, MultipartFile voiceFile) throws Exception;

    /**
     * Update the voice clone name
     */
    void updateName(String id, String name);

    /**
     * Get audio data
     */
    byte[] getVoiceData(String id);

    /**
     * Clone audio by calling Volcano Engine for voice clone training
     * 
     * @param cloneId voice clone record ID
     */
    void cloneAudio(String cloneId);
}
