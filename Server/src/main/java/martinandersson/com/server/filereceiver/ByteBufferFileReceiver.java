package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;
import javax.crypto.Cipher;

/**
 * Uses a message part handler that process {@code ByteBuffer}.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class ByteBufferFileReceiver extends AbstractByteFileReceiver<ByteBuffer, FileChannel>
{
    private static final Logger LOGGER = Logger.getLogger(ByteBufferFileReceiver.class.getName());
    
    public ByteBufferFileReceiver() {
        super(ByteBuffer.class);
    }

    @Override
    protected FileChannel getSink(Path destination) throws IOException {
        return FileChannel.open(destination, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Override
    protected boolean hasBytes(ByteBuffer part) {
        return part.hasRemaining();
    }
    
    @Override
    protected int transferAllBytes(ByteBuffer part, FileChannel out, Cipher cipher) throws IOException {
        if (cipher != null) {
            try {
                int len = part.remaining();
                ByteBuffer decoded = ByteBuffer.allocate(cipher.getOutputSize(len));
                
                // See: http://stackoverflow.com/q/26920906/1268003
                int wrote = cipher.update(part, decoded);
                
                if (wrote == 0) {
                    return 0;
                }
                else {
                    decoded.flip();
                    part = decoded;
                }
            }
            catch (GeneralSecurityException e) {
                throw new RuntimeException(e);
            }
        }
        
        int bytesRead = out.write(part);

        if (part.hasRemaining()) { // <-- will never happen because channel is in blocking mode..
            LOGGER.warning(() -> "Retrying without sleep..");
            return bytesRead + transferAllBytes(part, out, null);
        }
        
        // Surely the WebSocket provider must clear() the buffer..
        
        return bytesRead;
    }
    
    @Override
    protected void putCipherFinal(byte[] bytes, FileChannel out) throws IOException {
        out.write(ByteBuffer.wrap(bytes));
    }
}