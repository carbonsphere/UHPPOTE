package com.carbonsphere.uhppote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.ui.rememberSaveableList

/** The controller's name, how many doors it has, and what each door is called. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoorNamesSheet(vm: SettingsViewModel, c: SavedController, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(c.name) }
    var count by rememberSaveable { mutableIntStateOf(c.doorCount) }
    val names = rememberSaveableList { List(4) { c.doorNames.getOrElse(it) { "" } } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.doors_of, c.serial), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.controller_name)) },
                placeholder = { Text(c.serial.toString()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.door_count), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..4).forEach { n ->
                    SegmentedButton(
                        selected = count == n,
                        onClick = { count = n },
                        shape = SegmentedButtonDefaults.itemShape(n - 1, 4),
                    ) { Text(n.toString()) }
                }
            }
            (1..count).forEach { d ->
                OutlinedTextField(
                    value = names[d - 1],
                    onValueChange = { names[d - 1] = it },
                    label = { Text(stringResource(R.string.door_n_name, d)) },
                    placeholder = { Text("Door $d") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    vm.renameController(c.serial, name.trim())
                    vm.setDoorCount(c.serial, count)
                    (1..4).forEach { d -> vm.setDoorName(c.serial, d, names[d - 1].trim()) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}
