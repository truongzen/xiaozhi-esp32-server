package xiaozhi.modules.device.dao;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import xiaozhi.modules.device.entity.OtaEntity;

/**
 * OTA firmware management
 */
@Mapper
public interface OtaDao extends BaseMapper<OtaEntity> {
    
}