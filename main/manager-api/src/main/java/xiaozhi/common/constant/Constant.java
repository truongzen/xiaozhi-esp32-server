package xiaozhi.common.constant;

import lombok.Getter;

/**
 * Constants
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
public interface Constant {
    /**
     * Success
     */
    int SUCCESS = 1;
    /**
     * Failure
     */
    int FAIL = 0;
    /**
     * OK
     */
    String OK = "OK";
    /**
     * User identifier
     */
    String USER_KEY = "userId";
    /**
     * Menu root node identifier
     */
    Long MENU_ROOT = 0L;
    /**
     * Department root node identifier
     */
    Long DEPT_ROOT = 0L;
    /**
     * Data dictionary root node identifier
     */
    Long DICT_ROOT = 0L;
    /**
     * Ascending
     */
    String ASC = "asc";
    /**
     * Descending
     */
    String DESC = "desc";
    /**
     * Create-time field name
     */
    String CREATE_DATE = "create_date";

    /**
     * Create-time field name
     */
    String ID = "id";

    /**
     * Data permission filter
     */
    String SQL_FILTER = "sqlFilter";

    /**
     * Current page number
     */
    String PAGE = "page";
    /**
     * Records per page
     */
    String LIMIT = "limit";
    /**
     * Sort field
     */
    String ORDER_FIELD = "orderField";
    /**
     * Sort order
     */
    String ORDER = "order";

    /**
     * Request header authorization identifier
     */
    String AUTHORIZATION = "Authorization";

    /**
     * Server secret
     */
    String SERVER_SECRET = "server.secret";

    /**
     * SM2 public key
     */
    String SM2_PUBLIC_KEY = "server.public_key";

    /**
     * SM2 private key
     */
    String SM2_PRIVATE_KEY = "server.private_key";

    /**
     * WebSocket address
     */
    String SERVER_WEBSOCKET = "server.websocket";

    /**
     * MQTT gateway configuration
     */
    String SERVER_MQTT_GATEWAY = "server.mqtt_gateway";

    /**
     * OTA address
     */
    String SERVER_OTA = "server.ota";

    /**
     * Whether user registration is allowed
     */
    String SERVER_ALLOW_USER_REGISTER = "server.allow_user_register";

    /**
     * Control panel address shown when issuing the six-digit verification code
     */
    String SERVER_FRONTED_URL = "server.fronted_url";

    /**
     * Path separator
     */
    String FILE_EXTENSION_SEG = ".";

    /**
     * MCP access point path
     */
    String SERVER_MCP_ENDPOINT = "server.mcp_endpoint";

    /**
     * MCP access point path
     */
    String SERVER_VOICE_PRINT = "server.voice_print";

    /**
     * MQTT secret
     */
    String SERVER_MQTT_SECRET = "server.mqtt_signature_key";

    /**
     * WebSocket authentication switch
     */
    String SERVER_AUTH_ENABLED = "server.auth.enabled";

    /**
     * System feature menu configuration
     */
    String SYSTEM_WEB_MENU = "system-web.menu";

    /**
     * No memory
     */
    String MEMORY_NO_MEM = "Memory_nomem";

    /**
     * Report chat history only (no memory summarization)
     */
    String MEMORY_MEM_REPORT_ONLY = "Memory_mem_report_only";

    /**
     * Mem0AI memory
     */
    String MEMORY_MEM0AI = "Memory_mem0ai";

    /**
     * PowerMem memory
     */
    String MEMORY_POWERMEM = "Memory_powermem";

    /**
     * Volcengine dual-channel voice clone
     */
    String VOICE_CLONE_HUOSHAN_DOUBLE_STREAM = "huoshan_double_stream";

    /**
     * RAG configuration type
     */
    String RAG_CONFIG_TYPE = "RAG";

    enum SysBaseParam {
        /**
         * ICP filing number
         */
        BEIAN_ICP_NUM("server.beian_icp_num"),
        /**
         * GA (public security) filing number
         */
        BEIAN_GA_NUM("server.beian_ga_num"),
        /**
         * System name
         */
        SERVER_NAME("server.name");

        private String value;

        SysBaseParam(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Training status
     */
    enum TrainStatus {
        /**
         * Not trained
         */
        NOT_TRAINED(0),
        /**
         * Training
         */
        TRAINING(1),
        /**
         * Trained
         */
        TRAINED(2),
        /**
         * Training failed
         */
        TRAIN_FAILED(3);

        private final int code;

        TrainStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /**
     * System SMS
     */
    enum SysMSMParam {
        /**
         * Aliyun access key ID
         */
        ALIYUN_SMS_ACCESS_KEY_ID("aliyun.sms.access_key_id"),
        /**
         * Aliyun access key secret
         */
        ALIYUN_SMS_ACCESS_KEY_SECRET("aliyun.sms.access_key_secret"),
        /**
         * Aliyun SMS signature
         */
        ALIYUN_SMS_SIGN_NAME("aliyun.sms.sign_name"),
        /**
         * Aliyun SMS template
         */
        ALIYUN_SMS_SMS_CODE_TEMPLATE_CODE("aliyun.sms.sms_code_template_code"),
        /**
         * Maximum number of SMS messages per phone number
         */
        SERVER_SMS_MAX_SEND_COUNT("server.sms_max_send_count"),
        /**
         * Whether mobile registration is enabled
         */
        SERVER_ENABLE_MOBILE_REGISTER("server.enable_mobile_register");

        private String value;

        SysMSMParam(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Data status
     */
    enum DataOperation {
        /**
         * Inserted
         */
        INSERT("I"),
        /**
         * Modified
         */
        UPDATE("U"),
        /**
         * Deleted
         */
        DELETE("D");

        private String value;

        DataOperation(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Getter
    enum ChatHistoryConfEnum {
        IGNORE(0, "Do not record"),
        RECORD_TEXT(1, "Record text"),
        RECORD_TEXT_AUDIO(2, "Record both text and audio");

        private final int code;
        private final String name;

        ChatHistoryConfEnum(int code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    /**
     * Version number
     */
    public static final String VERSION = "0.9.7";

    /**
     * Invalid firmware URL
     */
    String INVALID_FIRMWARE_URL = "http://xiaozhi.server.com:8002/xiaozhi/otaMag/download/NOT_ACTIVATED_FIRMWARE_THIS_IS_A_INVALID_URL";

    /**
     * Dictionary type
     */
    enum DictType {
        /**
         * Mobile area code
         */
        MOBILE_AREA("MOBILE_AREA");

        private String value;

        DictType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}