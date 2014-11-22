package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Read every byte and throw it away without writing anything to disk.<p>
 * 
 * Note, using this strategy will cause server to log that a file was stored,
 * and a chunked file transfer will cause a java.nio.file.NoSuchFileException to
 * be logged; both of which are false. This strategy does nothing with the
 * received bytes.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class NoUseInputFileReceiver extends AbstractInputStreamFileReceiver {
    @Override
    protected long readAllBytes(InputStream in, Path ignored) throws IOException {
        long n = 0L;
        
        while (in.read() != -1) {
            ++n;
        }
        
        return n;
    }
}