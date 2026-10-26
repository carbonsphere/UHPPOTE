package com.carbonsphere.uhppote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.BuildConfig
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.ui.DangerConfirmDialog

const val REPO_URL = "https://github.com/carbonsphere/UHPPOTE"

/** Clock, event listener, doors, network, display, activity log and about. */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    var showLog by rememberSaveable { mutableStateOf(false) }
    if (showLog) {
        LogScreen(vm, onBack = { showLog = false })
        return
    }
    val controller by vm.controller.collectAsState()
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        controller?.let { c ->
            ClockSection(vm)
            ListenerSection(vm)
            DoorsSection(vm, c)
            NetworkSection(vm, c)
        }
        Section(stringResource(R.string.display)) {
            SwitchRow(stringResource(R.string.keep_screen_on), state.keepScreenOn, vm::setKeepScreenOn)
        }
        Section(stringResource(R.string.activity_log)) {
            OutlinedButton(onClick = { showLog = true }) { Text(stringResource(R.string.open_activity_log)) }
        }
        Section(stringResource(R.string.about)) {
            val uri = LocalUriHandler.current
            Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME)
            TextButton(onClick = { uri.openUri(REPO_URL) }) { Text(REPO_URL) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(2f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockSection(vm: SettingsViewModel) {
    val time by vm.time.collectAsState()
    Section(stringResource(R.string.clock)) {
        LabelValue(stringResource(R.string.controller_time), time?.controller ?: "-")
        LabelValue(stringResource(R.string.phone_time), time?.let { "${it.phone}  (${it.drift})" } ?: "-")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::readTime) { Text(stringResource(R.string.read)) }
            Button(onClick = vm::syncTime) { Text(stringResource(R.string.sync_time)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListenerSection(vm: SettingsViewModel) {
    val form by vm.listener.collectAsState()
    Section(stringResource(R.string.event_listener)) {
        OutlinedTextField(
            value = form.ip,
            onValueChange = { vm.updateListener(form.copy(ip = it)) },
            label = { Text(stringResource(R.string.ip_address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(stringResource(R.string.port), form.port, 65535, Modifier.weight(1f)) { vm.updateListener(form.copy(port = it)) }
            NumberField(stringResource(R.string.interval_s), form.interval, 255, Modifier.weight(1f)) { vm.updateListener(form.copy(interval = it)) }
        }
        Text(stringResource(R.string.interval_note), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::readListener) { Text(stringResource(R.string.read)) }
            OutlinedButton(onClick = vm::useThisDevice) { Text(stringResource(R.string.use_this_device)) }
            Button(onClick = { vm.saveListener(form.ip, form.port, form.interval) }) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(5)
            text.toIntOrNull()?.takeIf { it <= max }?.let(onChange)
        },
        label = { Text(label) },
        isError = text.toIntOrNull()?.let { it > max } ?: true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun DoorsSection(vm: SettingsViewModel, c: SavedController) {
    var edit by remember { mutableStateOf(false) }
    Section(stringResource(R.string.doors_section)) {
        LabelValue(stringResource(R.string.controller), c.displayName())
        (1..c.doorCount).forEach { d -> LabelValue(stringResource(R.string.door_n, d), c.doorName(d, "Door $d")) }
        OutlinedButton(onClick = { edit = true }) { Text(stringResource(R.string.edit_names)) }
    }
    if (edit) DoorNamesSheet(vm, c, onDismiss = { edit = false })
}

@Composable
private fun NetworkSection(vm: SettingsViewModel, c: SavedController) {
    var ip by remember(c.serial, c.ip) { mutableStateOf(c.ip) }
    var mask by remember(c.serial) { mutableStateOf(c.mask) }
    var gateway by remember(c.serial) { mutableStateOf(c.gateway) }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    Section(stringResource(R.string.network)) {
        listOf(
            Triple(R.string.ip_address, ip) { v: String -> ip = v },
            Triple(R.string.subnet_mask, mask) { v: String -> mask = v },
            Triple(R.string.gateway, gateway) { v: String -> gateway = v },
        ).forEach { (label, value, set) ->
            OutlinedTextField(
                value = value,
                onValueChange = { set(it); problem = null },
                label = { Text(stringResource(label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    problem = vm.networkProblem(ip, mask, gateway)
                    if (problem == null) confirm = true
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.widthIn(min = 120.dp),
            ) { Text(stringResource(R.string.change_network)) }
        }
    }
    if (confirm) {
        DangerConfirmDialog(
            title = stringResource(R.string.change_network_title),
            text = stringResource(R.string.change_network_message, c.serial, ip.trim(), mask.trim(), gateway.trim()),
            confirm = stringResource(R.string.change),
            onConfirm = { vm.changeNetwork(ip, mask, gateway) },
            onDismiss = { confirm = false },
        )
    }
}
