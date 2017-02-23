package com.afollestad.digitus;

import android.Manifest;
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

    private static Digitus instance;

    private int requestCode;
    private AuthenticationHandler authenticationHandler;
    private boolean isReady;

    private Digitus(
            @NonNull Activity context,
            @NonNull String keyName,
            int requestCode,
            @NonNull DigitusCallback callback) {
        super(context, keyName, callback);
        this.requestCode = requestCode;
    }

    public static Digitus get() {
        return instance;
    }

    public static Digitus init(
            @NonNull Activity context,
            @NonNull String keyName,
            int requestCode,
            @NonNull DigitusCallback callback) {
        if (instance != null) {
            deinit();
        }
        instance = new Digitus(context, keyName, requestCode, callback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int granted = ContextCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT);
            if (granted != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context,
                        new String[]{Manifest.permission.USE_FINGERPRINT}, requestCode);
            } else {
                finishInit();
            }
        } else finishInit();
        return instance;
    }

    public static void deinit() {
        if (instance == null) return;
        if (instance.authenticationHandler != null) {
            instance.authenticationHandler.stop();
            instance.authenticationHandler = null;
        }
        instance.requestCode = 0;
        instance.deinitBase();
        instance = null;
    }

    private static void finishInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!instance.isFingerprintAuthAvailable()) {
                instance.callback.onDigitusError(instance, DigitusErrorType.FINGERPRINTS_UNSUPPORTED,
                        new Exception("Fingerprint authentication is not available to this device."));
            } else if (instance.isFingerprintRegistered()) {
                instance.isReady = true;
                instance.recreateKey();
                instance.callback.onDigitusReady(instance);
            } else {
                instance.callback.onDigitusError(instance, DigitusErrorType.REGISTRATION_NEEDED,
                        new Exception("No fingerprints are registered on this device."));
            }
        } else {
            instance.isReady = true;
            instance.callback.onDigitusReady(instance);
        }
    }

    public void handleResult(int requestCode, String[] permissions, int[] state) {
        if (requestCode == this.requestCode && permissions != null &&
                permissions[0].equals(Manifest.permission.USE_FINGERPRINT)) {
            if (state[0] == PackageManager.PERMISSION_GRANTED) {
                finishInit();
            } else {
                callback.onDigitusError(this, DigitusErrorType.PERMISSION_DENIED,
                        new Exception("USE_FINGERPRINT permission is needed in " +
                                "your manifest, or was denied by the user."));
            }
        }
    }

    @SuppressWarnings("ResourceType")
    @TargetApi(Build.VERSION_CODES.M)
    public boolean startListening() {
        if (!isFingerprintAuthAvailable()) {
            // Fingerprints not supported on this device
            callback.onDigitusError(this, DigitusErrorType.FINGERPRINTS_UNSUPPORTED,
                    new Exception("Fingerprint authentication is not available to this device."));
            return false;
        } else if (authenticationHandler != null && !authenticationHandler.isReadyToStart()) {
            // Authentication handler is already listening
            return false;
        } else {
            callback.onDigitusListening(!initCipher());
            authenticationHandler = new AuthenticationHandler(this,
                    new FingerprintManager.CryptoObject(cipher));
            authenticationHandler.start();
            return true;
        }
    }

    public boolean stopListening() {
        if (authenticationHandler != null) {
            authenticationHandler.stop();
            return true;
        }
        return false;
    }

    public boolean openSecuritySettings() {
        if (context == null) return false;
        context.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        return true;
    }

    public boolean isReady() {
        return instance != null && instance.isReady;
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
