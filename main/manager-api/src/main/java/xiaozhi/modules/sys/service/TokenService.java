package xiaozhi.modules.sys.service;

public interface TokenService {
    /**
     * Generate token
     *
     * @param userId
     * @return
     */
    String createToken(long userId);
}
