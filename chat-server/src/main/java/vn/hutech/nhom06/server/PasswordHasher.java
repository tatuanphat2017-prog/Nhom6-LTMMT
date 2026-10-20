package vn.hutech.nhom06.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Bam mat khau bang PBKDF2 (HMAC-SHA256) + salt ngau nhien - co san trong Java, khong can thu vien ngoai.
 *
 * Vi sao khong luu mat khau truc tiep / khong dung SHA-256 1 lan:
 *  - CSDL bi lo thi ke tan cong khong doc duoc mat khau.
 *  - Salt rieng tung nguoi: 2 nguoi cung mat khau van ra 2 chuoi khac nhau.
 *  - PBKDF2 lap lai 65.536 lan: moi lan doan mat khau ton thoi gian -> do mat khau (brute force) rat cham.
 *
 * @author Ta Tuan Phat
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65_536;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** Ket qua bam: ca hai deu la chuoi Base64 de luu vao CSDL. */
    public record Hashed(String hash, String salt) {
    }

    public static Hashed hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return new Hashed(Base64.getEncoder().encodeToString(derive(password, salt)),
                Base64.getEncoder().encodeToString(salt));
    }

    /** So sanh thoi gian khong doi (MessageDigest.isEqual) de khong lo thong tin qua thoi gian phan hoi. */
    public static boolean verify(String password, String hash, String salt) {
        try {
            byte[] expected = Base64.getDecoder().decode(hash);
            byte[] actual = derive(password, Base64.getDecoder().decode(salt));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Java khong ho tro " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }
}
