package com.bam.sshfs

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        setContent {
            SshfsTheme {
                AppShell()
            }
        }
    }
}
