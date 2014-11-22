package martinandersson.com.client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 * Is the client's view of the server, by which client speak with the server.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class ServerConnection
{
    private static final Logger LOGGER = Logger.getLogger(ServerConnection.class.getName());
    
    
    /*
     * Why is the next declared singleton field marked volatile?
     * 
     * Firstly, see getInstance() and try to imagine the implementation without
     * the extra "conn" variable. To simplify the discussion, I shall pretend
     * that the getInstance() method does not make use of the "conn" variable
     * and that the method read and write to the "instance" field directly. I
     * will explain the use of the "conn" variable at the end of the following
     * discussion.
     * 
     * To understand why volatile is needed, one must understand what exactly a
     * synchronized block do.
     * 
     * Had the entire method been marked synchronized, then exiting the method
     * would establish a happens-before relationship with a subsequent
     * invocation of the method. All actions done by a thread in the first
     * invocation, would be visible for the thread making the second method
     * invocation. From the Java Language Specification (JLS) version 8, section
     * "17.4.5 Happens-before Order":
     * 
     *     "An unlock on a monitor happens-before every subsequent lock on that
     *      monitor."
     * 
     * Without the happens-before relationship, then action reordering and stale
     * CPU caches could mean that what has been done by one thread is not
     * visible by another thread (happens-before doesn't necessarily flush the
     * cache to heap).
     * 
     * With a clearly defined happens-before relationship between method
     * invocations, then the instance variable would not have to be marked
     * volatile.
     * 
     * But of course, it feels a bit overkill to synchronize on a simple null
     * check. Most of the time, the instance will not be null. Making the check
     * synchronized is an unnecessary cost. Hence we move the synchronized block
     * and require a lock only if the instance is null.
     * 
     * Some time will pass between the null check and when the thread acquire
     * the monitor. In fact, locking might even block. So a second null check
     * must be added.
     * 
     * Now the interesting part begin. Examining the bytecodes reveal that the
     * statement "instance = new ServerConnection()" is more than just two
     * actions. Least of all, it is three. One action says to create the object
     * and allocate memory for it. One action says to invoke the constructor.
     * Finally, some action must make the CPU actually write the memory address
     * to our field.
     * 
     * Reordering is allowed within a synchronized block. Hence, it could be
     * that the memory address leak and another thread might read that address,
     * using an object whose constructor has not been called yet.
     * 
     * If we want to be assured that no foreign thread ever see a not fully
     * constructed object, then we must mark the field itself volatile. The
     * keyword promise to create a happens-before relationship between all
     * actions that read and write the field! Now all of a sudden, the
     * constructor must be called before leaking the memory address. Same
     * section of the JLS as previously quoted says:
     * 
     *     "A write to a volatile field happens-before every subsequent read of
     *      that field."
     * 
     * Negate the logic and we also get that a read of the field must happen
     * after a preceding write of the field - not that is matter for this
     * discussion.
     * 
     * So, why the extra "conn" variable then? Well according to Wikipedia:
     * 
     *     http://en.wikipedia.org/wiki/Double-checked_locking
     * 
     * Accessing the volatile field just once can improve the performance by up
     * to 25 percent. Don't believe it? Apparently, the implementation of
     * File.toPath() do so who am I to disagree.
     * 
     * Also, note that an enum is the only really safe way to create a singleton
     * in Java and doesn't require application code to handle synchronization.
     * An enum should be the preferred method to achieve static lazy
     * initialization. Joshua Bloch's book Effective Java has some cool
     * information to share about the safety of singletons.
     */
    private static volatile ServerConnection instance = null;
    
    public static ServerConnection getInstance() {
        ServerConnection conn = instance;
        if (conn == null) {
            synchronized (ServerConnection.class) {
                conn = instance;
                if (conn == null) {
                    conn = new ServerConnection();
                    instance = conn;
                }
            }
        }
        
        return conn;
    }
    
    
    private final BlockingQueue<JsonObject> messages;
    
    private Session session;
    
    private RemoteEndpoint.Async async;
    private RemoteEndpoint.Basic basic;
    
    
    
    private ServerConnection() {
        messages = new LinkedBlockingQueue<>(500);
    }
    
    
    
    public void connectToServer(String uri) throws DeploymentException, IOException {
        final URI typed;
        
        try {
            typed = new URI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        
        WebSocketListener listener = new WebSocketListener();
        
        // Possible source of all exceptions:
        session = container.connectToServer(listener, typed); // <-- this adds the life cycle methods.
        
        session.addMessageHandler(listener); // .. and this call adds the onMessage listener method.
        
        async = session.getAsyncRemote();
        basic = session.getBasicRemote();
    }
    
    public boolean isOpen() {
        return session != null && session.isOpen();
    }
    
    /**
     * Receive next message.<p>
     * 
     * This method returns immediately if a message is already available in the
     * inbound queue, but will wait if necessary for one to become available.
     * 
     * @return the message
     * 
     * @throws InterruptedException if thread is interrupted while waiting (blocking)
     */
    public JsonObject receiveNext() throws InterruptedException {
        return messages.take();
    }
    
    public Future<Void> sendAsync(JsonObject json) {
        return async.sendText(json.toString());
    }
    
    public void sendBlock(JsonObject json) {
        try {
            basic.sendText(json.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    public OutputStream getOutputStream() throws IOException {
        return basic.getSendStream();
    }
    
    
    
    private void trace(String method, Object... args) {
        LOGGER.info(() -> {
            String prefix = "ENTER " + method + ", ARGS: ";
            String args2 = Stream.of(args).map(Object::toString).collect(Collectors.joining());
            return prefix + args2;
        });
    }
    
    
    
    /*
     *  ----------------------
     * | WEBSOCKET LIFE CYCLE |
     *  ----------------------
     */
    
    private class WebSocketListener extends Endpoint implements MessageHandler.Whole<String> {
        
        /**
         * Prefer this cached factory instead of {@code Json.createReader()} or
         * other similar constructs. {@code Json.createReader()} will traverse
         * the classpath each time to look for the Json provider.<p>
         * 
         * Safe to use for concurrent threads.
         */
        private final JsonReaderFactory jsonReaderFactory = Json.createReaderFactory(null);
        
        @Override
        public void onOpen(Session session, EndpointConfig config) {
            trace("onOpen", session, config);
        }

        @Override
        public void onClose(Session session, CloseReason reason) {
            trace("onClose", reason);
        }

        @Override
        public void onError(Session session, Throwable throwable) {
            trace("onError", throwable);
        }

        @Override
        public void onMessage(String data) {
            LOGGER.info(() -> "Received message: " + data);

            final JsonObject obj;

            try (StringReader strReader = new StringReader(data);
                 JsonReader jsonReader = jsonReaderFactory.createReader(strReader);) {
                 obj = jsonReader.readObject();
            }

            try {
                messages.put(obj);
            } catch (InterruptedException e) {
                LOGGER.warning("Interrupted while waiting for space to become available, message lost.");
            }
        }
    }
}