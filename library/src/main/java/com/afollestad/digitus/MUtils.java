package com.afollestad.digitus;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v4.content.ContextCompat;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * @author Aidan Follestad (afollestad)
 */
@TargetApi(Build.VERSION_CODES.M)
class MUtils {

    private MUtils() {
    }

    public static boolean isFingerprintRegistered(Digitus digitus) {
        if (!isFingerprintAuthAvailable(digitus)) return false;
        //noinspection ResourceType
        return digitus.mKeyguardManager.isKeyguardSecure() && digitus.mFingerprintManager.hasEnrolledFingerprints();
    }

    public static boolean isFingerprintAuthAvailable(Digitus digitus) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        int granted = ContextCompat.checkSelfPermission(digitus.mContext, Manifest.permission.USE_FINGERPRINT);
        if (granted != PackageManager.PERMISSION_GRANTED) return false;
        //noinspection ResourceType
        return digitus.mFingerprintManager.isHardwareDetected();
    }

    public static void initBase(Context context, DigitusBase digitus) {
        digitus.mKeyguardManager = context.getSystemService(KeyguardManager.class);
        digitus.mFingerprintManager = context.getSystemService(FingerprintManager.class);
        try {
            digitus.mKeyStore = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to get an instance of KeyStore", e);
        }
        try {
            digitus.mKeyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException("Failed to get an instance of KeyGenerator", e);
        }
        try {
            digitus.mCipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get an instance of Cipher", e);
        }
    }

    public static boolean initCipher(DigitusBase digitus) {
        try {
            digitus.mKeyStore.load(null);
            SecretKey key = (SecretKey) digitus.mKeyStore.getKey(digitus.mKeyName, null);
            digitus.mCipher.init(Cipher.ENCRYPT_MODE, key);
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {
            return false;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }
}
