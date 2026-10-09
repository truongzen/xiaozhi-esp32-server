package xiaozhi.common.service;

import java.io.Serializable;
import java.util.Collection;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

/**
 * Base service interface that all Service interfaces must extend
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
public interface BaseService<T> {
    Class<T> currentModelClass();

    /**
     * <p>
     * Insert a record (selective fields, strategy-based insert)
     * </p>
     *
     * @param entity entity object
     */
    boolean insert(T entity);

    /**
     * <p>
     * Insert (batch); this method does not support Oracle or SQL Server
     * </p>
     *
     * @param entityList entity collection
     */
    boolean insertBatch(Collection<T> entityList);

    /**
     * <p>
     * Insert (batch); this method does not support Oracle or SQL Server
     * </p>
     *
     * @param entityList entity collection
     * @param batchSize  insert batch size
     */
    boolean insertBatch(Collection<T> entityList, int batchSize);

    /**
     * <p>
     * Update selectively by ID
     * </p>
     *
     * @param entity entity object
     */
    boolean updateById(T entity);

    /**
     * <p>
     * Update records according to the whereEntity condition
     * </p>
     *
     * @param entity        entity object
     * @param updateWrapper entity wrapper for the operation
     *                      {@link com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper}
     */
    boolean update(T entity, Wrapper<T> updateWrapper);

    /**
     * <p>
     * Batch update by ID
     * </p>
     *
     * @param entityList entity collection
     */
    boolean updateBatchById(Collection<T> entityList);

    /**
     * <p>
     * Batch update by ID
     * </p>
     *
     * @param entityList entity collection
     * @param batchSize  update batch size
     */
    boolean updateBatchById(Collection<T> entityList, int batchSize);

    /**
     * <p>
     * Query by ID
     * </p>
     *
     * @param id primary key ID
     */
    T selectById(Serializable id);

    /**
     * <p>
     * Delete by ID
     * </p>
     *
     * @param id primary key ID
     */
    boolean deleteById(Serializable id);

    /**
     * <p>
     * Delete (batch delete by ID)
     * </p>
     *
     * @param idList primary key ID list
     */
    boolean deleteBatchIds(Collection<? extends Serializable> idList);
}