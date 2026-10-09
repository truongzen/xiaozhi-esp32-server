package xiaozhi.modules.device.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.device.dto.DeviceManualAddDTO;
import xiaozhi.modules.device.dto.DevicePageUserDTO;
import xiaozhi.modules.device.dto.DeviceReportReqDTO;
import xiaozhi.modules.device.dto.DeviceReportRespDTO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.vo.UserShowDeviceListVO;

public interface DeviceService extends BaseService<DeviceEntity> {
    /**
     * Get device online data
     */
    String getDeviceOnlineData(String agentId);

    /**
     * Check whether the device is activated
     */
    DeviceReportRespDTO checkDeviceActive(String macAddress, String clientId,
            DeviceReportReqDTO deviceReport);

    /**
     * Get the user's device list for the given agent,
     */
    List<DeviceEntity> getUserDevices(Long userId, String agentId);

    /**
     * Get the user's device list for the given agent (with time zone handling),
     */
    List<UserShowDeviceListVO> getUserDeviceList(Long userId, String agentId);

    /**
     * Unbind device
     */
    void unbindDevice(Long userId, String deviceId);

    /**
     * Activate device
     */
    Boolean deviceActivation(String agentId, String activationCode);

    /**
     * Delete all devices of this user
     * 
     * @param userId user ID
     */
    void deleteByUserId(Long userId);

    /**
     * Delete all devices associated with the given agent
     * 
     * @param agentId agent ID
     */
    void deleteByAgentId(String agentId);

    /**
     * Get the device count of the given user
     * 
     * @param userId user ID
     * @return device count
     */
    Long selectCountByUserId(Long userId);

    /**
     * Page all devices
     *
     * @param dto paging query parameters
     * @return paged user list
     */
    PageData<UserShowDeviceListVO> page(DevicePageUserDTO dto);

    /**
     * Get device info by MAC address
     * 
     * @param macAddress MAC address
     * @return device info
     */
    DeviceEntity getDeviceByMacAddress(String macAddress);

    /**
     * Get the activation code by device ID
     * 
     * @param deviceId device ID
     * @return activation code
     */
    String geCodeByDeviceId(String deviceId);

    /**
     * Get the most recent last connection time among this agent's devices
     * 
     * @param agentId agent ID
     * @return the most recent last connection time of the devices
     */
    Date getLatestLastConnectionTime(String agentId);

    /**
     * Add device manually
     */
    void manualAddDevice(Long userId, DeviceManualAddDTO dto);

    /**
     * Update device connection info
     */
    void updateDeviceConnectionInfo(String agentId, String deviceId, String appVersion);

    /**
     * Generate a WebSocket authentication token
     *
     * @param clientId client ID
     * @param username username (usually the deviceId)
     * @return authentication token string
     * @throws Exception if token generation fails
     */
    String generateWebSocketToken(String clientId, String username) throws Exception;

    /**
     * Search devices by MAC address
     *
     * @param macAddress MAC address keyword
     * @param userId     user ID
     * @return device list
     */
    List<DeviceEntity> searchDevicesByMacAddress(String macAddress, Long userId);

    /**
     * Get the device tool list
     */
    Object getDeviceTools(String deviceId);

    /**
     * Call a device tool
     */
    Object callDeviceTool(String deviceId, String toolName, Map<String, Object> arguments);

    }