package com.carbonsphere.uhppote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import java.time.format.DateTimeFormatter

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")

/** Every action the app took, newest at the bottom, optionally with raw packets. */
@Composable
fun LogScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val lines by vm.log.lines.collectAsState()
    val state by vm.state.collectAsState()
    val list = rememberLazyListState()
    BackHandler(onBack = onBack)
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) list.scrollToItem(lines.size - 1)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.activity_log), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = vm.log::clear) { Text(stringResource(R.string.clear)) }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp)
                .toggleable(state.showRawPackets, role = Role.Switch, onValueChange = vm::setShowRawPackets),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.show_raw_packets), Modifier.weight(1f))
            Switch(checked = state.showRawPackets, onCheckedChange = null)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp)) {
            items(lines) { l ->
                Text(
                    "${l.time.format(CLOCK)}  ${l.text}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        l.text.startsWith("✖") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
