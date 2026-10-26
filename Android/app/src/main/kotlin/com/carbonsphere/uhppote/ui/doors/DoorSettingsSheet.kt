package com.carbonsphere.uhppote.ui.doors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.DoorControl
import com.carbonsphere.uhppote.protocol.DoorMode

private val MODES = listOf(
    DoorMode.NORMALLY_OPEN to R.string.mode_normally_open,
    DoorMode.NORMALLY_CLOSED to R.string.mode_normally_closed,
    DoorMode.CONTROLLED to R.string.mode_controlled,
)

/** Lock mode and unlock time for one door, read from and saved to the controller. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorSettingsSheet(vm: DoorsViewModel, controller: SavedController, door: Int, onDismiss: () -> Unit) {
    val dc by vm.doorControl.collectAsState()
    val message by vm.message.collectAsState()
    var mode by remember { mutableIntStateOf(DoorMode.CONTROLLED) }
    var delay by remember { mutableIntStateOf(5) }

    LaunchedEffect(controller.serial, door) { vm.readDoorControl(controller.serial, door) }
    LaunchedEffect(dc) {
        dc?.takeIf { it.door == door }?.let { mode = it.mode; delay = it.delay }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(controller.doorName(door, "Door $door"), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.lock_mode), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                MODES.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = { mode = value },
                        shape = SegmentedButtonDefaults.itemShape(i, MODES.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            Text(stringResource(R.string.unlock_time, delay), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { delay = (delay - 1).coerceAtLeast(0) }, modifier = Modifier.size(56.dp)) { Text("−") }
                Slider(
                    value = delay.toFloat(),
                    onValueChange = { delay = it.toInt() },
                    valueRange = 0f..255f,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { delay = (delay + 1).coerceAtMost(255) }, modifier = Modifier.size(56.dp)) { Text("+") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.readDoorControl(controller.serial, door) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.read))
                }
                Button(onClick = { vm.saveDoorControl(controller.serial, DoorControl(door, mode, delay)) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
