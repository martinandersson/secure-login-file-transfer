package martinandersson.com.server.filereceiver;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Write every single byte directly to a buffered file output stream.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class SingleByteInputStreamFileReceiver extends AbstractInputStreamFileReceiver {
    @Override
    protected long readAllBytes(InputStream in, Path destination) throws IOException {
        long n = 0L;
        
        try (FileOutputStream raw = new FileOutputStream(destination.toFile());
             BufferedOutputStream out = new BufferedOutputStream(raw))
        {
            int b;
            
            while ((b = in.read()) != -1) {
                out.write(b);
                ++n;
            }
        }
        
        return n;
    }
}