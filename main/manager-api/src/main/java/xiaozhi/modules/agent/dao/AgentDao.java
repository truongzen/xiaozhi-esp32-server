package xiaozhi.modules.agent.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import org.apache.ibatis.annotations.Select;
import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.vo.AgentInfoVO;

@Mapper
public interface AgentDao extends BaseDao<AgentEntity> {
    /**
     * Get the device count of an agent
     * 
     * @param agentId agent ID
     * @return device count
     */
    Integer getDeviceCountByAgentId(@Param("agentId") String agentId);

    /**
     * Query the default agent of a device by its MAC address
     *
     * @param macAddress device MAC address
     * @return default agent info
     */
    @Select(" SELECT a.* FROM ai_device d " +
            " LEFT JOIN ai_agent a ON d.agent_id = a.id " +
            " WHERE d.mac_address = #{macAddress} " +
            " ORDER BY d.id DESC LIMIT 1")
    AgentEntity getDefaultAgentByMacAddress(@Param("macAddress") String macAddress);

    /**
     * Query agent info by ID, including plugin info
     *
     * @param agentId agent ID
     */
    AgentInfoVO selectAgentInfoById(@Param("agentId") String agentId);

    /**
     * Lock the agent main record to serialize configuration writes for the same agent
     *
     * @param agentId agent ID
     */
    AgentEntity selectByIdForUpdate(@Param("agentId") String agentId);

    /**
     * Write exactly the agent fields covered by a snapshot, including null values in the target snapshot.
     * Does not update fields outside the snapshot, such as owner user and creation info.
     *
     * @param agent agent with the target snapshot applied
     * @return number of affected rows
     */
    int updateSnapshotFields(@Param("agent") AgentEntity agent);
}
