package xiaozhi.modules.sms.service;

/**
 * SMS service interface
 *
 * @author zjy
 * @since 2025-05-12
 */
public interface SmsService {

    /**
     * Send a verification code SMS
     * @param phone phone number
     * @param VerificationCode verification code
     */
    void sendVerificationCodeSms(String phone, String VerificationCode) ;
}
