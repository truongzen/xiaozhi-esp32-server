package xiaozhi.modules.sys.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.sys.dto.SysParamsDTO;
import xiaozhi.modules.sys.entity.SysParamsEntity;

/**
 * Parameter management
 */
public interface SysParamsService extends BaseService<SysParamsEntity> {

    PageData<SysParamsDTO> page(Map<String, Object> params);

    List<SysParamsDTO> list(Map<String, Object> params);

    SysParamsDTO get(Long id);

    void save(SysParamsDTO dto);

    void update(SysParamsDTO dto);

    void delete(String[] ids);

    /**
     * Get a parameter's value by parameter code
     *
     * @param paramCode parameter code
     * @param fromCache whether to read from cache
     */
    String getValue(String paramCode, Boolean fromCache);

    /**
     * Get the value as an object by parameter code
     *
     * @param paramCode parameter code
     * @param clazz     object class
     */
    <T> T getValueObject(String paramCode, Class<T> clazz);

    /**
     * Update the value by parameter code
     *
     * @param paramCode  parameter code
     * @param paramValue parameter value
     */
    int updateValueByCode(String paramCode, String paramValue);

    /**
     * Initialize the server secret
     */
    void initServerSecret();

    /**
     * Get the system feature menu configuration
     *
     * @param fromCache whether to read from cache
     * @return system feature menu configuration JSON string
     */
    String getSystemWebMenu(boolean fromCache);

    /**
     * Update the system feature menu configuration (automatically cleans up feature-related plugins)
     *
     * @param configJson new system feature menu configuration JSON
     */
    void updateSystemWebMenu(String configJson);
}
