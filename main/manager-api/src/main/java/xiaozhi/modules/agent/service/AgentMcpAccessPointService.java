package xiaozhi.modules.agent.service;


import java.util.List;

/**
 * Service for agent MCP access points
 *
 * @author zjy
 */
public interface AgentMcpAccessPointService {
    /**
     * Get the MCP access point address of an agent
     * @param id agent ID
     * @return MCP access point address
     */
   String getAgentMcpAccessAddress(String id);

    /**
     * Get the list of tools available on the agent's MCP access point
     * @param id agent ID
     * @return tool list
     */
   List<String> getAgentMcpToolsList(String id);
}
