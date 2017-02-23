package com.afollestad.digitus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.NonNull;
import android.view.inputmethod.InputMethodManager;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;

/**
 * @author Aidan Follestad (afollestad)
 */
class DigitusBase {

    DigitusBase(@NonNull Activity context, @NonNull String keyName, @NonNull DigitusCallback callback) {
        this.context = context;
        this.keyName = keyName;
        this.callback = callback;

        inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            MUtils.initBase(context, this);
    }

    void deinitBase() {
        keyName = null;
        context = null;
        keyguardManager = null;
        fingerprintManager = null;
        keyStore = null;
        keyGenerator = null;
        cipher = null;
    }

    String keyName;
    Context context;
    KeyguardManager keyguardManager;
    FingerprintManager fingerprintManager;
    InputMethodManager inputMethodManager;
    KeyStore keyStore;
    KeyGenerator keyGenerator;
    Cipher cipher;
    DigitusCallback callback;

    public void setCallback(@NonNull DigitusCallback callback) {
        this.callback = callback;
    }

    /**
     * Initialize the {@link Cipher} instance with the created key in the {@link #recreateKey()}
     * method.
     *
     * @return {@code true} if initialization is successful, {@code false} if the lock screen has
     * been disabled or reset after the key was generated, or if a fingerprint got enrolled after
     * the key was generated.
     */
    boolean initCipher() {
        try {
            return MUtils.initCipher(this);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Creates a symmetric key in the Android Key Store which can only be used after the user has
     * authenticated with fingerprint.
     */
    @SuppressLint("NewApi")
    final void recreateKey() {
        // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
        // for your flow. Use of keys is necessary if you need to know if the set of
        // enrolled fingerprints has changed.
        try {
            keyStore.load(null);
            // Set the alias of the entry in Android KeyStore where the key will appear
            // and the constrains (purposes) in the constructor of the Builder
            keyGenerator.init(new KeyGenParameterSpec.Builder(keyName,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    // Require the user to authenticate with a fingerprint to authorize every use
                    // of the key
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
