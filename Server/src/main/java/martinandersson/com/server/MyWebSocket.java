package martinandersson.com.server;

import com.nimbusds.srp6.SRP6Exception;
import com.nimbusds.srp6.SRP6Exception.CauseType;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.crypto.NoSuchPaddingException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import martinandersson.com.library.AesGcmCipher;
import martinandersson.com.library.ServerStrategy;
import martinandersson.com.server.filereceiver.ByteArrayFileReceiver;
import martinandersson.com.server.filereceiver.ByteBufferFileReceiver;
import martinandersson.com.server.filereceiver.CopyInputStreamFileReceiver;
import martinandersson.com.server.filereceiver.FileReceiver;
import martinandersson.com.server.filereceiver.NoUseInputFileReceiver;
import martinandersson.com.server.filereceiver.SingleByteInputStreamFileReceiver;
import martinandersson.com.server.login.Credentials;
import martinandersson.com.server.login.SRP6ServerLogin;

/**
 * The server endpoint receive messages from client and respond according to the
 * following protocol that must be followed orderly:
 * 
 * <ol>
 *   <li>Client register a new user by sending username, salt and verifier to
 *       the server.</li>
 *   <li>Client authenticate the newly registered user using SRP (Secure Remote
 *       Protocol).</li>
 *   <li>Client may send a file:<ol>
 *      <li>Client send a request for a file transfer, providing 1) file name,
 *          2) server's receiving strategy, 3) whether or not to use encryption,
 *          and 4) if the transfer will be chunked.</li>
 *      <li>Server will setup his message handler and respond with an accept.</li>
 *      <li>Client begin sending bytes. If the file transfer was chunked, then
 *          client must complete the process with an end-of-file message once
 *          all chunks has been transferred.</li></ol></li>
 * </ol>
 * 
 * Unless something really unexpected happens, client may continue to send files
 * for as many times as he want to until the client disconnect.<p>
 * 
 * The server store all files and file chunks in a folder that is hard coded as
 * a constant in this class, namely {@code SAVE_DIR} (current value:
 * "{@value #SAVE_DIR}").<p>
 * 
 * On the server-side, the protocol is fully realized in method
 * {@linkplain #processJson(JsonObject) processJson(JsonObject)}. On the
 * client-side, different page controllers will guide the human user step by
 * step.<p>
 * 
 * Only binary data transfers (a file or a chunk thereof) may optionally be
 * encrypted. All other messages exchanged are not encrypted.<p>
 * 
 * This endpoint is not production friendly. The whole application is a
 * "proof of concept" with the chief goal of demonstrating SRP, AES/GCM and
 * binary data transfers over WebSocket. For example, a production friendly
 * endpoint would definitely close the client if he sends unauthenticated data,
 * which this implementation does not do.<p>
 * 
 * However, this endpoint will close the client if anything goes to hell during
 * the authentication process.
 * 
 * 
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
@javax.websocket.server.ServerEndpoint("/mywebsocket")
public class MyWebSocket
{
    private static final Logger LOGGER = Logger.getLogger(MyWebSocket.class.getName());
    
    /** Where to put all incoming files. Current value: {@value}. */
    private static final String SAVE_DIR = "C:/Temp";
    
    private static final EnumMap<SRP6Exception.CauseType, CloseReason> closeReasons;
    static {
        closeReasons = new EnumMap<>(CauseType.class);
        CloseReason unacceptable = new CloseReason(CloseCodes.CANNOT_ACCEPT, null);
        
        // "Invalid public client or server value ('A' or 'B')"
        closeReasons.put(CauseType.BAD_PUBLIC_VALUE, unacceptable);
        
        // "Invalid credentials (password)"
        closeReasons.put(CauseType.BAD_CREDENTIALS, unacceptable);
        
        // "SRP-6a authentication session timeout"
        closeReasons.put(CauseType.TIMEOUT, new CloseReason(CloseCodes.GOING_AWAY, null));
    }
    
    private static final EnumMap<ServerStrategy, Supplier<FileReceiver>> fileReceivers;
    static {
        fileReceivers = new EnumMap<>(ServerStrategy.class);
        
        fileReceivers.put(ServerStrategy.COPY_INPUT_STREAM,        CopyInputStreamFileReceiver::new);
        fileReceivers.put(ServerStrategy.NO_USE_INPUT_STREAM,      NoUseInputFileReceiver::new);
        fileReceivers.put(ServerStrategy.SINGLE_BYTE_INPUT_STREAM, SingleByteInputStreamFileReceiver::new);
        fileReceivers.put(ServerStrategy.BYTE_ARRAY,               ByteArrayFileReceiver::new);
        fileReceivers.put(ServerStrategy.BYTE_BUFFER,              ByteBufferFileReceiver::new);
    }
    
    /**
     * Prefer this cached factory instead of {@code Json.createReader()} or
     * other similar constructs. {@code Json.createReader()} will traverse the
     * classpath each time to look for the JSON provider.<p>
     * 
     * Safe to use by concurrent threads.
     */
    private static final JsonReaderFactory jsonReaderFactory = Json.createReaderFactory(null);
    
    
    
    
    private Credentials creds;
    private SRP6ServerLogin login;
    
    private AesGcmCipher aesGcmCipher;
    
    private FileReceiver receiver; // <-- field only used during chunked transfers
    
    private Session session;
    
    private Async async;
    private Basic basic;
    
    
    
    /*
     *  --------------------
     * | MESSAGE PROCESSING |
     *  --------------------
     */
    
    private void processJson(JsonObject json) throws SRP6Exception, GeneralSecurityException {
        if (login == null) {
            // Accept registration
            creds = new Credentials(json);
            LOGGER.info(() -> "Registered \"new\" user: " + creds);
            
            login = new SRP6ServerLogin(creds, basic);
            login.whenAuthenticated(key -> {
                try {
                    aesGcmCipher = new AesGcmCipher(key);
                    return true;
                }
                catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                    throw new RuntimeException("Failed to create a Cipher from session key!", e);
                }
            });
        }
        else if (!login.isAuthenticated()) {
            // Proceed with SRP
            login.handle(json); // <-- SRP6Exception
        }
        else {
            if (receiver == null) {
                // No chunked file transfer is active:
                processNewFileTransferRequest(json);
            }
            else {
                // Chunked file transfer is active:
                processChunkedTransferCompleted(json);
            }
        }
    }
    
    private void processNewFileTransferRequest(JsonObject json) throws GeneralSecurityException {
        Path file = Paths.get(SAVE_DIR, json.getString("file"));

        ServerStrategy strategy = ServerStrategy.valueOf(json.getString("strategy"));
        FileReceiver receiver = fileReceivers.get(strategy).get();

        AesGcmCipher cipher = json.getBoolean("encrypted") ? aesGcmCipher : null;
        boolean chunked = json.getBoolean("chunked");

        if (chunked) {
            this.receiver = receiver;
        }

        receiver.init(session, file, chunked, cipher, exception -> {
            JsonObjectBuilder b = Json.createObjectBuilder();
            // Report any problems to client:
            if (exception.isPresent()) {
                this.receiver = null;
                b.add("problem", exception.get().toString());
            } else {
                b.add("problem", "");
            }

            try {
                basic.sendText(b.build().toString());
            }
            catch (IOException e1) {
                try {
                    __onError(e1);
                }
                catch (IOException e2) {
                    LOGGER.log(Level.WARNING, "Failed to send a file confirmation, then failed some more :'(", e2);
                }
            }
        });

        async.sendText(Json.createObjectBuilder().add("accept", true).build().toString());
    }
    
    private void processChunkedTransferCompleted(JsonObject json) {
        if (!json.getBoolean("eof")) {
            LOGGER.warning("Did not expect the message we received.");
        }

        try {
            receiver.completeChunked();
        } catch (IOException e) {
            /*
             * WARNING! If anything like this software architecture would run in
             * production code, then catching a MergeException here should be
             * reported back to client because that can only mean that the file
             * wasn't properly received. I ignore that in this test project.
             */
            LOGGER.log(Level.WARNING, "Failed to merge or delete file chunks after completing a chunked file transfer.", e);
        }

        receiver = null;
    }
    
    
    
    /*
     *  ----------------------
     * | WEBSOCKET LIFE CYCLE |
     *  ----------------------
     */
    
    @OnOpen
    public void __onOpen(Session session, EndpointConfig config) {
        trace("__onOpen", session, config);
        
        this.session = session;
        
        this.async = session.getAsyncRemote();
        this.basic = session.getBasicRemote();
        
        LOGGER.info(() -> "Max buffer size of text mesages: " + session.getMaxTextMessageBufferSize());
        LOGGER.info(() -> "Max buffer size of binary mesages: " + session.getMaxBinaryMessageBufferSize());
    }
    
    @OnClose
    public void __onClose(Session session, CloseReason reason) {
        trace("__onClose", session, reason);
    }
    
    @OnMessage
    public void __onMessage(String data) throws SRP6Exception, GeneralSecurityException {
        trace("__onMessage", data);
        
        final JsonObject obj;
        
        try (StringReader strRead = new StringReader(data);
             JsonReader jsonRead = jsonReaderFactory.createReader(strRead);) {
             obj = jsonRead.readObject();
        }
        
        processJson(obj);
    }
    
    @OnError
    public void __onError(Throwable throwable) throws IOException {
        LOGGER.log(Level.WARNING, "Unhandled throwable:", throwable);
        
        if (throwable instanceof SRP6Exception) {
            SRP6Exception e = (SRP6Exception) throwable;
            session.close(closeReasons.get(e.getCauseType()));
        }
        
        else if (session.isOpen()) {
            // Do nothing, this is a test program so to speak.
//            session.close(new CloseReason(CloseCodes.CLOSED_ABNORMALLY, null));
        }
    }
    
    
    
    /*
     *  --------------
     * | INTERNAL API |
     *  --------------
     */
    
    private void trace(String method, Object... args) {
        LOGGER.info(() -> {
            String prefix = "ENTER " + method + ", ARGS: ";
            
            String stringified = Stream.of(args)
                    .map(Object::toString)
                    .collect(Collectors.joining());
            
            return prefix + stringified;
        });
    }
}