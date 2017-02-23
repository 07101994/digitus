package com.afollestad.digitus;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDTintHelper;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
@SuppressWarnings("ResourceType")
public class FingerprintDialog extends DialogFragment
        implements TextView.OnEditorActionListener, DigitusCallback {

    public interface Callback {
        void onFingerprintDialogAuthenticated();

        void onFingerprintDialogVerifyPassword(FingerprintDialog dialog, String password);

        void onFingerprintDialogStageUpdated(FingerprintDialog dialog, Stage stage);

        void onFingerprintDialogCancelled();
    }

    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1300;
    static final String TAG = "[DIGITUS_FPDIALOG]";

    private View fingerprintContent;
    private View backupContent;
    private EditText password;
    private CheckBox useFingerprintFutureCheckBox;
    private TextView passwordDescriptionTextView;
    private TextView newFingerprintEnrolledTextView;
    private ImageView fingerprintIcon;
    private TextView fingerprintStatus;

    private Stage lastStage;
    private Stage stage = Stage.FINGERPRINT;
    private Digitus digitus;
    private Callback callback;

    public FingerprintDialog() {
    }

    public static <T extends FragmentActivity & Callback> FingerprintDialog show(T context, String keyName, int requestCode) {
        return show(context, keyName, requestCode, true);
    }

    public static <T extends FragmentActivity & Callback> FingerprintDialog show(T context, String keyName, int requestCode, boolean cancelable) {
        FingerprintDialog dialog = getVisible(context);
        if (dialog != null)
            dialog.dismiss();
        dialog = new FingerprintDialog();
        Bundle args = new Bundle();
        args.putString("key_name", keyName);
        args.putInt("request_code", requestCode);
        args.putBoolean("was_initialized", Digitus.get() != null && Digitus.get().callback == context);
        args.putBoolean("cancelable", cancelable);
        dialog.setArguments(args);
        dialog.show(context.getSupportFragmentManager(), TAG);
        return dialog;
    }

    public static <T extends FragmentActivity> FingerprintDialog getVisible(T context) {
        Fragment frag = context.getSupportFragmentManager().findFragmentByTag(TAG);
        if (frag != null && frag instanceof FingerprintDialog)
            return (FingerprintDialog) frag;
        return null;
    }

    public void setTitle(@NonNull CharSequence title) {
        Dialog dialog = getDialog();
        if (dialog != null) dialog.setTitle(title);
    }

    public void setTitle(@StringRes int titleRes) {
        Dialog dialog = getDialog();
        if (dialog != null) dialog.setTitle(titleRes);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("stage", stage);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getArguments() == null || !getArguments().containsKey("key_name"))
            throw new IllegalStateException("FingerprintDialog must be shown with show(Activity, String, int).");
        else if (savedInstanceState != null)
            stage = (Stage) savedInstanceState.getSerializable("stage");
        setCancelable(getArguments().getBoolean("cancelable", true));

        MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.sign_in)
                .customView(R.layout.fingerprint_dialog_container, false)
                .positiveText(android.R.string.cancel)
                .negativeText(R.string.use_password)
                .autoDismiss(false)
                .cancelable(getArguments().getBoolean("cancelable", true))
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                        materialDialog.cancel();
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                        if (stage == Stage.FINGERPRINT) {
                            goToBackup(materialDialog);
                        } else {
                            verifyPassword();
                        }
                    }
                }).build();

        final View v = dialog.getCustomView();
        assert v != null;
        fingerprintContent = v.findViewById(R.id.fingerprint_container);
        backupContent = v.findViewById(R.id.backup_container);
        password = (EditText) v.findViewById(R.id.password);
        password.setOnEditorActionListener(this);
        passwordDescriptionTextView = (TextView) v.findViewById(R.id.password_description);
        useFingerprintFutureCheckBox = (CheckBox) v.findViewById(R.id.use_fingerprint_in_future_check);
        newFingerprintEnrolledTextView = (TextView) v.findViewById(R.id.new_fingerprint_enrolled_description);
        fingerprintIcon = (ImageView) v.findViewById(R.id.fingerprint_icon);
        fingerprintStatus = (TextView) v.findViewById(R.id.fingerprint_status);
        fingerprintStatus.setText(R.string.initializing);

        return dialog;
    }

    @Override public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateStage(null);
    }

    @Override public void onResume() {
        super.onResume();
        digitus = Digitus.init(getActivity(),
                getArguments().getString("key_name", ""),
                getArguments().getInt("request_code", -1),
                FingerprintDialog.this);
    }

    @Override public void onPause() {
        super.onPause();
        if (Digitus.get() != null) {
            Digitus.get().stopListening();
        }
    }

    @Override public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        redirectToActivity();
        if (callback != null) {
            callback.onFingerprintDialogCancelled();
        }
    }

    @Override public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        redirectToActivity();
    }

    private void redirectToActivity() {
        Digitus.deinit();
        if (getActivity() != null &&
                getActivity() instanceof DigitusCallback &&
                getArguments().getBoolean("was_initialized", false)) {
            Digitus.init(getActivity(),
                    getArguments().getString("key_name", ""),
                    getArguments().getInt("request_code", -1),
                    (DigitusCallback) getActivity());
        }
    }

    @Override public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof Callback)) {
            Digitus.deinit();
            throw new IllegalStateException("Activities showing a FingerprintDialog must implement FingerprintDialog.Callback.");
        }
        callback = (Callback) activity;
    }

    /**
     * Switches to backup (password) screen. This either can happen when fingerprint is not
     * available or the user chooses to use the password authentication method by pressing the
     * button. This can also happen when the user had too many fingerprint attempts.
     */
    private void goToBackup(MaterialDialog dialog) {
        stage = Stage.PASSWORD;
        updateStage(dialog);
        password.requestFocus();
        // Show the keyboard.
        password.postDelayed(showKeyboardRunnable, 500);
        // Fingerprint is not used anymore. Stop listening for it.
        if (digitus != null) {
            digitus.stopListening();
        }
    }

    private void toggleButtonsEnabled(boolean enabled) {
        MaterialDialog dialog = (MaterialDialog) getDialog();
        dialog.getActionButton(DialogAction.POSITIVE).setEnabled(enabled);
        dialog.getActionButton(DialogAction.NEGATIVE).setEnabled(enabled);
    }

    private void verifyPassword() {
        toggleButtonsEnabled(false);
        callback.onFingerprintDialogVerifyPassword(this, password.getText().toString());
    }

    public void notifyPasswordValidation(boolean valid) {
        final MaterialDialog dialog = (MaterialDialog) getDialog();
        final View positive = dialog.getActionButton(DialogAction.POSITIVE);
        final View negative = dialog.getActionButton(DialogAction.NEGATIVE);
        toggleButtonsEnabled(true);

        if (valid) {
            if (stage == Stage.NEW_FINGERPRINT_ENROLLED &&
                    useFingerprintFutureCheckBox.isChecked()) {
                // Re-create the key so that fingerprints including new ones are validated.
                Digitus.get().recreateKey();
                stage = Stage.FINGERPRINT;
            }
            password.setText("");
            callback.onFingerprintDialogAuthenticated();
            dismiss();
        } else {
            passwordDescriptionTextView.setText(R.string.password_not_recognized);
            final int red = ContextCompat.getColor(getActivity(), R.color.material_red_500);
            MDTintHelper.setTint(password, red);
            ((TextView) positive).setTextColor(red);
            ((TextView) negative).setTextColor(red);
        }
    }

    private final Runnable showKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (digitus != null) {
                digitus.inputMethodManager.showSoftInput(password, 0);
            }
        }
    };

    private void updateStage(@Nullable MaterialDialog dialog) {
        if (lastStage == null || (lastStage != stage && callback != null)) {
            lastStage = stage;
            callback.onFingerprintDialogStageUpdated(this, stage);
        }
        if (dialog == null)
            dialog = (MaterialDialog) getDialog();
        if (dialog == null) return;
        switch (stage) {
            case FINGERPRINT:
                dialog.setActionButton(DialogAction.POSITIVE, android.R.string.cancel);
                dialog.setActionButton(DialogAction.NEGATIVE, R.string.use_password);
                fingerprintContent.setVisibility(View.VISIBLE);
                backupContent.setVisibility(View.GONE);
                break;
            case NEW_FINGERPRINT_ENROLLED:
                // Intentional fall through
            case PASSWORD:
                dialog.setActionButton(DialogAction.POSITIVE, android.R.string.cancel);
                dialog.setActionButton(DialogAction.NEGATIVE, android.R.string.ok);
                fingerprintContent.setVisibility(View.GONE);
                backupContent.setVisibility(View.VISIBLE);
                if (stage == Stage.NEW_FINGERPRINT_ENROLLED) {
                    passwordDescriptionTextView.setVisibility(View.GONE);
                    newFingerprintEnrolledTextView.setVisibility(View.VISIBLE);
                    useFingerprintFutureCheckBox.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_GO) {
            verifyPassword();
            return true;
        }
        return false;
    }

    /**
     * Enumeration to indicate which authentication method the user is trying to authenticate with.
     */
    public enum Stage {
        FINGERPRINT,
        NEW_FINGERPRINT_ENROLLED,
        PASSWORD
    }

    private void showError(CharSequence error) {
        if (getActivity() == null) return;
        fingerprintIcon.setImageResource(R.drawable.ic_fingerprint_error);
        fingerprintStatus.setText(error);
        fingerprintStatus.setTextColor(
                ContextCompat.getColor(getActivity(), R.color.warning_color));
        fingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        fingerprintStatus.postDelayed(resetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    Runnable resetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (getActivity() == null) return;
            fingerprintStatus.setTextColor(Utils.resolveColor(
                    getActivity(), android.R.attr.textColorSecondary));
            fingerprintStatus.setText(getResources().getString(R.string.fingerprint_hint));
            fingerprintIcon.setImageResource(R.drawable.ic_fp_40px);
        }
    };

    // Digitus callbacks

    @Override public void onDigitusReady(Digitus digitus) {
        digitus.startListening();
    }

    @Override public void onDigitusListening(boolean newFingerprint) {
        fingerprintStatus.setText(R.string.fingerprint_hint);
        if (newFingerprint)
            stage = Stage.NEW_FINGERPRINT_ENROLLED;
        updateStage(null);
    }

    @Override public void onDigitusAuthenticated(Digitus digitus) {
        toggleButtonsEnabled(false);
        fingerprintStatus.removeCallbacks(resetErrorTextRunnable);
        fingerprintIcon.setImageResource(R.drawable.ic_fingerprint_success);
        fingerprintStatus.setTextColor(
                ContextCompat.getColor(getActivity(), R.color.success_color));
        fingerprintStatus.setText(getResources().getString(R.string.fingerprint_success));
        fingerprintIcon.postDelayed(new Runnable() {
            @Override
            public void run() {
                callback.onFingerprintDialogAuthenticated();
                dismiss();
            }
        }, SUCCESS_DELAY_MILLIS);
    }

    @Override public void onDigitusError(
            Digitus digitus,
            DigitusErrorType type,
            Exception e) {
        switch (type) {
            case FINGERPRINTS_UNSUPPORTED:
                goToBackup(null);
                break;
            case UNRECOVERABLE_ERROR:
            case PERMISSION_DENIED:
                showError(e.getMessage());
                fingerprintIcon.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        goToBackup(null);
                    }
                }, ERROR_TIMEOUT_MILLIS);
                break;
            case REGISTRATION_NEEDED:
                passwordDescriptionTextView.setText(R.string.no_fingerprints_registered);
                goToBackup(null);
                break;
            case HELP_ERROR:
                showError(e.getMessage());
                break;
            case FINGERPRINT_NOT_RECOGNIZED:
                showError(getResources().getString(R.string.fingerprint_not_recognized));
                break;
        }
    }
}