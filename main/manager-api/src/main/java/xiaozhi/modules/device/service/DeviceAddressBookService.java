package xiaozhi.modules.device.service;

import java.util.List;
import java.util.Map;

import xiaozhi.modules.device.entity.DeviceAddressBookEntity;

public interface DeviceAddressBookService {

    /**
     * Get the device address book list
     */
    List<DeviceAddressBookEntity> getAddressBookList(String macAddress);

    /**
     * Get the address books of all devices (for the global cache)
     */
    Map<String, Map<String, String>> getAllAddressBooks();

    /**
     * Update alias
     */
    void updateAlias(String macAddress, String targetMac, String alias);

    /**
     * Update permission
     */
    void updatePermission(String macAddress, String targetMac, Boolean hasPermission);

    /**
     * Add or update an address book record
     */
    void saveOrUpdate(String macAddress, String targetMac, String alias, Boolean hasPermission);

    /**
     * Refresh the address book cache
     */
    void refreshCache();

    /**
     * Place a call by nickname
     * @param callerMac caller MAC address
     * @param nickname callee nickname
     * @param isAnswer whether this is answer mode (skips the permission check)
     */
    Map<String, Object> callByNickname(String callerMac, String nickname, boolean isAnswer);

    /**
     * Batch delete address book records related to devices
     */
    void deleteByMacAddresses(List<String> macAddresses);
}