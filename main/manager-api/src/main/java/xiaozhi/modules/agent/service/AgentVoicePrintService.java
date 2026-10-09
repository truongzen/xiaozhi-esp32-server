package xiaozhi.modules.agent.service;

import java.util.List;

import xiaozhi.modules.agent.dto.AgentVoicePrintSaveDTO;
import xiaozhi.modules.agent.dto.AgentVoicePrintUpdateDTO;
import xiaozhi.modules.agent.vo.AgentVoicePrintVO;

/**
 * Service for agent voiceprints
 *
 * @author zjy
 */
public interface AgentVoicePrintService {
    /**
     * Add a new voiceprint to an agent
     *
     * @param dto data for saving the agent voiceprint
     * @return true: success, false: failure
     */
    boolean insert(AgentVoicePrintSaveDTO dto);

    /**
     * Delete the specified voiceprint of an agent
     *
     * @param userId       ID of the currently logged-in user
     * @param voicePrintId voiceprint ID
     * @return whether it succeeded (true: success, false: failure)
     */
    boolean delete(Long userId, String voicePrintId);

    /**
     * Get all voiceprint data of the specified agent
     *
     * @param userId  ID of the currently logged-in user
     * @param agentId agent ID
     * @return voiceprint data collection
     */
    List<AgentVoicePrintVO> list(Long userId, String agentId);

    /**
     * Update the specified voiceprint data of an agent
     *
     * @param userId ID of the currently logged-in user
     * @param dto    voiceprint data to update
     * @return whether it succeeded (true: success, false: failure)
     */
    boolean update(Long userId, AgentVoicePrintUpdateDTO dto);

}
