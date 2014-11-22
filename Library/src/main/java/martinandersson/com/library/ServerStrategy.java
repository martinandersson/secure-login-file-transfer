package martinandersson.com.library;

/**
 * Server may use different strategies for how binary data should be received.
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public enum ServerStrategy
{
    COPY_INPUT_STREAM ("Copy InputStream",
            "Server will use a MessageHandler.Whole<InputStream> that use Files.copy() to transfer all bytes to disk."),
    
    NO_USE_INPUT_STREAM ("No-use InputStream",
            "Server will use a MessageHandler.Whole<InputStream> that throw away all bytes without using disk IO."),
    
    SINGLE_BYTE_INPUT_STREAM ("Single-byte InputStream",
            "Server will use a MessageHandler.Whole<InputStream> that save the bytes using a buffered FileOutputStream."),
    
    BYTE_ARRAY ("byte[]",
            "Server will use a MessageHandler.Partial<byte[]> that save the bytes using a buffered OutputStream."),
    
    BYTE_BUFFER ("ByteBuffer",
            "Server will use a MessageHandler.Partial<ByteBuffer> that save the bytes using a blocking FileChannel.");

    private final String readable, description;
    
    ServerStrategy(String readable, String description) {
        this.readable = readable;
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return readable;
    }
}