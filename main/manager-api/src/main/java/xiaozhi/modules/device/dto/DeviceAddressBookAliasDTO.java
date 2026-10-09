package xiaozhi.modules.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Update device address book alias")
public class DeviceAddressBookAliasDTO {

    @NotBlank(message = "MAC address cannot be empty")
    @Schema(description = "MAC address of this device")
    private String macAddress;

    @NotBlank(message = "Target MAC address cannot be empty")
    @Schema(description = "MAC address of the other device")
    private String targetMac;

    @Schema(description = "What I call the other party")
    private String alias;
}