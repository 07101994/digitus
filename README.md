# Digitus

Digitus is a library designed to make Fingerprint authentication using Nexus Imprint easier for developers.
On API levels below Marshmallow, it will fall back to a password dialog.

![Art](https://raw.githubusercontent.com/afollestad/digitus/master/digitusshowcase.png)

**Note**: this library is powered by [material-dialogs](https://github.com/afollestad/material-dialogs),
depending on this library will automatically depend on Material Dialogs. 

# Sample

Check out the sample project's code! You can also [download the latest APK](https://github.com/afollestad/digitus/raw/master/sample/sample.apk), 
or [view it on Google Play](https://play.google.com/store/apps/details?id=com.afollestad.digitussample)!

# Gradle Dependency

[ ![jCenter](https://api.bintray.com/packages/drummer-aidan/maven/digitus/images/download.svg) ](https://bintray.com/drummer-aidan/maven/digitus/_latestVersion)
[![Build Status](https://travis-ci.org/afollestad/digitus.svg)](https://travis-ci.org/afollestad/digitus)
[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg?style=flat-square)](https://www.apache.org/licenses/LICENSE-2.0.html)

The Gradle dependency is available via [jCenter](https://bintray.com/drummer-aidan/maven/digitus/view).
jCenter is the default Maven repository used by Android Studio.

### Dependency

Add this to your module's `build.gradle` file:

```Gradle
dependencies {
    // .. other dependencies

    compile 'com.afollestad:digitus:0.2.12'
}
```

---

# Testing Fingerprints from an Emulator

The stock Android emulator allows you to test Nexus Imprint. If you create a Nexus 5X emulator,
you can simulate a finger pressing against the reader using this Terminal command:

```shell
adb -e emu finger touch finger-id
```

*finger-id* would be replaced with a number, e.g. 1, 2, 3, etc.


# Tutorial

1. [Initialization and De-initialization](https://github.com/afollestad/digitus#initialization-and-de-initialization)
    1. [Initialization](https://github.com/afollestad/digitus#initialization)
    2. [Permissions Result](https://github.com/afollestad/digitus#permissions-result)
    3. [De-initialization](https://github.com/afollestad/digitus#de-initialization)
2. [Callbacks](https://github.com/afollestad/digitus#callbacks)
    1. [Ready](https://github.com/afollestad/digitus#ready)
    2. [Listening](https://github.com/afollestad/digitus#listening)
    3. [Authenticated](https://github.com/afollestad/digitus#authenticated)
    4. [Error](https://github.com/afollestad/digitus#error)
3. [FingerprintDialog](https://github.com/afollestad/digitus#fingerprint-dialog)
4. [Misc](https://github.com/afollestad/digitus#misc)
    
---
    
# Initialization and De-initialization

### Initialization

Before you can do anything with Digitus, you need to initialize it:

```java
Digitus.init(this,                  // context 
    getString(R.string.app_name),   // key name 
    69,                             // permission request code
    this);                          // callback
```

The first parameter, where `this` is passed above, is just an `Activity` instance.
It's used to retrieve resources and request the `USE_FINGERPRINT` permission.

The second parameter should be a unique string used as the key name for the encryption cipher. 
Don't worry about what that means too much, it should just be unique for your app.

The third parameter is a request code which gets passed back to the `Activity` in the
 first parameter later. It should be a unique integer that you don't use for permission 
 requests elsewhere in the same Activity. It should be an 8-bit (2 in length) integer.

The fourth parameter is a callback that receives certain events that are discussed in [Callbacks](https://github.com/afollestad/digitus#callbacks).

### Permissions Result

On Marshmallow, Digitus will automatically request the `USE_FINGERPRINT` permission from the device for you.
You should also include the permission in your `AndroidManifest.xml` file (like the sample project).

Digitus will make this request when you make a call to `init(Activity, String, int, DigitusCallback)`. 
You need to receive the result by overriding the `onRequestPermissionsResult(int, String[], int[])` 
method in your `Activity`:

```java
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    // Notify Digitus of the result
    Digitus.get().handleResult(requestCode, permissions, grantResults);
}
```

The above implementation of `onRequestPermissionsResult(int, String[], int[])` does all that you need 
to do here. It notifies Digitus if the user allowed the app to access their fingerprint sensor. If not,
Digitus will send `PERMISSION_DENIED` to `onDigitusError()` in your callback.

### De-initialization

De-initialization allows Digitus to help Java recycle resources faster, and avoid memory leaks on Android.
It also makes a call to `stopListening()` (discussed below) if necessary, which avoids locking the fingerprint
sensor from other apps and your lockscreen.

```java
Digitus.deinit();
```

You'll want to call this method when you're done with Digitus. It's good to put it in `onDestroy()`
of your `Activity`, or `onPause()` if you remember to initialize Digitus again in `onResume()`.

It's recommend that you try to make a call to this method even in the case of a
crash in your app (e.g. using try/catch/finally).

---

# Callbacks

### Ready

The `onDigitusReady(Digitus)` callback method is invoked when Digitus has finished the 
initialization process. At this point, you are able to start listening for fingerprints 
at any time.

```java
@Override
public void onDigitusReady(Digitus digitus) {
    // Digitus is ready for authentication.
    // Here, you could enable UI that starts it, hide progress indicators/dialogs, etc.
    // Otherwise, you could immediately start listening for fingerprints:
    
    digitus.startListening();
}
```

If you weren't using `startListening()` from within a Digitus callback method (e.g. if it was 
in  a button click event), you can do it like this:

```java
Digitus.get().startListening();
```

If you wanted to stop listening, you can make a call to `stopListening()`. `Digitus.deinit()`
will automatically do this for you.

### Listening

The `onDigitusListening(boolean)` callback method is invoked when Digitus has started listening
for fingerprints. It's generally a good time to update any UI that indicates the user should press
their finger to the sensor.

The `newFingerprint` boolean parameter is true if the lockscreen has been disabled
  or reset after the key was generated, or if a fingerprint got enrolled after the key was generated.
  Generally you *should* fallback to using a password in this case, and let them use a fingerprint next time
  (e.g. with a checkbox).

```java
@Override
public void onDigitusListening(boolean newFingerprint) {
    // TODO update UI to indicate the user can imprint their finger
}
```

### Authenticated

The `onDigitusAuthenticated(Digitus)` callback method is pretty straight forward. It's called when the
user's fingerprint was successfully recognized. After this point, Digitus automatically stops
listening for fingerprints.

```java
@Override
public void onDigitusAuthenticated(Digitus digitus) {
    // TODO authentication was successful
}
```

### Error

The error callback is very important, it provides a lot of events that should be displayed
in the UI. They're all covered in the switch statement below. The `Exception` parameter
will always contain a human readable message, also.

```java
@Override
public void onDigitusError(Digitus digitus, DigitusErrorType type, Exception e) {
 switch (type) {
     case FINGERPRINT_NOT_RECOGNIZED:
         // Fingerprint wasn't recognized, try again
         break;
     case FINGERPRINTS_UNSUPPORTED:
         // Fingerprints are not supported by the device (e.g. no sensor, or no API support).
         // You should fallback to password authentication.
         break;
     case HELP_ERROR:
         // A help message for the user, e.g. "Clean the sensor", "Swiped too fast, try again", etc.
         // e.getMessage() should be displayed in UI so the user knows to try again.
         break;
     case PERMISSION_DENIED:
         // The USE_FINGERPRINT permission was denied by the user or device.
         // You should fallback to password authentication.
         break;
     case REGISTRATION_NEEDED:
         // There are no fingerprints registered on the device.
         // You can open the Security Settings system screen using the code below...
         // ...but probably with a button click instead of doing it automatically.
         digitus.openSecuritySettings();
         break;
     case UNRECOVERABLE_ERROR:
         // An recoverable error occurred, no further callbacks are sent until you start listening again. 
         break;   
     }
}
```

---

# FingerprintDialog

The `FingerprintDialog` is a pre-built authentication dialog based off of the Design Guidelines on
fingerprints. It automatically handles the error cases above by displaying the errors to the
user and falling back to password authentication if necessary.

You show the dialog like this:

```java
// Last boolean parameter is optional
FingerprintDialog.show(this, getString(R.string.app_name), 69, true);
```

You can change the title of dialogs, if you wish:

```java
FingerprintDialog dialog = FingerprintDialog.show(this, getString(R.string.app_name), 69, true);
dialog.setTitle("Hello!"); // or R.string.some_string
```

---

It's pretty similar to `Digitus.init()`. The first parameter must be a `FragmentActivity` instance
(or `AppCompatActivity`), which implements the `FingerprintDialog.Callback` interface:

```java
public class MainActivity extends AppCompatDialog implements FingerprintDialog.Callback {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Show a FingerprintDialog
        FingerprintDialog.show(this,        // FragmentActivity, or AppCompatActivity 
            getString(R.string.app_name),   // Unique key name
            69);                            // Permission request code
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);    
        // Notify Digitus of the result
        Digitus.get().handleResult(requestCode, permissions, grantResults);
    }
    
    @Override
    public void onFingerprintDialogAuthenticated() {
        Toast.makeText(this, "Authenticated successfully", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFingerprintDialogVerifyPassword(FingerprintDialog dialog, final String password) {
        // Simulate server contact
        mButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                dialog.notifyPasswordValidation(password.equals("password"));
            }
        }, 1500);
    }
    
    @Override
    public void onFingerprintDialogStageUpdated(FingerprintDialog dialog, FingerprintDialog.Stage stage) {
        Log.d("Digitus", "Dialog stage: " + stage.name());
    }

    @Override
    public void onFingerprintDialogCancelled() {
        Toast.makeText(this, "Authentication dialog cancelled", Toast.LENGTH_SHORT).show();
    }
}
```

`onFingerprintDialogAuthenticated()` is pretty straight forward. `onFingerprintDialogVerifyPassword()`
is called when the user inputs a password and presses 'OK'. You need to validate the password
and notify the dialog whether or not it's correct. The code above simulates a delay as if the
password was validated with a remote server.

If you need to get an instance of an open `FingerprintDialog` from somewhere in your `Activity`,
you can use this:

```java
// Will be null if there's none
FingerprintDialog dialog = FingerprintDialog.getVisible(this);
```

---

# Misc

There are other utility methods you can use to aid in making things easier. Note that these methods
can't be invoked until Digitus is initialized with the static `init()` method; otherwise `get()`
will return null.

```java
Digitus digitus = Digitus.get();

// Whether or not device has API and hardware support
boolean fingerprintAuthAvailable = digitus.isFingerprintAuthAvailable();

// Whether or not device has fingerprints enrolled
boolean fingerprintRegistered = digitus.isFingerprintRegistered();

// Used in a section above, opens the device Security Settings where fingerprints can be enrolled
digitus.openSecuritySettings();
```
