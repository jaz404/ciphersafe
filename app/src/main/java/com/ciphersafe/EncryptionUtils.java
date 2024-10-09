package com.ciphersafe;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final String KEYSTORE_ALIAS = "CipherSafeKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    // Generates or retrieves a securely stored AES key using Android's Keystore system
    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        // Check if the key exists
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            keyGenerator.init(keySpec);
            keyGenerator.generateKey();
        }

        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
    }

    // Encrypts data with AES GCM and returns the encrypted string in Base64 format
    public static String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = getSecretKey();

        // Initialize Cipher and let it generate the IV automatically
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        // Get the generated IV
        byte[] iv = cipher.getIV();

        byte[] encryptedBytes = cipher.doFinal(data.getBytes());

        // Combine IV and encrypted data
        byte[] ivAndEncryptedData = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, ivAndEncryptedData, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, ivAndEncryptedData, iv.length, encryptedBytes.length);

        return Base64.encodeToString(ivAndEncryptedData, Base64.DEFAULT);
    }

    // Decrypts the Base64 encoded encrypted string using AES GCM
    public static String decrypt(String encryptedData) throws Exception {
        byte[] ivAndEncryptedData = Base64.decode(encryptedData, Base64.DEFAULT);

        // Extract the IV (first 12 bytes)
        byte[] iv = new byte[12];
        System.arraycopy(ivAndEncryptedData, 0, iv, 0, iv.length);

        // Extract the encrypted data
        byte[] encryptedBytes = new byte[ivAndEncryptedData.length - iv.length];
        System.arraycopy(ivAndEncryptedData, iv.length, encryptedBytes, 0, encryptedBytes.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKey secretKey = getSecretKey();
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        // Initialize Cipher for decryption using the extracted IV
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }
}
