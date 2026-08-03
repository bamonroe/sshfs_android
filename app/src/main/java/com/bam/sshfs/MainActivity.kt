package com.bam.sshfs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.bam.sshfs.crypto.BiometricAuthGate
import com.bam.sshfs.crypto.SecretAuthGate
import com.bam.sshfs.crypto.SshSecurity
import com.bam.sshfs.ui.shell.AppShell
import com.bam.sshfs.ui.theme.SshfsTheme

/**
 * Launcher activity — hosts the Compose UI.
 *
 * Everything lives in [AppShell]: one activity, bottom navigation over Connections,
 * Hosts, Identities, Keys and Settings.
 *
 * A [FragmentActivity] rather than a `ComponentActivity` because `BiometricPrompt`
 * shows itself through the fragment manager. While this activity is started it is
 * also the process's [SecretAuthGate], so a locked secret anywhere — the connection
 * service, the provider — can raise a prompt here.
 */
class MainActivity : FragmentActivity() {

    private val authGate by lazy { BiometricAuthGate(this) }

    override fun onStart() {
        super.onStart()
        SecretAuthGate.register(authGate)
    }

    override fun onStop() {
        // Nothing can prompt once we're backgrounded; unlocks must fail fast instead.
        SecretAuthGate.unregister(authGate)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SshSecurity.install()
        askForNotifications()
        setContent {
            SshfsTheme {
                AppShell()
            }
        }
    }

    /**
     * Ask for the notification permission on Android 13+.
     *
     * The connection service needs an ongoing notification to stay in the
     * foreground; without the grant the sessions still work, but the user gets no
     * visible handle on them. Asking once at launch keeps the prompt away from the
     * moment they hit Connect.
     */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
    }
}
