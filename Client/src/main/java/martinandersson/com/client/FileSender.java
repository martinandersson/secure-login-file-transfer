package martinandersson.com.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.spec.InvalidParameterSpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.concurrent.Task;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.xml.bind.DatatypeConverter;
import martinandersson.com.library.AesGcmCipher;
import martinandersson.com.library.ServerStrategy;

/**
 * A JavaFX task that send a file to the server.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public class FileSender extends Task<Long>
{
    private static final Logger LOGGER = Logger.getLogger(FileSender.class.getName());
    
    /**
     * Is preferred buffer size, might be overridden by a smaller chunk size.
     */
    private static final int BUFFER_SIZE = 8192;
    
    
    
    private final Path file;
    
    private long chunkSize;
    
    private final ServerStrategy strategy;
    
    private Optional<Boolean> tell       = Optional.empty(),
                              manipulate = Optional.empty();
    
    private final ServerConnection conn;
    
    private AesGcmCipher cipher;
    
    private long sent;
    
    private Duration taskDuration;
    
    private final List<Duration> chunkDurations        = new ArrayList<>(),
                                 confirmationDurations = new ArrayList<>();
    
    /**
     * Problem "description" returned by server if he failed to receive the file/chunk.
     */
    private String problem;
    
    
    
    /**
     * Initializes a newly constructed {@code FileSender} with the specified
     * file to send and a server receiving strategy.
     * 
     * @param file file to send
     * @param strategy the strategy the server should use to receive the file
     */
    public FileSender(Path file, ServerStrategy strategy) {
        this.file = file;
        chunkSize = Long.MAX_VALUE;
        this.strategy = strategy;
        this.conn = ServerConnection.getInstance();
    }
    
    public void useCipher(AesGcmCipher cipher) {
        this.cipher = cipher;
    }
    
    public void useChunkSize(long chunkSize) {
        if (chunkSize < 1L) {
            throw new IllegalArgumentException("Gzuz we must have something to send!");
        }
        
        this.chunkSize = chunkSize;
    }
    
    public void tellServerAboutEncryption(boolean tell) {
        if (getState().compareTo(State.RUNNING) >= 0) {
            throw new IllegalStateException("Too late.");
        }
        
        this.tell = Optional.of(tell);
    }
    
    public void manipulateBitInMiddle(boolean manipulate) {
        if (getState().compareTo(State.RUNNING) >= 0) {
            throw new IllegalStateException("Too late.");
        }
        
        this.manipulate = Optional.of(manipulate);
    }
    
    public String getProblem() {
        requireSucceeded();
        return problem;
    }
    
    public long getBytesSent() {
        return sent;
    }
    
    public List<Duration> getTransferDurations() {
        requireSucceeded();
        return Collections.unmodifiableList(chunkDurations);
    }
    
    public Duration getTransferDurationTotal() {
        return chunkDurations.stream()
                .collect(Collectors.reducing(Duration.ZERO, Duration::plus));
    }
    
    public List<Duration> getConfirmationDurations() {
        requireSucceeded();
        return Collections.unmodifiableList(confirmationDurations);
    }
    
    public Duration getConfirmationDurationTotal() {
        return confirmationDurations.stream()
                .collect(Collectors.reducing(Duration.ZERO, Duration::plus));
    }
    
    public Duration getTaskDuration() {
        requireSucceeded();
        return taskDuration;
    }
    
    
    
    @Override
    protected Long call() throws Exception {
        final long TOT = Files.size(file);
        
        updateMessage("Sending file transmission request to server..\n");
        updateProgress(-1L, -1L);
        
        __sendFileTransferRequest(TOT);
        
        updateMessage(reportBytesLeft(TOT));
        updateProgress(0L, TOT);
        
        Instant taskStart = Instant.now();
        
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            while (__transferChunk(in, TOT) & !__waitForConfirmation()) {
                ; // All work done in transferChunk() and waitForConfirmation()
            }
        }
        
        taskDuration = Duration.between(taskStart, Instant.now());
        
        /*
         * If chunkSize < TOT, then wewe sent the file in pieces, so if
         * problem.isEmpty() (all chunks succeeded without any problems), then
         * we need to tell the server we're not gonna send him anything more.
         * 
         * If there was a problem, then the server already know and assume that
         * no more chunks will be sent.
         */
        if (chunkSize < TOT && problem.isEmpty()) {
            conn.sendAsync(Json.createObjectBuilder().add("eof", true).build());
        }
        
        return sent;
    }
    
    private void __sendFileTransferRequest(long fileSize) throws InterruptedException {
        // What happens if we don't tell server? Apparently, the file goes away over the wire?
        JsonObjectBuilder b = Json.createObjectBuilder();
        
        b.add("file", file.getFileName().toString())
         .add("chunked", fileSize > chunkSize)
         .add("encrypted", tell.orElse(cipher != null))
         .add("strategy", strategy.name());
        
        conn.sendBlock(b.build());
        
        updateMessage("Waiting for server accept..\n");
        
        if (!conn.receiveNext().getBoolean("accept")) {
            throw new RuntimeException("Server did not accept the request to send a file.");
        }
    }
    
    /**
     * Send a chunk to the server.<p>
     * 
     * The chunk might be the entire file of course.
     * 
     * @param in channel to read from
     * @param fileSize total file size
     * 
     * @return {@code true} if there are more bytes to read from the file,
     *         otherwise {@code false}
     * 
     * @throws GeneralSecurityException if cipher errors
     * @throws IOException if IO errors
     */
    private boolean __transferChunk(FileChannel in, long fileSize) throws GeneralSecurityException, IOException {
        final long MID = fileSize / 2;
        final int BUFFER = (int) Math.min(BUFFER_SIZE, chunkSize);
        
        ByteBuffer raw = ByteBuffer.allocate(BUFFER);
        
        Cipher c = null;
        ByteBuffer encoded = null;
        
        if (cipher != null) {
            c = cipher.initForEncryption(); // <-- GeneralSecurityException
            encoded = ByteBuffer.allocate(BUFFER + getAuthenticationTagLength(c)); // <-- GeneralSecurityException
        }
        
        boolean manipulate = this.manipulate.orElse(false);
        
        Instant chunkStart = Instant.now();
        
        try (WritableByteChannel out = Channels.newChannel(conn.getOutputStream())) { // <-- IOException
            long bytesLeft = chunkSize;
            int r = 0;
            
            while (((bytesLeft -= r) > 0) && ((r = in.read(raw)) != -1)) {
                raw.flip();
                final int w;
                
                if (encoded != null) { // .. do some crypto:
                    int need = c.getOutputSize(raw.remaining());

                    if (need > encoded.capacity()) {
                        encoded = ByteBuffer.allocate(need);
                    }
                    else {
                        encoded.clear();
                    }

                    if (c.update(raw, encoded) > 0) {
                        if (manipulate && fileSize > BUFFER && sent >= MID) {
                            encoded.put(0, flipRightmostBit(encoded.get(0)));

                            // Don't repeat the attack:
                            manipulate = false;
                        }

                        encoded.flip();
                        w = out.write(encoded); // <-- IOException
                    }
                    else {
                        w = 0;
                    }
                }
                else { // .. just write plaintext as-is:
                    w = out.write(raw); // <-- IOException
                }

                sent += w;
                updateMessage(reportBytesLeft(fileSize));
                updateProgress(sent, fileSize);

                raw.clear();
            }

            if (cipher != null) {
                final byte[] residue = c.doFinal(); // <-- GeneralSecurityException
                final int tLen = getAuthenticationTagLength(c); // <-- GeneralSecurityException

                if (residue.length > tLen) {

                    if (manipulate && fileSize < BUFFER_SIZE) { // .. chunkStart attack hasn't been made yet:
                        int i = (residue.length - tLen) / 2;
                        residue[i] = flipRightmostBit(residue[i]);
                    }

                    LOGGER.info(() -> "Sending cipher residue and appending authentication tag: " +
                            DatatypeConverter.printHexBinary(Arrays.copyOfRange(residue, residue.length - tLen, residue.length)));
                }
                else {
                    LOGGER.info(() -> "Encoded bytes sent, now appending authentication tag: " +
                            DatatypeConverter.printHexBinary(residue));
                }

                sent += out.write(ByteBuffer.wrap(residue)); // <-- IOException
            }
            
            chunkDurations.add(Duration.between(chunkStart, Instant.now()));
            return r != -1;
        }
    }
    
    /**
     * Wait for server to confirm a chunk (or file) received.
     * 
     * @return {@code true} if server reported a problem, otherwise {@code false}
     */
    private boolean __waitForConfirmation() throws InterruptedException {
        final Instant confStart = Instant.now();
        
        updateMessage("Waiting for server confirmation..\n ");
        updateProgress(-1L, -1L);
        
        problem = conn.receiveNext().getString("problem");
        
        confirmationDurations.add(Duration.between(confStart, Instant.now()));
        return !problem.isEmpty();
    }
    
    private void requireSucceeded() {
        if (getState() != State.SUCCEEDED) {
            throw new IllegalStateException("Task has not begun yet or task have not been completed successfully.");
        }
    }
    
    private int getAuthenticationTagLength(Cipher cipher) throws InvalidParameterSpecException {
        return cipher.getParameters()
                    .getParameterSpec(GCMParameterSpec.class)
                    .getTLen() / Byte.SIZE;
    }
    
    private String reportBytesLeft(long totalBytes) {
        return new StringBuilder("Sending \"").append(file).append('"').append("\n")
                .append(totalBytes - sent).append(" bytes left..\n")
                .toString();
    }
    
    private byte flipRightmostBit(byte b) {
        byte flipped = (byte) (b ^ 1); // <-- flip least significant bit (rightmost)
        LOGGER.info(() -> "Manipulated byte " + toBinaryString(b) + " to " + toBinaryString(flipped));
        return flipped;
    }
    
    private String toBinaryString(byte b) {
        return Integer.toBinaryString((b & 0xFF) + 0x100).substring(1);
    }
}