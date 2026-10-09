package xiaozhi.modules.sys.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.sys.entity.SysParamsEntity;

/**
 * Parameter management
 */
@Mapper
public interface SysParamsDao extends BaseDao<SysParamsEntity> {
    /**
     * Query the value by parameter code
     *
     * @param paramCode parameter code
     * @return parameter value
     */
    String getValueByCode(String paramCode);

    /**
     * Get the list of parameter codes
     *
     * @param ids ids
     * @return list of parameter codes
     */
    List<String> getParamCodeList(String[] ids);

    /**
     * Update the value by parameter code
     *
     * @param paramCode  parameter code
     * @param paramValue parameter value
     */
    int updateValueByCode(@Param("paramCode") String paramCode, @Param("paramValue") String paramValue);
}
