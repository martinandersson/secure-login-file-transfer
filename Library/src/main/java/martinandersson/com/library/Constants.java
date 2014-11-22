package martinandersson.com.library;

import com.nimbusds.srp6.SRP6CryptoParams;

/**
 * Constants shared by both client and server.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public final class Constants
{
    private Constants() {
        // Must not be created
    }
    
    /**
     * The SRP-6a authentication session timeout in seconds. If the
     * authenticating counterpart (server or client)  fails to respond within
     * the specified time the session will be closed.<p>
     * 
     * Current value: {@value} seconds.
     * 
     * @see com.nimbusds.srp6.SRP6ClientSession
     * @see com.nimbusds.srp6.SRP6ServerSession
     */
    public final static int SRP6A_TIMEOUT = 7;
    
    /**
     * Salt length in bytes.<p>
     * 
     * Current value: {@value}.
     */
    public final static int SALT_LENGTH = 16;
    
    /**
     * 
     */
    public final static SRP6CryptoParams CRYPTO_PARAMS = SRP6CryptoParams.getInstance(512, "SHA-256");
}