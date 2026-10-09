package xiaozhi.modules.sys.service;


import java.util.function.Consumer;

/**
 * System user utility, to avoid a circular dependency with the user module
 * e.g. users and devices depend on each other: users need all devices, devices need each device's username
 * @author zjy
 * @since 2025-4-2
 */
public interface SysUserUtilService {
    /**
     * Assign the username
     * @param userId user ID
     * @param setter setter method
     */
    void assignUsername( Long userId, Consumer<String> setter);
}
