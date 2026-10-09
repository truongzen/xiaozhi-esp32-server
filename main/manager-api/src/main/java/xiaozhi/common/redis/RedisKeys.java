package xiaozhi.common.redis;

/**
 * Redis key constants
 * Copyright (c) Renren Open Source All rights reserved.
 * Website: https://www.renren.io
 */
public class RedisKeys {
    /**
     * System parameter key
     */
    public static String getSysParamsKey() {
        return "sys:params";
    }

    /**
     * Captcha key
     */
    public static String getCaptchaKey(String uuid) {
        return "sys:captcha:" + uuid;
    }

    /**
     * Verification code key for unregistered devices
     */
    public static String getDeviceCaptchaKey(String captcha) {
        return "sys:device:captcha:" + captcha;
    }

    /**
     * User ID key
     */
    public static String getUserIdKey(Long userid) {
        return "sys:username:id:" + userid;
    }

    /**
     * Model name key
     */
    public static String getModelNameById(String id) {
        return "model:name:" + id;
    }

    /**
     * Model configuration key
     */
    public static String getModelConfigById(String id) {
        return "model:data:" + id;
    }

    /**
     * Cache key for voice name
     */
    public static String getTimbreNameById(String id) {
        return "timbre:name:" + id;
    }

    /**
     * Cache key for device count
     */
    public static String getAgentDeviceCountById(String id) {
        return "agent:device:count:" + id;
    }

    /**
     * Cache key for agent last connection time
     */
    public static String getAgentDeviceLastConnectedAtById(String id) {
        return "agent:device:lastConnected:" + id;
    }

    /**
     * Cache key for system configuration
     */
    public static String getServerConfigKey() {
        return "server:config";
    }

    /**
     * Cache key for voice details
     */
    public static String getTimbreDetailsKey(String id) {
        return "timbre:details:" + id;
    }

    /**
     * Version number key
     */
    public static String getVersionKey() {
        return "sys:version";
    }

    /**
     * OTA firmware ID key
     */
    public static String getOtaIdKey(String uuid) {
        return "ota:id:" + uuid;
    }

    /**
     * OTA firmware download count key
     */
    public static String getOtaDownloadCountKey(String uuid) {
        return "ota:download:count:" + uuid;
    }

    /**
     * Cache key for dictionary data
     */
    public static String getDictDataByTypeKey(String dictType) {
        return "sys:dict:data:" + dictType;
    }

    /**
     * Cache key for agent audio ID
     */
    public static String getAgentAudioIdKey(String uuid) {
        return "agent:audio:id:" + uuid;
    }

    /**
     * Cache key for SMS verification code
     */
    public static String getSMSValidateCodeKey(String phone) {
        return "sms:Validate:Code:" + phone;
    }

    /**
     * Cache key for SMS verification code last send time
     */
    public static String getSMSLastSendTimeKey(String phone) {
        return "sms:Validate:Code:" + phone + ":last_send_time";
    }

    /**
     * Cache key for SMS verification code send count today
     */
    public static String getSMSTodayCountKey(String phone) {
        return "sms:Validate:Code:" + phone + ":today_count";
    }

    /**
     * Chat history UUID mapping key
     */
    public static String getChatHistoryKey(String uuid) {
        return "agent:chat:history:" + uuid;
    }

    /**
     * Cache key for voice clone audio ID
     */
    public static String getVoiceCloneAudioIdKey(String uuid) {
        return "voiceClone:audio:id:" + uuid;
    }

    /**
     * Cache key for knowledge base
     */
    public static String getKnowledgeBaseCacheKey(String datasetId) {
        return "knowledge:base:" + datasetId;
    }

    /**
     * Key for temporary device registration flag
     */
    public static String getTmpRegisterMacKey(String deviceId) {
        return "tmp_register_mac:" + deviceId;
    }

    /**
     * OTA device binding
     */
    public static String getOtaActivationCode(String activationCode) {
        return "ota:activation:code:" + activationCode;
    }

    /**
     * OTA device MAC information
     */
    public static String getOtaDeviceActivationInfo(String deviceId) {
        return "ota:activation:data:" + deviceId;
    }

    /**
     * OTA upload count
     */
    public static String getOtaUploadCountKey(Long username) {
        return "ota:upload:count:" + username;
    }

    /**
     * Device address book cache key
     */
    public static String getAddressBookKey() {
        return "device:address_book:all";
    }

}
