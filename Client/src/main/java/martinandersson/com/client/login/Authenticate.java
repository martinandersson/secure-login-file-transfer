package martinandersson.com.client.login;

import com.nimbusds.srp6.BigIntegerUtils;
import com.nimbusds.srp6.SRP6ClientCredentials;
import com.nimbusds.srp6.SRP6ClientSession;
import com.nimbusds.srp6.SRP6ClientSession.State;
import com.nimbusds.srp6.SRP6Exception;
import java.math.BigInteger;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import martinandersson.com.client.ServerConnection;
import martinandersson.com.library.Constants;

/**
 * Is the client's view of the SRP authentication process and outbound messages
 * sent to the server connection as required by the authentication procedure.<p>
 * 
 * Once the user is authenticated, his session key may be retrieved using
 * {@linkplain #getSessionKey() getSessionKey()}.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class Authenticate
{
    private final ServerConnection conn;
    
    private SRP6ClientSession session;
    
    /** Last state (step) that has been completed. */
    private SRP6ClientSession.State completed;
    
    
    
    public Authenticate(ServerConnection connection) {
        this.conn = connection;
        completed = State.INIT;
    }
    
    
    
    /**
     * Will start a new authentication session and send username (identity 'I')
     * to server.
     * 
     * @param username the username
     * @param password the password
     */
    public void step1(String username, String password) {
        SRP6ClientSession s = new SRP6ClientSession(Constants.SRP6A_TIMEOUT);
        
        s.step1(username, password);
        
        JsonObjectBuilder b = Json.createObjectBuilder();
        b.add("username", username);
        
        conn.sendBlock(b.build());
        
        session = s;
        completed = State.STEP_1;
    }
    
    /**
     * Accept salt and B, authenticate B, then send A and M1 to server.
     * 
     * @param saltAndB as received from server
     * 
     * @throws IllegalStateException if invoked in a state other than step 1
     * @throws SRP6Exception if timeout or invalid server B
     */
    public void step2(JsonObject saltAndB) throws SRP6Exception {
        if (completed != State.STEP_1) {
            throw new IllegalStateException();
        }
        
        BigInteger salt = BigIntegerUtils.fromHex(saltAndB.getString("salt"));
        BigInteger B = BigIntegerUtils.fromHex(saltAndB.getString("B"));
        
        SRP6ClientCredentials cred = session.step2(Constants.CRYPTO_PARAMS, salt, B); // <-- SRP6Exception, IllegalStateException
        
        // Send client public value 'A' and evidence message 'M1' to server
        JsonObjectBuilder b = Json.createObjectBuilder();
        
        b.add("A", BigIntegerUtils.toHex(cred.A))
         .add("M1", BigIntegerUtils.toHex(cred.M1));
        
        conn.sendBlock(b.build());
    }
    
    /**
     * Accept M2 from server, authenticate M2 and complete step 3.
     * 
     * @param m2 as received from server
     * 
     * @throws IllegalStateException if invoked in a state other than step 2
     * @throws SRP6Exception if the session has timed out or the server evidence
     *         message 'M2' is invalid. 
     */
    public void step3(JsonObject m2) throws SRP6Exception {
        BigInteger __m2 = BigIntegerUtils.fromHex(m2.getString("M2"));
        session.step3(__m2);
    }
    
    public boolean isAuthenticated() {
        // getState() returns the SUCCESSFULLY COMPLETED state
        return session != null && session.getState() == State.STEP_3;
    }
    
    public BigInteger getSessionKey() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Not authenticated.");
        }
        
        return session.getSessionKey(false);
    }
}