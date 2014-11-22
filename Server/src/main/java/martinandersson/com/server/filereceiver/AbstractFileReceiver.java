package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import martinandersson.com.library.AesGcmCipher;

/**
 * Base class for all file receivers.<p>
 * 
 * This class take care of state, initiation of the cipher and life cycle of the
 * message handler.<p>
 * 
 * The architecture is arguable a bit complex and hard to read. It was chosen
 * so that the client may dynamically select which strategy the server should
 * implement.<p>
 * 
 * Real world applications would probably use just one! Given the current
 * constraints, the server must register and deregister a message handler with
 * the websocket session for each file transfer.
 * 
 * @param <T> type of inbound message
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public abstract class AbstractFileReceiver<T extends MessageHandler> implements FileReceiver
{
    private static final Logger LOGGER = Logger.getLogger(AbstractFileReceiver.class.getName());
    
    private static final ExecutorService executor;
    private static final ThreadFactory threads;
    
    static {
        ExecutorService exec = null;
        ThreadFactory factory = null;
        
        try {
            exec = InitialContext.doLookup("java:comp/DefaultManagedExecutorService");
        } catch (NamingException e) {
            LOGGER.log(Level.SEVERE, "Failed to lookup managed executor service.", e);
        }
        
        try {
            factory = InitialContext.doLookup("java:comp/DefaultManagedThreadFactory");
        } catch (NamingException e) {
            LOGGER.log(Level.SEVERE, "Failed to lookup managed thread factory.", e);
        }
        
        executor = exec;
        threads = factory;
    }
    
    
    
    private volatile State state = State.NOT_INITITATED;
    
    private Path currentChunk;
    private Path destination;
    
    private List<Path> chunks;
    
    private Session session;
    private T handler;
    
    private AesGcmCipher aesGcmCipher;
    private Cipher usedCipher;
    
    private boolean chunked;
    
    /** Never {@code null}, but might be an empty handler doing nothing. */
    private Consumer<Optional<? extends Throwable>> onCompletion;
    
    
    
    /*
     *  --------------
     * | EXTERNAL API |
     *  --------------
     */
    
    @Override
    public final void init(Session session, Path file, boolean chunked, AesGcmCipher cipher, Consumer<Optional<? extends Throwable>> onCompletion) {
        if (state != State.NOT_INITITATED) {
            throw new IllegalStateException("Already initiated. Current state: " + state);
        }
        
        this.chunked = chunked;
        this.session = session;
        this.destination = file;
        this.onCompletion = onCompletion != null ? onCompletion : e -> {};
        
        this.aesGcmCipher = cipher;
        
        if (chunked) {
            chunks = new ArrayList<>();
            startNewChunk();
        }
        else {
            currentChunk = destination;
        }
        
        registerHandler();
    }
    
    @Override
    public final void completeChunked() throws MergeException, DeleteException {
        deregisterHandler(null);
        mergeChunks();
        deleteChunks();
    }
    
    @Override
    public final State getState() {
        return state;
    }
    
    
    
    /*
     *  --------------
     * | SUBCLASS API |
     *  --------------
     */
    
    /**
     * Subclasses must provide the real message handler that will be registered
     * with the websocket session.
     * 
     * @return the message handler
     */
    protected abstract T getMessageHandler();
    
    protected final void startTransfer() {
        if (currentChunk == null) {
            throw new IllegalStateException("Not ready for receiving a file!");
        }
        
        LOGGER.info(() -> getClass().getSimpleName() + " receiving " + getFile() + "..");
        LOGGER.info(() -> "Using decryption? " + (aesGcmCipher == null ? "No." : "Yes!" + " (" + aesGcmCipher + ")"));
        
        this.state = State.RECEIVING;
    }
    
    /**
     * Reinitiate cipher for each new chunk and only upon the first invocation
     * of this method after a new chunk has begun.
     * 
     * @return the ready to use cipher instance
     * 
     * @throws GeneralSecurityException on like, failure
     */
    protected final Cipher getCipher() throws GeneralSecurityException {
        if (!isReceiving()) {
            throw new IllegalStateException("Not in the state of receiving, so what you gonna use a cipher for?");
        }
        
        if (aesGcmCipher == null) {
            return null;
        }
        
        if (usedCipher == null) {
            usedCipher = aesGcmCipher.initForDecryption();
        }
        
        return usedCipher;
    }
    
    protected final Path getFile() {
        if (state == State.NOT_INITITATED) {
            throw new IllegalStateException("Not initialized.");
        }
        
        return currentChunk;
    }
    
    protected final void transferFinished() {
        transferFinished(null);
    }
    
    /**
     * If not chunked, deregister handler at once, otherwise start a new chunk.
     * 
     * @param problem provided by sub class
     */
    protected final void transferFinished(Throwable problem) {
        if (! (isWaiting() || isReceiving())) {
            return;
        }

        if (chunked) {
            if (problem != null) {
                try {
                    deleteChunks();
                    deregisterHandler(() -> onCompletion.accept(Optional.of(problem)));
                }
                catch (DeleteException e) {
                    e.addSuppressed(problem);
                    deregisterHandler(() -> onCompletion.accept(Optional.of(e)));
                }
            }
            else {
                startNewChunk();
                onCompletion.accept(Optional.empty());
            }
        }
        else {
            destination = null;
            state = State.NOT_INITITATED;
            deregisterHandler(() -> onCompletion.accept(Optional.ofNullable(problem)));
        }
    }
    
    
    
    /*
     *  --------------
     * | INTERNAL API |
     *  --------------
     */
    
    private void startNewChunk() {
        // Create new random temp chunk in hopefully same folder..
        currentChunk = destination.resolveSibling(
                destination.getFileName() + ".part" + (chunks.size() + 1));
        
        chunks.add(currentChunk);
        usedCipher = null;
        state = State.WAITING;
    }
    
    private void mergeChunks() throws MergeException {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        
        /*
         * "Last chunk" is never transfered. startNewChunk() is always called
         * after each previous chunked was completed. Therefore only the chunks
         * except the last one will be processed.
         */
        
        try (FileChannel target = FileChannel.open(destination,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (int i = 0; i < chunks.size() - 1; ++i) {
                Path chunk = chunks.get(i);
                long size = Files.size(chunk);
                try (FileChannel source = FileChannel.open(chunk, StandardOpenOption.READ)) {
                    source.transferTo(0L, size, target);
                }
            }
        }
        catch (IOException e) {
            throw new MergeException(e);
        }
    }
    
    private void deleteChunks() throws DeleteException {
        if (chunks == null || chunks.isEmpty()) {
            currentChunk = destination = null;
            chunks = null;
            state = State.NOT_INITITATED;
            return;
        }
        
        /*
         * We might or might not have to delete the last chunk. Almost same
         * resoning applies here as in mergeChunks(). However, it is possible
         * for a chunked transfer to crash during the transmission of a chunk or
         * right after without startNewChunk() called (for example on
         * AEADBadTagException). Thus we delete the "last chunk" only if it
         * exists.
         */
        
        final int ceiling = Files.exists(chunks.get(chunks.size() - 1)) ?
                chunks.size() :
                chunks.size() - 1;
        
        IOException oops = null;
        
        for (int i = 0; i < ceiling; ++i) { // <-- see comment in mergeChunks()
            Path chunk = chunks.get(i);
            try {
                Files.delete(chunk); }
            catch (IOException e) {
                if (oops != null) {
                    e.addSuppressed(oops); }
                oops = e;
            }
        }
        
        destination = null;
        currentChunk = null;
        chunks.clear();
        chunks = null;
        
        state = State.NOT_INITITATED;
        
        if (oops != null) {
            throw new DeleteException(oops);
        }
    }
    
    private void registerHandler() {
        state = State.WAITING;
        handler = getMessageHandler();
        session.addMessageHandler(handler);
    }
    
    /**
     * Session.removeMessageHandler() might block until the current message
     * handler is done, hence the deregistration will happen asynchronously and
     * the {@code onCompleted} callback will be called once deregistration has
     * happened.
     * 
     * @param after optional logic to run after deregistration (may be {@code
     *              null})
     */
    private void deregisterHandler(Runnable after) {
        long id = Thread.currentThread().getId();
        
        Runnable deregister = () -> {
            if (id == Thread.currentThread().getId()) {
                // I don't trust them managed things..
                LOGGER.warning("Deregistering message handler not done in another thread, we might block forever now..");
            }

            session.removeMessageHandler(handler);

            if (id == Thread.currentThread().getId()) {
                LOGGER.warning("I survived, job done! =)");
            }
            
            if (after != null) {
                after.run();
            }
        };

        try {
            executor.submit(deregister);
        }
        catch (RejectedExecutionException e) {
            LOGGER.warning(() -> "Deregistration task rejected, submitting to a new thread instead..");
            threads.newThread(deregister).start();
        }
    }
}