package martinandersson.com.library;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A provider of a ready to use cipher, depending on operation mode.<p>
 * 
 * The IV is constructed using guidelines found in this document:
 * <pre>{@code

 *     http://csrc.nist.gov/publications/nistpubs/800-38D/SP-800-38D.pdf
 * 
 * }</pre>
 * 
 * Section "5.2.1.2 Input Data" says:
 * <pre>{@code
 * 
 *     "For IVs, it is recommended that implementations restrict support to the
 *      length of 96 bits, to promote interoperability, efficiency, and
 *      simplicity of design."
 * 
 * }</pre>
 * 
 * Section "8.2.1 Deterministic Construction" says:
 * 
 * <pre>{@code
 * 
 *     "[..] the entire fixed field may consist of arbitrary bits when there is
 *      only one context to identify, such as when a fresh key is limited to a
 *      single session of a communications protocol."
 * 
 *     "The invocation field typically is either 1) an integer counter [..]."
 * 
 *     "The lengths and positions of the fixed field and the invocation field
 *      shall be fixed for each supported IV length for the life of the key."
 * 
 * }</pre>
 * 
 * @author Martin Andersson (webmaster at martinandersson.com)
 */
public final class AesGcmCipher
{
    private final SecretKey key;
    
    private final byte[] ivFixed;
    private long ivInvocation;
    
    private final Cipher cipher;
    
    public AesGcmCipher(BigInteger sessionKey) throws NoSuchAlgorithmException, NoSuchPaddingException {
        // Produce 256-bit digest
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(sessionKey.toByteArray());
        
        /**
         * If Nimbus is provided SHA-512, then key length will mostly be 512 if
         * salt is negative, or sometimes 520. If salt is positive, it will
         * always be 520.
         * 
         * If Nimbus is provided SHA-256, then the same. Bit size is dependent
         * on "prime number bit size" supplied to SRP6 param generator?
         * 
         * Yes it is based on key size supplied to SRP6. If key size 256 (was
         * 512), then the key size we get to this method is 256, but can be 264
         * for most negative keys.
         */
        
        // Split 128 bits into key, and 128 bits into IV base
        
        byte[] keyBytes = Arrays.copyOf(bytes, bytes.length / 2);
        key = new SecretKeySpec(keyBytes, "AES");
        
        byte[] ivBytes = Arrays.copyOfRange(bytes, bytes.length / 2, bytes.length);
        
        // Furthermore, split IV base into 64 bits fixed, and 64 bits into a long counter
        ivFixed = Arrays.copyOf(ivBytes, ivBytes.length / 2);
        
        byte[] counterBytes = Arrays.copyOfRange(ivBytes, ivBytes.length / 2, ivBytes.length);
        ByteBuffer counterBuff = ByteBuffer.wrap(counterBytes);
        ivInvocation = counterBuff.getLong();
        
        cipher = Cipher.getInstance("AES_128/GCM/NoPadding"); // <-- NoSuchAlgorithmException, NoSuchPaddingException
    }
    
    public Cipher initForEncryption() throws InvalidKeyException, InvalidAlgorithmParameterException {
        /*
         * Had we provided a "new IvParameterSpec(IV)" instead of
         * GCMParameterSpec, then the next statement would crash with:
         *     java.security.InvalidAlgorithmParameterException: Unsupported parameter: javax.crypto.spec.IvParameterSpec@208061ba
         * 
         * If we try to reuse IV, we get on the second initiation:
         *     java.security.InvalidAlgorithmParameterException: Cannot reuse iv for GCM encryption
         * 
         * If you google this exception, you'll see that the implementation only
         * check last IV used. It doesn't store all IV:s used. So whether or not
         * a unique IV is really required for each operation, SunJCE try to
         * enforce that constraint and I dare not write a nighthack built on
         * implementation specific details.
         */
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(96, getNextIV())); // InvalidKeyException, InvalidAlgorithmParameterException
        return cipher;
    }
    
    public Cipher initForDecryption() throws InvalidKeyException, InvalidAlgorithmParameterException {
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(96, getNextIV())); // <-- InvalidKeyException, InvalidAlgorithmParameterException
        return cipher;
    }
    
    private byte[] getNextIV() {
        ByteBuffer iv = ByteBuffer.allocate(16);
        
        /*
         * Do I feel honored or what. A real professional just said the next
         * piece of code is "quite safe".
         * 
         * Source: http://stackoverflow.com/questions/27361148#comment-43184307
         */
        
        iv.put(ivFixed);
        iv.put(ByteBuffer.allocate(8).putLong(++ivInvocation).array());
        
        return iv.array();
    }
    
    
    
    @Override
    public String toString() {
        return cipher.getAlgorithm();
    }
}