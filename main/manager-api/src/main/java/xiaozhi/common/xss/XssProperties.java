package xiaozhi.common.xss;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * XSS configuration properties
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
@Data
@ConfigurationProperties(prefix = "renren.xss")
public class XssProperties {
    /**
     * Whether XSS filtering is enabled
     */
    private boolean enabled;
    /**
     * Excluded URL list
     */
    private List<String> excludeUrls = Collections.emptyList();
}
