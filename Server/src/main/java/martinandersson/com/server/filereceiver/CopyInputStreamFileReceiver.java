package martinandersson.com.server.filereceiver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Use {@code Files.copy()} to transfer all bytes.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class CopyInputStreamFileReceiver extends AbstractInputStreamFileReceiver {
    @Override
    protected long readAllBytes(InputStream in, Path destination) throws IOException {
        return Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}