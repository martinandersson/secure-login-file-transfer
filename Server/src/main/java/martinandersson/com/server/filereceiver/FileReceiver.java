package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import javax.websocket.Session;
import martinandersson.com.library.AesGcmCipher;

/**
 * A file receiver receives files or file chunks that is finally merged into a
 * file destination.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public interface FileReceiver
{
    public enum State {
        NOT_INITITATED,
        WAITING,
        RECEIVING;
    }
    
    /**
     * Initiates a new file transfer.<p>
     * 
     * The provided {@code onComplete} callback is called when the file transfer
     * has fully been completed (file received or last chunk received). At the
     * time of the callback invocation, the file receiver must have also
     * completed deregistration of his message handler. If during the file
     * transfer, an exception happened, then it is provided to the callback.
     * 
     * @param session the web socket session, used for registration of the
     *                message handler
     * @param file destination
     * @param pieces {@code true} if the file is sent in pieces (chunks),
     *               otherwise {@code false}
     * @param cipher may be {@code null}, in which case no decryption will
     *              happen
     * @param onCompletion called when the file transfer has been completed
     */
    void init(Session session, Path file, boolean pieces, AesGcmCipher cipher, Consumer<Optional<? extends Throwable>> onCompletion);
    
    /**
     * Must be called after last file chunk has been received.<p>
     * 
     * @throws MergeException on failure to merge all chunks into the final file
     *         destination
     * @throws DeleteException if at least one file chunk failed to be deleted
     */
    void completeChunked() throws MergeException, DeleteException;
    
    State getState();
    
    default boolean isWaiting() {
        return getState() == State.WAITING; }
    
    default boolean isReceiving() {
        return getState() == State.RECEIVING; }
    
    public static class MergeException extends IOException {
        MergeException(IOException cause) { super(cause); } }
    
    public static class DeleteException extends IOException {
        DeleteException(IOException cause) { super(cause); } }
}