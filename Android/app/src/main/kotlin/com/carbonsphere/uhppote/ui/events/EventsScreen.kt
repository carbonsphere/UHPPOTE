package com.carbonsphere.uhppote.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.Event
import com.carbonsphere.uhppote.ui.formatTime

/** The selected controller's event log, newest first. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventsScreen(vm: EventsViewModel) {
    val controller by vm.controller.collectAsState()
    val newest by vm.newest.collectAsState()
    val read by vm.readIndex.collectAsState()
    val events by vm.events.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    var want by rememberSaveable { mutableLongStateOf(20L) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(controller?.serial) { vm.readIndexes() }
    val c = controller ?: return

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.event_indexes, newest?.toString() ?: "-", read?.toString() ?: "-"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.show_last))
                FilledTonalButton(onClick = { want = (want - 10).coerceAtLeast(1) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
                OutlinedTextField(
                    value = want.toString(),
                    onValueChange = { t -> t.filter(Char::isDigit).take(3).toLongOrNull()?.let { want = it.coerceIn(1, 500) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(88.dp),
                )
                FilledTonalButton(onClick = { want = (want + 10).coerceAtMost(500) }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.fetch(want) }, enabled = progress == null) { Text(stringResource(R.string.fetch)) }
                OutlinedButton(onClick = vm::fetchUnread, enabled = progress == null) { Text(stringResource(R.string.fetch_unread)) }
                OutlinedButton(onClick = vm::markAllRead) { Text(stringResource(R.string.mark_all_read)) }
            }
            progress?.let { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(Modifier.weight(1f))
                    Text(stringResource(R.string.fetched_n, n), Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = vm::cancelFetch) { Text(stringResource(R.string.cancel)) }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        }

        PullToRefreshBox(isRefreshing = progress != null, onRefresh = { vm.fetch(want) }, modifier = Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 840.dp) {
                    Row(Modifier.fillMaxSize()) {
                        EventList(events, c, selected, { selected = it }, Modifier.weight(1f))
                        val e = events.firstOrNull { it.index == selected }
                        Column(Modifier.weight(1f).padding(16.dp)) {
                            if (e != null) EventDetail(e, c)
                        }
                    }
                } else {
                    EventList(events, c, null, { selected = it }, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun EventList(events: List<Event>, c: SavedController, selected: Long?, onSelect: (Long) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(events, key = { it.index }) { e ->
            Card(
                Modifier.fillMaxWidth().clickable { onSelect(e.index) },
                colors = if (e.index == selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text(formatTime(e.timestamp), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Access(e)
                    }
                    Text("#${e.index} · ${stringResource(R.string.card_n, e.card)} · ${doorText(e, c)} · ${e.directionText()}")
                    Text("${e.typeText()} · ${e.reasonText()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Access(e: Event) {
    Text(
        stringResource(if (e.granted) R.string.granted else R.string.denied),
        color = if (e.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.titleSmall,
    )
}

private fun doorText(e: Event, c: SavedController) = if (e.door in 1..4) c.doorName(e.door, "Door ${e.door}") else "Door ${e.door}"

@Composable
private fun EventDetail(e: Event, c: SavedController) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Event #" to e.index.toString(),
                "Time" to formatTime(e.timestamp),
                "Type" to e.typeText(),
                "Card" to e.card.toString(),
                "Door / direction" to "${doorText(e, c)} / ${e.directionText()}",
                "Access" to if (e.granted) "Granted" else "Denied",
                "Reason" to e.reasonText(),
            ).forEach { (label, value) ->
                Row {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, Modifier.weight(2f))
                }
            }
        }
    }
}
