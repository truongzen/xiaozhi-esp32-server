package xiaozhi.modules.device;

import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.sys.dto.SysUserDTO;
import xiaozhi.modules.sys.service.SysUserService;

@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("Device test")
public class DeviceTest {

    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private SysUserService sysUserService;

    @Test
    public void testRejectWeakPassword() {
        SysUserDTO userDTO = new SysUserDTO();
        userDTO.setUsername("test");
        userDTO.setPassword("weak-password-123");

        RenException exception = Assertions.assertThrows(RenException.class, () -> sysUserService.save(userDTO));
        Assertions.assertEquals(ErrorCode.PASSWORD_WEAK_ERROR, exception.getCode());
    }

    @Test
    @DisplayName("Test writing device info")
    public void testWriteDeviceInfo() {
        log.info("Start testing writing device info...");
        // Simulated device MAC address
        String macAddress = "00:11:22:33:44:66";
        // Simulated device verification code
        String deviceCode = "123456";

        HashMap<String, Object> map = new HashMap<>();
        map.put("mac_address", macAddress);
        map.put("activation_code", deviceCode);
        map.put("board", "hardware-model");
        map.put("app_version", "0.3.13");

        String safeDeviceId = macAddress.replace(":", "_").toLowerCase();
        String cacheDeviceKey = String.format("ota:activation:data:%s", safeDeviceId);
        redisUtils.set(cacheDeviceKey, map, 300);

        String redisKey = "ota:activation:code:" + deviceCode;
        log.info("Redis Key: {}", redisKey);

        // Write the device info to Redis
        redisUtils.set(redisKey, macAddress, 300);
        log.info("Device info written to Redis");

        // Verify the write succeeded
        String savedMacAddress = (String) redisUtils.get(redisKey);
        log.info("MAC address read from Redis: {}", savedMacAddress);

        // Verify with assertions
        Assertions.assertNotNull(savedMacAddress, "MAC address read from Redis should not be null");
        Assertions.assertEquals(macAddress, savedMacAddress, "Saved MAC address does not match the original MAC address");

        log.info("Test completed");
    }
}
