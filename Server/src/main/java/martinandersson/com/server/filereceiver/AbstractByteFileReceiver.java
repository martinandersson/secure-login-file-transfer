package martinandersson.com.server.filereceiver;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.websocket.MessageHandler;

/**
 * Base class for all byte-based file receivers that receive message parts.
 * 
 * @param <T> type of binary message part
 * @param <S> type of destination sink
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public abstract class AbstractByteFileReceiver<T, S extends Closeable> extends AbstractFileReceiver<MessageHandler.Partial<T>>
{
    private final Logger LOGGER;
    
    private final Class<T> type;
    
    /**
     * Initializes a byte file receiver of the provided type.
     * 
     * The type argument is only needed because message handler can not be
     * generic. See a source code comment in method getMessageHandler().
     * 
     * @param type message part type
     */
    public AbstractByteFileReceiver(Class<T> type) {
        this.type = type;
        LOGGER = Logger.getLogger(getClass().getName());
    }
    
    @Override
    protected final MessageHandler.Partial<T> getMessageHandler() {
        MessageHandler.Partial<T> delegate = new MessageHandlerImpl();
        
        /*
         * ..and the now named "delegate" should be all we need to return to
         * AbstractFileReceiver who then register the message handler with the
         * websocket session. But if so, both WildFly and GlassFish crash.
         * 
         * GlassFish see only the erased Object type of the message handler and
         * throw:
         * 
         *     java.lang.IllegalStateException: Partial MessageHandler can't be of type: java.lang.Object
         * 
         * WildFly try to cast the type parameter and throw:
         * 
         *     java.lang.ClassCastException: sun.reflect.generics.reflectiveObjects.TypeVariableImpl cannot be cast to java.lang.Class
         * 
         * Ultimately, this is a problem with generic message handlers and to
         * alleviate the problem a bit, the API has been proposed to expand in
         * version 1.1:
         * 
         *     https://jcp.org/aboutJava/communityprocess/maintenance/jsr356/websocket-1.1-changes.txt
         * 
         * My solution here is a wrapper class.
         */
        
        if (type == byte[].class) {
            return (MessageHandler.Partial<T>) new MessageHandler.Partial<byte[]>(){
                @Override public void onMessage(byte[] partialMessage, boolean last) {
                    delegate.onMessage((T) partialMessage, last);
                }
            };
        }
        else if (type == ByteBuffer.class) {
            return (MessageHandler.Partial<T>) new MessageHandler.Partial<ByteBuffer>(){
                @Override public void onMessage(ByteBuffer partialMessage, boolean last) {
                    delegate.onMessage((T) partialMessage, last);
                }
            };
        }
        else {
            throw new UnsupportedOperationException("Unknown binary message type: " + type);
        }
    }
    
    protected abstract S getSink(Path destination) throws IOException;
    
    /**
     * Note that at least GlassFish (don't know about WildFly because WildFly
     * cannot accept message part handlers programmatically) will provide the
     * "last part" together with an empty buffer source (length of byte[] == 0
     * and ByteBuffer.remaining() == 0).<p>
     * 
     * A call to {@code transferAllBytes()} will only follow a call to this
     * method if this method return {@code true}.
     * 
     * @param part the message part to read from
     * @return {@code false} if there's no more bytes to read, otherwise {@code true}
     */
    protected abstract boolean hasBytes(T part);
    
    protected abstract int transferAllBytes(T part, S out, Cipher cipher) throws IOException;
    
    protected abstract void putCipherFinal(byte[] bytes, S out) throws IOException;
    
    private class MessageHandlerImpl implements MessageHandler.Partial<T> {
        S out;
        long bytesRead = 0L;
        
        @Override
        public void onMessage(T buff, boolean lastPart) {
            try {
                if (isWaiting()) {
                    startTransfer();
                }
                
                doMessage(buff, lastPart, getCipher());
                
                if (lastPart) {
                    done(null);
                }
            }
            catch (IOException | GeneralSecurityException e) {
                // TODO: Should remove file?
                if (e instanceof IOException && e.getCause() != null) {
                    done(e.getCause());
                }
                else {
                    done(e);
                }
                
                // Ask container kindly to please close the stream or something:
                throw new RuntimeException(e);
            }
            catch (Throwable t) {
                done(t);
                throw t;
            }
        }
        
        private void doMessage(T buff, boolean lastPart, Cipher cipher) throws IOException, IllegalBlockSizeException, BadPaddingException {
            if (hasBytes(buff)) {
                bytesRead += transferAllBytes(buff, out, cipher); // <-- IOException
            }
            
            if (lastPart) {
                if (cipher != null) {
                    byte[] res = cipher.doFinal(); // <-- IllegalBlockSizeException, BadPaddingException
                    putCipherFinal(res, out);
                    bytesRead += res.length;
                }
                
                LOGGER.info(() -> "Successfully stored " + bytesRead + " bytes in " + getFile());
            }
        }
        
        private void startTransfer() throws IOException {
            AbstractByteFileReceiver.super.startTransfer();
            out = getSink(getFile()); // <-- IOException
        }
        
        private void done(Throwable problem) {
            transferFinished(problem);

            bytesRead = 0L;

            if (out != null) {
                try {
                    out.close();
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to close sink.", e);
                }
                finally {
                    out = null;
                }
            }
        }
    }
}