package xiaozhi.modules.agent.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;

/**
 * {@link AgentChatHistoryEntity} agent chat history DAO
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Mapper
public interface AgentVoicePrintDao extends BaseMapper<AgentVoicePrintEntity> {

}
