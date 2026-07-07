package com.medical.chestxray

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.medical.chestxray.ui.navigation.AppNavigation
import com.medical.chestxray.ui.screens.AppLockScreen
import com.medical.chestxray.ui.theme.ChestXRayTheme
import com.medical.chestxray.util.AppLock

// FragmentActivity (not plain ComponentActivity) is required by BiometricPrompt for the
// App Lock feature; Compose's setContent works identically on either base class.
class MainActivity : FragmentActivity() {

    // True while the system biometric/credential dialog is on screen, so a transient
    // onStop caused by that dialog doesn't immediately re-lock the app underneath it.
    private var authInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChestXRayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val lockEnabled = remember { AppLock.isEnabled(context) }
                    var unlocked by remember { mutableStateOf(!lockEnabled) }

                    // Re-lock whenever the app is actually backgrounded (not just showing
                    // the biometric dialog), so returning to the app requires re-auth.
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP && lockEnabled && !authInProgress) {
                                unlocked = false
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    fun triggerUnlock() {
                        authInProgress = true
                        val executor = ContextCompat.getMainExecutor(this@MainActivity)
                        val prompt = BiometricPrompt(
                            this@MainActivity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    authInProgress = false
                                    unlocked = true
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    authInProgress = false
                                }
                            }
                        )
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock ChestX")
                            .setSubtitle("Verify it's you to view medical data")
                            .setAllowedAuthenticators(AppLock.ALLOWED_AUTHENTICATORS)
                            .build()
                        prompt.authenticate(promptInfo)
                    }

                    if (lockEnabled && !unlocked) {
                        AppLockScreen(onUnlockTap = ::triggerUnlock)
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
