package xiaozhi.modules.device.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.device.entity.DeviceAddressBookEntity;

@Mapper
public interface DeviceAddressBookDao extends BaseMapper<DeviceAddressBookEntity> {

    /**
     * Add a device address book record
     */
    int insertAddressBook(DeviceAddressBookEntity entity);

    /**
     * Get the device address book list
     */
    List<DeviceAddressBookEntity> getAddressBookList(@Param("macAddress") String macAddress);

    /**
     * Update alias
     */
    void updateAlias(@Param("macAddress") String macAddress, @Param("targetMac") String targetMac, @Param("alias") String alias);

    /**
     * Update permission
     */
    void updatePermission(@Param("macAddress") String macAddress, @Param("targetMac") String targetMac, @Param("hasPermission") Boolean hasPermission);

    /**
     * Batch delete address book records related to devices
     */
    void deleteByMacAddresses(@Param("macAddresses") List<String> macAddresses);
}
