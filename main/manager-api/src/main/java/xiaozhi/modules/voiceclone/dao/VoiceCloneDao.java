package xiaozhi.modules.voiceclone.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.model.dto.VoiceDTO;
import xiaozhi.modules.voiceclone.entity.VoiceCloneEntity;

/**
 * Voice clone
 */
@Mapper
public interface VoiceCloneDao extends BaseMapper<VoiceCloneEntity> {
    /**
     * Get the list of the user's successfully trained voices
     * 
     * @param modelId model ID
     * @param userId  user ID
     * @return list of successfully trained voices
     */
    List<VoiceDTO> getTrainSuccess(String modelId, Long userId);

}
