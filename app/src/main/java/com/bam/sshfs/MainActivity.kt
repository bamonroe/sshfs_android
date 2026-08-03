package com.bam.sshfs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.bam.sshfs.ui.PlaceholderScreen
import com.bam.sshfs.ui.theme.SshfsTheme

/** Launcher activity — hosts the Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SshfsTheme {
                Scaffold { inner ->
                    PlaceholderScreen(Modifier.padding(inner))
                }
            }
        }
    }
}
