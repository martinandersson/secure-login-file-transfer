package martinandersson.com.server.login;

import com.nimbusds.srp6.BigIntegerUtils;
import com.nimbusds.srp6.SRP6ClientSession;
import com.nimbusds.srp6.SRP6ClientSession.State;
import com.nimbusds.srp6.SRP6Exception;
import com.nimbusds.srp6.SRP6ServerSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.websocket.RemoteEndpoint;
import javax.websocket.RemoteEndpoint.Basic;
import martinandersson.com.library.Constants;

/**
 * Is the server's abstraction of the login, or rather, authentication process
 * of a user.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class SRP6ServerLogin
{
    private static final Logger LOGGER = Logger.getLogger(SRP6ServerLogin.class.getName());
    
    private final Credentials creds;
    private final Basic basic;
    
    private SRP6ServerSession session;
    
    private Predicate<BigInteger> keyTest;
    
    private SRP6ClientSession.State completed;
    
    
    private BigInteger B;
    
    private BigInteger M2;
    
    
    public SRP6ServerLogin(Credentials credentials, RemoteEndpoint.Basic basic) {
        this.creds = credentials;
        this.basic = basic;
        
        completed = State.INIT;
    }
    
    /**
     * Application code may ask to test the key when user has become
     * authenticated to have a final saying.<p>
     * 
     * If provided predicate return {@code false}, then the key is discarded and
     * the state will somehow be reverted (TODO: implement). If the predicate
     * return {@code true}, then the user will be authenticated just like if at
     * this point in time - no predicate at all was provided.<p>
     * 
     * All unchecked exceptions is caught (during the final step) and apply
     * the same logic as a predicate that return {@code false}. Then the
     * exception will be rethrowed.<p>
     * 
     * Previously assigned key tests are replaced with no warning. Providing
     * {@code null} is legal and may discard a previously stored key tester.<p>
     * 
     * Note that the predicate is called when server has authenticated the
     * client (received and approved 'M1', then sent 'M2'). In the real world,
     * it is still possible for the client to not authenticate the server
     * (client received but did not approve 'M2').
     * 
     * @param keyTest key tester
     */
    public void whenAuthenticated(Predicate<BigInteger> keyTest) {
        if (isAuthenticated()) {
            throw new IllegalStateException("Session key already accepted.");
        }
        else {
            this.keyTest = keyTest;
        }
    }
    
    public boolean isAuthenticated() {
        return completed == State.STEP_2 && session != null; // application code that test the key might nullify our session
    }
    
    public BigInteger getSessionKey() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }
        
        return session.getSessionKey(false);
    }
    
    public void handle(JsonObject loginMsg) throws SRP6Exception {
        switch (completed) {
            case INIT:
                step1(loginMsg);
                LOGGER.info(() -> "Step 1 finished successfully.");
                break;
                
            case STEP_1:
                step2(loginMsg);
                LOGGER.info(() -> "Step 2 finished successfully, user is authenticated.");
                
                // Erm, I do hope you understand that this is a test project!!
                LOGGER.info(() -> "Session key: " + getSessionKey());
                break;
            
            default:
                throw new AssertionError("Has no handler for " + completed);
        }
    }
    
    /**
     * Server begin new authentication session upon receiving the client's
     * username.
     * 
     * @param username as provided by client
     * 
     * @return the server session
     */
    private void step1(JsonObject msg) {
        final String username = msg.getString("username");
        
        if (!creds.getUsername().equals(username)) {
            throw new IllegalArgumentException("Unknown username.");
        }
        
        // Server: begin new authentication session on receiving the client request:
        session = new SRP6ServerSession(Constants.CRYPTO_PARAMS, Constants.SRP6A_TIMEOUT);
        
        // Compute the public server value 'B'
        B = session.step1(username, creds.getSalt(), creds.getVerifier());
        
        // Respond with salt 's' and public server value 'B'
        
        JsonObjectBuilder b = Json.createObjectBuilder();
        
        b.add("salt", creds.getSaltHex())
         .add("B", BigIntegerUtils.toHex(B));
        
        send(b.build());
        
        completed = State.STEP_1;
    }
    
    /**
     * Completes user authentication and compute own evidence message 'M2', then
     * send that to client.
     * 
     * @param A as provided by client
     * @param M1 as provided by client
     * 
     * @throws SRP6Exception if session has timed out, the client public value
     *         'A' is invalid or the user credentials are invalid
     */
    private void step2(JsonObject msg) throws SRP6Exception {
        BigInteger A = BigIntegerUtils.fromHex(msg.getString("A"));
        BigInteger M1 = BigIntegerUtils.fromHex(msg.getString("M1"));
        
        M2 = session.step2(A, M1); // <-- thrower of SRP6Exception
        
        // Send M2
        JsonObject reply = Json.createObjectBuilder()
                .add("M2", BigIntegerUtils.toHex(M2))
                .build();
        
        send(reply);
        
        completed = State.STEP_2;
        
        testKey();
    }
    
    private void testKey() { // ..or something else, like "verifyKey"?
        if (keyTest == null) {
            return;
        }
        
        try {
            boolean accepted = keyTest.test(getSessionKey());
            
            if (!accepted) {
                session = null;
            }
        }
        catch (RuntimeException e) {
            session = null;
            throw e;
        }
    }
    
    private void send(JsonObject obj) {
        try {
            basic.sendText(obj.toString());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}