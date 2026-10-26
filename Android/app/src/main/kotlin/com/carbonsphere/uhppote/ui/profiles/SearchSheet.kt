package com.carbonsphere.uhppote.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.domain.defaultSaveTarget
import com.carbonsphere.uhppote.ui.MainViewModel
import com.carbonsphere.uhppote.ui.SearchState

/** Search the network (or one IP), pick the controllers found and save them to a site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val search by vm.searchState.collectAsState()
    val state by vm.state.collectAsState()
    var target by rememberSaveable { mutableStateOf("") }
    var unpicked by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    // null = new site. Until the manager picks, follow where the found controllers belong.
    var profileId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileChosen by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var searched by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!searched) {
            searched = true
            vm.search("")
        }
    }
    LaunchedEffect(search) {
        val s = search
        if (s is SearchState.Found && !profileChosen) profileId = defaultSaveTarget(state, s.devices)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.add_controllers), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(target) }),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { vm.search(target) }, enabled = search != SearchState.Searching) {
                    Text(stringResource(R.string.search))
                }
            }

            when (val s = search) {
                SearchState.Idle -> {}
                SearchState.Searching -> {
                    Text(stringResource(R.string.searching))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is SearchState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is SearchState.Found -> {
                    if (s.devices.isEmpty()) Text(stringResource(R.string.none_found))
                    s.devices.forEach { d ->
                        val checked = d.serial !in unpicked
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = { unpicked = if (it) unpicked - d.serial else unpicked + d.serial })
                            Text(
                                stringResource(R.string.found_controller, d.serial, d.ip.hostAddress ?: "", d.firmware),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (s.devices.isNotEmpty()) {
                        val chosen = s.devices.filter { it.serial !in unpicked }
                        val label = state.profiles.firstOrNull { it.id == profileId }?.name ?: stringResource(R.string.new_site)
                        ExposedDropdownMenuBox(expanded = pickerOpen, onExpandedChange = { pickerOpen = it }) {
                            OutlinedTextField(
                                value = label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.save_to)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pickerOpen) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                                state.profiles.forEach { p ->
                                    DropdownMenuItem(text = { Text(p.name) }, onClick = { profileId = p.id; profileChosen = true; pickerOpen = false })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.new_site)) }, onClick = { profileId = null; profileChosen = true; pickerOpen = false })
                            }
                        }
                        if (profileId == null) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text(stringResource(R.string.new_site_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = { vm.saveFound(chosen, profileId, newName); onDismiss() },
                            enabled = chosen.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
            Row(Modifier.padding(bottom = 16.dp)) {}
        }
    }
}
