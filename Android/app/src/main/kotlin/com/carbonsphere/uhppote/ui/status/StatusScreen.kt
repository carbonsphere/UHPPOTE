package com.carbonsphere.uhppote.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.Status
import com.carbonsphere.uhppote.ui.statusRows

/** Door sensors, buttons and locks, plus the controller's status rows. */
@Composable
fun StatusScreen(vm: StatusViewModel) {
    val controller by vm.controller.collectAsState()
    val status by vm.status.collectAsState()
    val error by vm.error.collectAsState()
    val auto by vm.autoRefresh.collectAsState()

    LaunchedEffect(controller?.serial) { vm.refresh() }
    LifecycleStartEffect(vm) {
        vm.startPolling()
        onStopOrDispose { vm.stopPolling() }
    }

    val c = controller ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = vm::refresh) { Text(stringResource(R.string.refresh)) }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.heightIn(min = 48.dp).toggleable(auto, role = Role.Switch, onValueChange = vm::setAutoRefresh),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.auto_refresh), Modifier.padding(end = 8.dp))
                Switch(checked = auto, onCheckedChange = null)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        status?.let { s ->
            DoorTable(c, s)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusRows(s) { c.doorName(it, "Door $it") }.forEach { (label, value) ->
                        Row {
                            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class DoorRow(val name: String, val sensor: String, val button: String, val lock: String)

@Composable
private fun DoorTable(c: SavedController, s: Status) {
    val rows = (1..c.doorCount).map { d ->
        DoorRow(
            name = c.doorName(d, "Door $d"),
            sensor = stringResource(if (s.doorOpen[d - 1]) R.string.door_open else R.string.door_closed),
            button = stringResource(if (s.buttonPressed[d - 1]) R.string.pressed else R.string.released),
            lock = stringResource(if (s.relays and (1 shl (d - 1)) != 0) R.string.unlocked else R.string.locked),
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(r.sensor)
                                Text(r.button)
                                Text(r.lock)
                            }
                        }
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TableRow(
                        listOf(stringResource(R.string.door), stringResource(R.string.sensor), stringResource(R.string.button), stringResource(R.string.lock)),
                        header = true,
                    )
                    HorizontalDivider()
                    rows.forEach { TableRow(listOf(it.name, it.sensor, it.button, it.lock)) }
                }
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row {
        cells.forEach { Text(it, Modifier.weight(1f), fontWeight = if (header) FontWeight.Bold else null) }
    }
}
