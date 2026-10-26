package com.carbonsphere.uhppote.ui.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.ui.DangerConfirmDialog
import java.time.LocalDate

private const val NEW_CARD = -1L

/** What the editor is showing: a new card, or an existing one. */
sealed interface Editing {
    data object New : Editing
    data class Existing(val card: Card) : Editing
}

/** Every card on the selected controller, with search, add/edit and delete. */
@Composable
fun CardsScreen(vm: CardsViewModel) {
    // Cards are read from the controller only while this screen is showing.
    LifecycleStartEffect(vm) {
        vm.setVisible(true)
        onStopOrDispose { vm.setVisible(false) }
    }
    val controller by vm.controller.collectAsState()
    val c = controller ?: return
    val count by vm.count.collectAsState()
    val cards by vm.cards.collectAsState()
    val query by vm.query.collectAsState()
    val progress by vm.loadProgress.collectAsState()
    val complete by vm.complete.collectAsState()
    val message by vm.message.collectAsState()
    val fromCache by vm.fromCache.collectAsState()
    // The open editor, by card number (NEW_CARD for a new card), so it survives rotation.
    var editingNumber by rememberSaveable(c.serial) { mutableStateOf<Long?>(null) }
    val editing: Editing? = editingNumber?.let { n -> if (n == NEW_CARD) Editing.New else vm.card(n)?.let { Editing.Existing(it) } }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { vm.clearMessage(); editingNumber = NEW_CARD },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.add_card)) },
                )
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.card_count, count?.toString() ?: "-"),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (progress != null) {
                                TextButton(onClick = vm::cancelLoad) { Text(stringResource(R.string.cancel)) }
                            } else {
                                OutlinedButton(onClick = vm::reload) { Text(stringResource(R.string.reload)) }
                            }
                        }
                        progress?.let { p ->
                            LinearProgressIndicator(
                                progress = { if (p.count > 0) p.found.toFloat() / p.count else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.loading_cards, p.index, p.found, p.count), style = MaterialTheme.typography.bodySmall)
                        }
                        if (fromCache && progress == null) {
                            Text(stringResource(R.string.saved_list_hint), style = MaterialTheme.typography.bodySmall)
                        }
                        if (!complete && progress == null) {
                            Text(stringResource(R.string.list_incomplete), color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.filter(it.filter(Char::isDigit)) },
                            label = { Text(stringResource(R.string.search_cards)) },
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (editing == null || wide) message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                        if (cards.isEmpty() && progress == null) {
                            item { Text(stringResource(R.string.no_cards), Modifier.padding(16.dp)) }
                        }
                        items(cards, key = { it.number }) { card ->
                            CardRow(card, c) { vm.clearMessage(); editingNumber = card.number }
                            HorizontalDivider()
                        }
                        item {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.danger_zone), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                Row {
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(
                                        onClick = { confirmDeleteAll = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    ) { Text(stringResource(R.string.delete_all_cards)) }
                                }
                            }
                        }
                    }
                }
                if (wide) {
                    editing?.let { e ->
                        Column(Modifier.weight(1f).fillMaxSize()) {
                            CardEditor(vm, c, e, onClose = { editingNumber = null }, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
        }
        if (!wide) {
            editing?.let { e -> CardEditorSheet(vm, c, e, onClose = { editingNumber = null }) }
        }
    }

    if (confirmDeleteAll) {
        DangerConfirmDialog(
            title = stringResource(R.string.delete_all_title),
            text = stringResource(R.string.delete_all_message, c.serial),
            confirm = stringResource(R.string.delete_all_confirm),
            onConfirm = vm::deleteAll,
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun CardRow(card: Card, c: SavedController, onClick: () -> Unit) {
    val expired = card.to.isBefore(LocalDate.now())
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(card.number.toString(), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(doorSummary(card, c)) },
        trailingContent = {
            Text(
                stringResource(if (expired) R.string.expired else R.string.valid_to_short, card.to.toString()),
                color = if (expired) MaterialTheme.colorScheme.error else ListItemDefaults.colors().supportingTextColor,
            )
        },
    )
}

/** "Front door, Door 2 (profile 5)" — the doors this card opens. */
@Composable
private fun doorSummary(card: Card, c: SavedController): String {
    val parts = (1..c.doorCount).mapNotNull { d ->
        val name = c.doorName(d, "Door $d")
        when (val v = card.doors.getOrElse(d - 1) { 0 }) {
            0 -> null
            1 -> name
            else -> stringResource(R.string.door_profile, name, v)
        }
    }
    return if (parts.isEmpty()) stringResource(R.string.no_doors) else parts.joinToString(", ")
}
