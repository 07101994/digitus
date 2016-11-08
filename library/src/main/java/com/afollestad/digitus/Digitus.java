package com.afollestad.digitus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * @author Aidan Follestad (afollestad)
 */
@SuppressWarnings("WeakerAccess")
public class Digitus extends DigitusBase {

    @SuppressLint("StaticFieldLeak")
    private static Digitus mInstance;

    private int mRequestCode;
    private AuthenticationHandler mAuthenticationHandler;
    private boolean mIsReady;

    private Digitus(@NonNull Activity context, @NonNull String keyName, int requestCode, @NonNull DigitusCallback callback) {
        super(context, keyName, callback);
        mRequestCode = requestCode;
    }

    public static Digitus get() {
        return mInstance;
    }

    public static Digitus init(@NonNull Activity context, @NonNull String keyName, int requestCode, @NonNull DigitusCallback callback) {
        if (mInstance != null)
            deinit();
        mInstance = new Digitus(context, keyName, requestCode, callback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int granted = ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT);
            if (granted != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.USE_FINGERPRINT}, requestCode);
            else finishInit();
        } else finishInit();
        return mInstance;
    }

    public static void deinit() {
        if (mInstance == null) return;
        if (mInstance.mAuthenticationHandler != null) {
            mInstance.mAuthenticationHandler.stop();
            mInstance.mAuthenticationHandler = null;
        }
        mInstance.mRequestCode = 0;
        mInstance.deinitBase();
        mInstance = null;
    }

    private static void finishInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!mInstance.isFingerprintAuthAvailable()) {
                mInstance.mCallback.onDigitusError(mInstance, DigitusErrorType.FINGERPRINTS_UNSUPPORTED,
                        new Exception("Fingerprint authentication is not available to this device."));
            } else if (mInstance.isFingerprintRegistered()) {
                mInstance.mIsReady = true;
                mInstance.recreateKey();
                mInstance.mCallback.onDigitusReady(mInstance);
            } else {
                mInstance.mCallback.onDigitusError(mInstance, DigitusErrorType.REGISTRATION_NEEDED,
                        new Exception("No fingerprints are registered on this device."));
            }
        } else {
            mInstance.mIsReady = true;
            mInstance.mCallback.onDigitusReady(mInstance);
        }
    }

    public void handleResult(int requestCode, String[] permissions, int[] state) {
        if (requestCode == mRequestCode && permissions != null &&
                permissions[0].equals(Manifest.permission.USE_FINGERPRINT)) {
            if (state[0] == PackageManager.PERMISSION_GRANTED) {
                finishInit();
            } else {
                mCallback.onDigitusError(this, DigitusErrorType.PERMISSION_DENIED, new Exception("USE_FINGERPRINT permission is needed in your manifest, or was denied by the user."));
            }
        }
    }

    @SuppressWarnings("ResourceType")
    @TargetApi(Build.VERSION_CODES.M)
    public boolean startListening() {
        if (!isFingerprintAuthAvailable()) {
            // Fingerprints not supported on this device
            mCallback.onDigitusError(this, DigitusErrorType.FINGERPRINTS_UNSUPPORTED, new Exception("Fingerprint authentication is not available to this device."));
            return false;
        } else if (mAuthenticationHandler != null && !mAuthenticationHandler.isReadyToStart()) {
            // Authentication handler is already listening
            return false;
        } else {
            mCallback.onDigitusListening(!initCipher());
            mAuthenticationHandler = new AuthenticationHandler(this, new FingerprintManager.CryptoObject(mCipher));
            mAuthenticationHandler.start();
            return true;
        }
    }

    public boolean stopListening() {
        if (mAuthenticationHandler != null) {
            mAuthenticationHandler.stop();
            return true;
        }
        return false;
    }

    public boolean openSecuritySettings() {
        if (mContext == null) return false;
        mContext.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        return true;
    }

    public boolean isReady() {
        return mInstance != null && mInstance.mIsReady;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean isFingerprintRegistered() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                MUtils.isFingerprintRegistered(this);
    }

    public boolean isFingerprintAuthAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                MUtils.isFingerprintAuthAvailable(this);
    }
}
