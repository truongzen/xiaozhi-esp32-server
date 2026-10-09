package xiaozhi.modules.security.user;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import xiaozhi.common.user.UserDetail;

/**
 * Shiro utility class
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
public class SecurityUser {

    public static Subject getSubject() {
        try {
            return SecurityUtils.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get user info
     */
    public static UserDetail getUser() {
        Subject subject = getSubject();
        if (subject == null) {
            return new UserDetail();
        }

        UserDetail user = (UserDetail) subject.getPrincipal();
        if (user == null) {
            return new UserDetail();
        }

        return user;
    }

    public static String getToken() {
        return getUser().getToken();
    }

    /**
     * Get user ID
     */
    public static Long getUserId() {
        return getUser().getId();
    }
}