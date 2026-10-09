package xiaozhi.modules.agent.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.user.UserDetail;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentMcpAccessPointService;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.security.user.SecurityUser;

@Tag(name = "Agent MCP access point management")
@RequiredArgsConstructor
@RestController
@RequestMapping("/agent/mcp")
public class AgentMcpAccessPointController {
    private final AgentMcpAccessPointService agentMcpAccessPointService;
    private final AgentService agentService;

    /**
     * Get the agent's MCP access point address
     * 
     * @param agentId agent ID
     * @return an error message or the MCP access point address
     */
    @Operation(summary = "Get the agent's MCP access point address")
    @GetMapping("/address/{agentId}")
    @RequiresPermissions("sys:role:normal")
    public Result<String> getAgentMcpAccessAddress(@PathVariable("agentId") String agentId) {
        // Get the current user
        UserDetail user = SecurityUser.getUser();

        // Check permissions
        if (!agentService.checkAgentPermission(agentId, user.getId())) {
            return new Result<String>().error(ErrorCode.MCP_ACCESS_POINT_ADDRESS_NO_PERMISSION);
        }
        String agentMcpAccessAddress = agentMcpAccessPointService.getAgentMcpAccessAddress(agentId);
        if (agentMcpAccessAddress == null) {
            return new Result<String>().error(ErrorCode.MCP_ACCESS_POINT_ADDRESS_NOT_CONFIGURED);
        }
        return new Result<String>().ok(agentMcpAccessAddress);
    }

    @Operation(summary = "Get the agent's MCP tool list")
    @GetMapping("/tools/{agentId}")
    @RequiresPermissions("sys:role:normal")
    public Result<List<String>> getAgentMcpToolsList(@PathVariable("agentId") String agentId) {
        // Get the current user
        UserDetail user = SecurityUser.getUser();

        // Check permissions
        if (!agentService.checkAgentPermission(agentId, user.getId())) {
            return new Result<List<String>>().error(ErrorCode.MCP_ACCESS_POINT_TOOLS_LIST_NO_PERMISSION);
        }
        List<String> agentMcpToolsList = agentMcpAccessPointService.getAgentMcpToolsList(agentId);
        return new Result<List<String>>().ok(agentMcpToolsList);
    }
}
