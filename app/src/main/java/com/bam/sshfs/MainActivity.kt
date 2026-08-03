package com.bam.sshfs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bam.sshfs.crypto.SshSecurity
import com.bam.sshfs.ui.hosts.HostsScreen
import com.bam.sshfs.ui.theme.SshfsTheme

/**
 * Launcher activity — hosts the Compose UI.
 *
 * Shows the Hosts screen directly for now; the Hosts / Identities / Keys bottom
 * navigation replaces this in the app-shell task.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SshSecurity.install()
        setContent {
            SshfsTheme {
                HostsScreen()
            }
        }
    }
}
