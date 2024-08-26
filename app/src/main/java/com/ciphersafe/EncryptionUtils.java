// EncryptionUtils.java
package com.ciphersafe;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    private static SecretKey generateKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(256); // Generate a 256-bit key
        return keyGenerator.generateKey();
    }

    private static SecretKey getKey() {
        // Use a fixed key for simplicity. In production, store the key securely.
        byte[] keyBytes = new byte[32];
        System.arraycopy("my_super_secret_key".getBytes(), 0, keyBytes, 0, Math.min("my_super_secret_key".length(), 32));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public static String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encryptedBytes = cipher.doFinal(data.getBytes());
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    public static String decrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey());
        byte[] decodedBytes = Base64.decode(data, Base64.DEFAULT);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes);
    }
}
