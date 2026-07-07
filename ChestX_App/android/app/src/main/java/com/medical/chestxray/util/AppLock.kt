package com.medical.chestxray.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Persists whether "App Lock" is enabled — requiring the device's biometric or
 * PIN/pattern/password credential before ChestX's content is shown, since the app
 * stores patient names and medical images locally.
 */
object AppLock {

    /** Accept either biometrics (fingerprint/face) or the device's PIN/pattern/password. */
    const val ALLOWED_AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private const val PREFS = "chestx_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** True if the device currently has a usable biometric or screen-lock credential set up. */
    fun canUseDeviceCredential(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
