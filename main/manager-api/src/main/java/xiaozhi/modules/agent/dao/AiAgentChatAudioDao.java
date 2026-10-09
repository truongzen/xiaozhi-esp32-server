package xiaozhi.modules.agent.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.agent.entity.AgentChatAudioEntity;

/**
 * {@link AgentChatAudioEntity} agent chat audio data DAO
 *
 * @author Goody
 * @version 1.0, 2025/5/8
 * @since 1.0.0
 */
@Mapper
public interface AiAgentChatAudioDao extends BaseMapper<AgentChatAudioEntity> {
}