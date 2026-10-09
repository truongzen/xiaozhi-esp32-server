package xiaozhi.modules.device.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_device_address_book")
@Schema(description = "Device address book")
public class DeviceAddressBookEntity {

    @TableId(type = IdType.INPUT)
    @Schema(description = "MAC address of this device")
    private String macAddress;

    @Schema(description = "MAC address of the other device")
    private String targetMac;

    @Schema(description = "What I call the other party")
    private String alias;

    @Schema(description = "Whether calling is permitted")
    private Boolean hasPermission;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "Creator")
    private Long creator;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "Create time")
    private Date createDate;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "Updater")
    private Long updater;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "Update time")
    private Date updateDate;
}