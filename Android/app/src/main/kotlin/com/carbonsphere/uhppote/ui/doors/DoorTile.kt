package com.carbonsphere.uhppote.ui.doors

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R

private val OPENED_GREEN = Color(0xFF2E7D32)

/** One door: its name, sensor and lock state, and a big Open button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DoorTile(tile: DoorTileState, onOpen: () -> Unit, onSettings: () -> Unit) {
    val view = LocalView.current
    LaunchedEffect(tile.lastResult) {
        if (tile.lastResult == OpenResult.OPENED) {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(effect)
        }
    }

    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onSettings)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tile.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.door_settings))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    when (tile.open) {
                        true -> stringResource(R.string.door_open)
                        false -> stringResource(R.string.door_closed)
                        null -> stringResource(R.string.unknown)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when (tile.unlocked) {
                        true -> stringResource(R.string.unlocked)
                        false -> stringResource(R.string.locked)
                        null -> stringResource(R.string.unknown)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (tile.unlocked == true) OPENED_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val opened = tile.lastResult == OpenResult.OPENED
            Button(
                onClick = onOpen,
                enabled = !tile.opening,
                colors = if (opened) ButtonDefaults.buttonColors(containerColor = OPENED_GREEN) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            ) {
                when {
                    tile.opening -> CircularProgressIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
                    opened -> {
                        Icon(Icons.Filled.Check, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.opened), style = MaterialTheme.typography.headlineSmall)
                    }
                    else -> Text(stringResource(R.string.open), style = MaterialTheme.typography.headlineSmall)
                }
            }
            when (tile.lastResult) {
                OpenResult.REFUSED -> Text(stringResource(R.string.refused), color = MaterialTheme.colorScheme.error)
                OpenResult.NO_REPLY -> Text(stringResource(R.string.no_reply), color = MaterialTheme.colorScheme.error)
                else -> {}
            }
        }
    }
}
