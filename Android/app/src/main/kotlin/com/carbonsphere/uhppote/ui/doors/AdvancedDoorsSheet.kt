package com.carbonsphere.uhppote.ui.doors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.INTERLOCK_MODES

/** Write-only controller settings: door interlock and reader keypads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedDoorsSheet(vm: DoorsViewModel, controller: SavedController, onDismiss: () -> Unit) {
    val message by vm.message.collectAsState()
    var interlock by remember { mutableIntStateOf(INTERLOCK_MODES.first().first) }
    val keypads = remember { mutableStateListOf(false, false, false, false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(controller.displayName(), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.write_only_note), style = MaterialTheme.typography.bodyMedium)
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

            Text(stringResource(R.string.interlock), style = MaterialTheme.typography.titleSmall)
            INTERLOCK_MODES.forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = interlock == value, role = Role.RadioButton, onClick = { interlock = value }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = interlock == value, onClick = null)
                    Text(label, Modifier.padding(start = 12.dp))
                }
            }
            Button(onClick = { vm.setInterlock(controller.serial, interlock) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_interlock))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.keypads), style = MaterialTheme.typography.titleSmall)
            (1..controller.doorCount).forEach { n ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = keypads[n - 1], role = Role.Switch, onValueChange = { keypads[n - 1] = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.keypad_n, n), Modifier.weight(1f))
                    Switch(checked = keypads[n - 1], onCheckedChange = null)
                }
            }
            Button(onClick = { vm.setKeypads(controller.serial, keypads.toList()) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_keypads))
            }
        }
    }
}
