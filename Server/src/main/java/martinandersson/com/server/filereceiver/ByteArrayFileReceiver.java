package martinandersson.com.server.filereceiver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.crypto.Cipher;

/**
 * Uses a message part handler that process {@code byte[]}.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class ByteArrayFileReceiver extends AbstractByteFileReceiver<byte[], OutputStream>
{   
    public ByteArrayFileReceiver() {
        super(byte[].class);
    }

    @Override
    protected OutputStream getSink(Path destination) throws IOException {
        OutputStream raw = Files.newOutputStream(destination,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        
        return new BufferedOutputStream(raw);
    }

    @Override
    protected boolean hasBytes(byte[] part) {
        return part.length > 0;
    }
    
    @Override
    protected int transferAllBytes(byte[] part, OutputStream out, Cipher cipher) throws IOException {
        if (cipher != null) {
            // See: http://stackoverflow.com/q/26920906/1268003
            part = cipher.update(part);
        }
        
        if (part.length > 0) {
            out.write(part);
        }
        
        return part.length;
    }

    @Override
    protected void putCipherFinal(byte[] bytes, OutputStream out) throws IOException {
        out.write(bytes);
    }
}