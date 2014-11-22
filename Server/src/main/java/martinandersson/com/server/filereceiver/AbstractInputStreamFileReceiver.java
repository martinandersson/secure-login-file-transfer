package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.CipherInputStream;
import javax.websocket.MessageHandler;

/**
 * Base class for all file receivers reading whole messages from an input
 * stream.<p>
 * 
 * Manages decryption by decorating the raw input stream with a {@code
 * CipherInputStream} which is then provided to subclass (if decryption was
 * enabled).<p>
 * 
 * The specification say that provider must buffer the message before feeding
 * the {@code MessageHandler.Whole<InputStream>}, but in reality, that isn't the
 * case. Both GlassFish and WildFly feed the message handler with an input
 * stream as soon as bytes has arrived.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public abstract class AbstractInputStreamFileReceiver extends AbstractFileReceiver<MessageHandler.Whole<InputStream>>
{
    /**
     * Read all bytes from the provided input stream and then return how many
     * bytes was read.
     * 
     * @param in input stream provided by WebSocket provider
     * @param destination where to put the bytes
     * 
     * @return bytes read
     * 
     * @throws java.io.IOException if anything goes to hell
     */
    protected abstract long readAllBytes(InputStream in, Path destination) throws IOException;

    @Override
    protected final MessageHandler.Whole<InputStream> getMessageHandler() {
        return new MessageHandlerImpl();
    }
    
    private class MessageHandlerImpl implements MessageHandler.Whole<InputStream> {
        final Class<? extends AbstractInputStreamFileReceiver> type
                = AbstractInputStreamFileReceiver.this.getClass();
        
        final Logger logger = Logger.getLogger(type.getName());
        
        @Override
        public void onMessage(InputStream in) {
            startTransfer();
            
            try {
                if (getCipher() != null) {
                    in = new CipherInputStream(in, getCipher());
                }
            }
            catch (GeneralSecurityException e) {
                transferFinished(e);
            }
            catch (IllegalStateException e) {
                closeSilently(in);
                throw e;
            }
            
            Throwable problem = null;
            
            try {
                long len = readAllBytes(in, getFile());
                logger.info(() -> "Successfully stored " + len + " bytes in " + getFile());
            }
            catch (IOException e) {
                logger.log(Level.WARNING, "Failed to receive file " + getFile() + ":", e);
                problem = e.getCause() != null ? e.getCause() : e;
            }
            finally {
                closeSilently(in);
                transferFinished(problem);
            }
        }
        
        void closeSilently(InputStream in) {
            try {
                in.close();
            }
            catch (IOException e) {
                logger.log(Level.WARNING, "Failed to close InputStream.", e);
            }
        }
    }
}