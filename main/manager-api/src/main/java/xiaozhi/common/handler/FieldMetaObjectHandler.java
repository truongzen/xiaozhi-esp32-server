package xiaozhi.common.handler;

import java.util.Date;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.user.UserDetail;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * Common fields, auto-filled values
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
@Component
public class FieldMetaObjectHandler implements MetaObjectHandler {
    private final static String CREATE_DATE = "createDate";
    private final static String CREATOR = "creator";
    private final static String UPDATE_DATE = "updateDate";
    private final static String UPDATER = "updater";

    private final static String DATA_OPERATION = "dataOperation";

    @Override
    public void insertFill(MetaObject metaObject) {
        UserDetail user = SecurityUser.getUser();
        Date date = new Date();

        // Creator
        strictInsertFill(metaObject, CREATOR, Long.class, user.getId());
        // Create time - supports both createDate and createdAt field names
        if (metaObject.hasSetter(CREATE_DATE)) {
            strictInsertFill(metaObject, CREATE_DATE, Date.class, date);
        }
        if (metaObject.hasSetter("createdAt")) {
            strictInsertFill(metaObject, "createdAt", Date.class, date);
        }

        // Updater
        strictInsertFill(metaObject, UPDATER, Long.class, user.getId());
        // Update time - supports both updateDate and updatedAt field names
        if (metaObject.hasSetter(UPDATE_DATE)) {
            strictInsertFill(metaObject, UPDATE_DATE, Date.class, date);
        }
        if (metaObject.hasSetter("updatedAt")) {
            strictInsertFill(metaObject, "updatedAt", Date.class, date);
        }

        // Data flag
        strictInsertFill(metaObject, DATA_OPERATION, String.class, Constant.DataOperation.INSERT.getValue());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Date date = new Date();

        // Updater
        strictUpdateFill(metaObject, UPDATER, Long.class, SecurityUser.getUserId());
        // Update time - supports both updateDate and updatedAt field names
        if (metaObject.hasSetter(UPDATE_DATE)) {
            strictUpdateFill(metaObject, UPDATE_DATE, Date.class, date);
        }
        if (metaObject.hasSetter("updatedAt")) {
            strictUpdateFill(metaObject, "updatedAt", Date.class, date);
        }

        // Data flag
        strictInsertFill(metaObject, DATA_OPERATION, String.class, Constant.DataOperation.UPDATE.getValue());
    }
}