package martinandersson.com.client.login;

import com.nimbusds.srp6.BigIntegerUtils;
import com.nimbusds.srp6.SRP6Routines;
import com.nimbusds.srp6.SRP6VerifierGenerator;
import java.math.BigInteger;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import martinandersson.com.client.ServerConnection;
import martinandersson.com.library.Constants;

/**
 * Utility class for client procedures.<p>
 * 
 * Currently, only the procedure to register a user is provided.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public final class ClientProcedures {
    private static final Logger LOGGER = Logger.getLogger(ClientProcedures.class.getName());
    
    private ClientProcedures() {
        // Is utility class
    }
    
    /**
     * Will compute salt and verifier, then send that to the server along with
     * a username as a request to register a new user with the server.<p>
     * 
     * Note that this registration happens over an insecure line. In a real
     * production environment, the verifier should be sent securely.
     * 
     * @param conn server connection
     * @param username user username
     * @param password user password
     */
    public static void registerUser(ServerConnection conn, String username, String password) {
        SRP6VerifierGenerator gen = new SRP6VerifierGenerator(Constants.CRYPTO_PARAMS);
        
        
        /*
         * If salt was hard coded to a test value and not random, then the
         * computed verifier would be the same given we also use the same
         * username.
         *
         * But in a production environment, salt is random AND usernames are
         * unique as well, so is it really necessary for the server to require a
         * unique constraint on the verifier? I think it is a bit overkill. But
         * then again, I'm not a crypto guy and cannot say for sure.
         */
        
        
        BigInteger salt = new BigInteger(SRP6Routines.generateRandomSalt(Constants.SALT_LENGTH));
        LOGGER.info(() -> "Computed salt: " + BigIntegerUtils.toHex(salt));
        
        // Compute verifier 'v'
        BigInteger v = gen.generateVerifier(salt, username, password);
        LOGGER.info(() -> "Computed verifier: " + BigIntegerUtils.toHex(v));
        
        // Send salt and v to server
        sendToServer(conn, username, salt, v);
    }
    
    private static void sendToServer(ServerConnection conn, String username, BigInteger salt, BigInteger v) {
        JsonObjectBuilder b = Json.createObjectBuilder();
        
        b.add("username", username)
         .add("salt", BigIntegerUtils.toHex(salt))
         .add("verifier", BigIntegerUtils.toHex(v));
        
        conn.sendBlock(b.build());
    }
}