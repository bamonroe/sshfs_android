package com.bam.sshfs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bam.sshfs.R

/**
 * The scaffold's only screen: proof the Compose/Material 3 stack builds and runs.
 * The Hosts / Identities / Keys navigation replaces this in the next task.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.scaffold_headline),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.scaffold_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
