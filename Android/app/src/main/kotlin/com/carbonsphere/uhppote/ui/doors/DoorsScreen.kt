package com.carbonsphere.uhppote.ui.doors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.carbonsphere.uhppote.R

/** The home screen: a big Open button for every door at the site. */
@Composable
fun DoorsScreen(vm: DoorsViewModel) {
    val tiles by vm.tiles.collectAsState()
    var settingsFor by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    var advancedFor by rememberSaveable { mutableStateOf<Long?>(null) }

    // Poll door sensors only while the screen is visible.
    LifecycleStartEffect(vm) {
        vm.startPolling()
        onStopOrDispose { vm.stopPolling() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 600.dp -> 1
            maxWidth < 840.dp -> 2
            else -> 4
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tiles.forEach { group ->
                item(key = "h${group.controller.serial}", span = { GridItemSpan(maxLineSpan) }) {
                    ControllerHeader(
                        group = group,
                        showName = tiles.size > 1,
                        onAdvanced = { advancedFor = group.controller.serial },
                    )
                }
                items(group.doors, key = { "${group.controller.serial}-${it.door}" }) { tile ->
                    DoorTile(
                        tile = tile,
                        onOpen = { vm.open(group.controller.serial, tile.door) },
                        onSettings = { settingsFor = group.controller.serial to tile.door },
                    )
                }
            }
        }
    }

    settingsFor?.let { (serial, door) ->
        val c = tiles.firstOrNull { it.controller.serial == serial }?.controller
        if (c != null) {
            DoorSettingsSheet(vm, c, door, onDismiss = { settingsFor = null; vm.clearMessage() })
        }
    }
    advancedFor?.let { serial ->
        val c = tiles.firstOrNull { it.controller.serial == serial }?.controller
        if (c != null) {
            AdvancedDoorsSheet(vm, c, onDismiss = { advancedFor = null; vm.clearMessage() })
        }
    }
}

@Composable
private fun ControllerHeader(group: ControllerDoors, showName: Boolean, onAdvanced: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showName) {
            Text(group.controller.displayName(), style = MaterialTheme.typography.titleMedium)
        }
        if (group.reachable == false) {
            Text(
                stringResource(R.string.not_responding),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = if (showName) 12.dp else 0.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.advanced)) },
                onClick = { menu = false; onAdvanced() },
            )
        }
    }
}
