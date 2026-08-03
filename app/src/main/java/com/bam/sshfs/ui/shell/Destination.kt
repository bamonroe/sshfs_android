package com.bam.sshfs.ui.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.bam.sshfs.R

/**
 * The four sections of the app, in bottom-navigation order.
 *
 * The enum *is* the nav bar: adding a section here adds its tab, its icon and its
 * label, so there's no second list to keep in sync.
 */
enum class Destination(
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Connections(R.string.connections_title, Icons.Filled.Share),
    Hosts(R.string.hosts_title, Icons.Filled.Home),
    Identities(R.string.identities_title, Icons.Filled.Person),
    Keys(R.string.keys_title, Icons.Filled.Lock),
}
