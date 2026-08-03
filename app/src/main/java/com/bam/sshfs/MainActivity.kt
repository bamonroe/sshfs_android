package com.bam.sshfs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bam.sshfs.crypto.SshSecurity
import com.bam.sshfs.ui.shell.AppShell
import com.bam.sshfs.ui.theme.SshfsTheme

/**
 * Launcher activity — hosts the Compose UI.
 *
 * Everything lives in [AppShell]: one activity, bottom navigation over Connections,
 * Hosts, Identities and Keys.
 */
class MainActivity : ComponentActivity() {
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
