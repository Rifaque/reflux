package dev.reflux.android

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import dev.reflux.ui.state.SystemAuthentication
import dev.reflux.ui.state.SystemConfirmation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The device's own lock, through the system's biometric prompt (Forgot PIN; DECISIONS.md, 2026-11-19). The system asks
 * for a fingerprint, a face, or the screen lock's PIN, pattern, or password; Reflux never sees any of it, only whether
 * the system confirmed the owner. Android 10 and later: from Android 11 strong biometrics or the screen lock
 * (`BIOMETRIC_STRONG | DEVICE_CREDENTIAL`), on Android 10 the same through `setDeviceCredentialAllowed`. Earlier
 * versions, and a device with no screen lock, are not offered it: the viewer keeps the profile or resets it.
 *
 * The platform `BiometricPrompt` is used rather than AndroidX Biometric, which would need a `FragmentActivity` and a new
 * dependency for the same system prompt.
 */
internal class SystemLock(private val activity: Activity) : SystemAuthentication {
    override fun available(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = activity.getSystemService(BiometricManager::class.java) ?: return false
            return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
        }
        return activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    override suspend fun confirm(title: String, subtitle: String): SystemConfirmation {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !available()) return SystemConfirmation.UNAVAILABLE
        return suspendCancellableCoroutine { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            val builder = BiometricPrompt.Builder(activity).setTitle(title).setSubtitle(subtitle)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(AUTHENTICATORS)
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            val prompt = try {
                builder.build()
            } catch (_: Exception) {
                continuation.resume(SystemConfirmation.UNAVAILABLE)
                return@suspendCancellableCoroutine
            }
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(SystemConfirmation.CONFIRMED)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    continuation.resume(
                        when (errorCode) {
                            BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_CANCELED -> SystemConfirmation.CANCELLED
                            BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE, BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT, BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
                            -> SystemConfirmation.UNAVAILABLE
                            else -> SystemConfirmation.FAILED
                        },
                    )
                }

                // A finger not recognised is one try: the system keeps asking until the viewer succeeds, gives up, or is locked out.
                override fun onAuthenticationFailed() = Unit
            }
            try {
                prompt.authenticate(cancel, activity.mainExecutor, callback)
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(SystemConfirmation.UNAVAILABLE)
            }
        }
    }

    private companion object {
        /** Android 11 and later: a strong biometric, or the screen lock itself. */
        val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
