package xiaozhi.modules.sys.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.sys.dto.SysDictTypeDTO;
import xiaozhi.modules.sys.entity.SysDictTypeEntity;
import xiaozhi.modules.sys.vo.SysDictTypeVO;

/**
 * Data dictionary
 */
public interface SysDictTypeService extends BaseService<SysDictTypeEntity> {

    /**
     * Page dictionary types
     *
     * @param params query parameters, including paging info and query conditions
     * @return paged dictionary type data
     */
    PageData<SysDictTypeVO> page(Map<String, Object> params);

    /**
     * Get dictionary type info by ID
     *
     * @param id dictionary type ID
     * @return dictionary type object
     */
    SysDictTypeVO get(Long id);

    /**
     * Save dictionary type info
     *
     * @param dto dictionary type data transfer object
     */
    void save(SysDictTypeDTO dto);

    /**
     * Update dictionary type info
     *
     * @param dto dictionary type data transfer object
     */
    void update(SysDictTypeDTO dto);

    /**
     * Delete dictionary types
     *
     * @param ids array of dictionary type IDs to delete
     */
    void delete(Long[] ids);

    /**
     * List all dictionary types
     *
     * @return dictionary type list
     */
    List<SysDictTypeVO> list(Map<String, Object> params);
}