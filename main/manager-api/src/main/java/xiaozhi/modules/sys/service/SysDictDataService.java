package xiaozhi.modules.sys.service;

import java.util.List;
import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.common.service.BaseService;
import xiaozhi.modules.sys.dto.SysDictDataDTO;
import xiaozhi.modules.sys.entity.SysDictDataEntity;
import xiaozhi.modules.sys.vo.SysDictDataItem;
import xiaozhi.modules.sys.vo.SysDictDataVO;

/**
 * Data dictionary
 */
public interface SysDictDataService extends BaseService<SysDictDataEntity> {

    /**
     * Page data dictionary entries
     *
     * @param params query parameters, including paging info and query conditions
     * @return paged data dictionary results
     */
    PageData<SysDictDataVO> page(Map<String, Object> params);

    /**
     * Get a data dictionary entity by ID
     *
     * @param id unique identifier of the data dictionary entity
     * @return details of the data dictionary entity
     */
    SysDictDataVO get(Long id);

    /**
     * Save a new data dictionary item
     *
     * @param dto data transfer object for saving the item
     */
    void save(SysDictDataDTO dto);

    /**
     * Update a data dictionary item
     *
     * @param dto data transfer object for updating the item
     */
    void update(SysDictDataDTO dto);

    /**
     * Delete data dictionary items
     *
     * @param ids array of IDs of the items to delete
     */
    void delete(Long[] ids);

    /**
     * Delete the dictionary data belonging to a dictionary type ID
     *
     * @param dictTypeId dictionary type ID
     */
    void deleteByTypeId(Long dictTypeId);

    /**
     * Get the dictionary data list by dictionary type
     *
     * @param dictType dictionary type
     * @return dictionary data list
     */
    List<SysDictDataItem> getDictDataByType(String dictType);

}