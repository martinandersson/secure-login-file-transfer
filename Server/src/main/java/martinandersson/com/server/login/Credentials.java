package martinandersson.com.server.login;

import com.nimbusds.srp6.BigIntegerUtils;
import java.math.BigInteger;
import javax.json.JsonObject;
import martinandersson.com.library.Constants;

/**
 * Qoute from: http://en.wikipedia.org/wiki/Secure_Remote_Password_protocol
 * 
 * <pre>{@code
 * 
 *     "Note server Steve should enforce a uniqueness constraint on all "v" to
 *      protect against someone stealing his database observing which users have
 *      identical passwords."
 * 
 * }</pre>
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Credentials
{   
    private final String username;
    private final BigInteger salt;
    private final BigInteger verifier;
    
    public Credentials(JsonObject json) {
        this.username = json.getString("username");
        this.salt = BigIntegerUtils.fromHex(json.getString("salt"));
        
        // Stolen from impl. of BigInteger.toByteArray():
        final int byteLen = (this.salt.bitLength() / 8) + 1;
        
        if (byteLen != Constants.SALT_LENGTH) {
            throw new IllegalArgumentException("Salt required to be " + Constants.SALT_LENGTH + " bytes long.");
        }
        
        this.verifier = BigIntegerUtils.fromHex(json.getString("verifier"));
    }
    
    public Credentials(String username, BigInteger salt, BigInteger verifier) {
        this.username = username;
        this.salt = salt;
        this.verifier = verifier;
    }
    
    public String getUsername() {
        return username;
    }
    
    public BigInteger getSalt() {
        return salt;
    }
    
    public String getSaltHex() {
        return BigIntegerUtils.toHex(getSalt());
    }
    
    public BigInteger getVerifier() {
        return verifier;
    }
    
    public String getVerifierHex() {
        return BigIntegerUtils.toHex(getVerifier());
    }

    @Override
    public String toString() {
        return new StringBuilder(Credentials.class.getSimpleName())
                .append('[')
                  .append("username=").append(username)
                  .append(", salt=").append(getSaltHex())
                  .append(", verifier=").append(getVerifierHex())
                .append(']')
                .toString();
    }
}