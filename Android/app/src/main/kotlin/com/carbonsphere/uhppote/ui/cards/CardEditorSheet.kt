package com.carbonsphere.uhppote.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.domain.CardForm
import com.carbonsphere.uhppote.domain.CardProblem
import com.carbonsphere.uhppote.protocol.Card
import com.carbonsphere.uhppote.ui.ConfirmDialog
import com.carbonsphere.uhppote.ui.rememberSaveableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The card editor in a bottom sheet (phones and narrow tablets). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorSheet(vm: CardsViewModel, controller: SavedController, editing: Editing, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CardEditor(
            vm, controller, editing, onClose,
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        )
    }
}

private enum class Access { NONE, ALLOWED, PROFILE }

private val ACCESS_LABELS = listOf(R.string.access_none, R.string.access_allowed, R.string.access_profile)

/** Number, validity dates and per-door access for one card. */
@Composable
fun CardEditor(vm: CardsViewModel, controller: SavedController, editing: Editing, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val existing = (editing as? Editing.Existing)?.card
    val message by vm.message.collectAsState()
    val today = remember { LocalDate.now() }
    var number by rememberSaveable(editing) { mutableStateOf(existing?.number?.toString() ?: "") }
    var from by rememberSaveable(editing) { mutableStateOf(existing?.from ?: today) }
    var to by rememberSaveable(editing) { mutableStateOf(existing?.to ?: LocalDate.of(today.year + 1, 12, 31)) }
    val doors = rememberSaveableList(editing) { existing?.doors ?: List(4) { 0 } }
    val profiles = rememberSaveableList(editing) { List(4) { i -> profileText(doors[i]) } }
    var problem by rememberSaveable(editing) { mutableStateOf<CardProblem?>(null) }
    var confirmDelete by rememberSaveable(editing) { mutableStateOf(false) }

    fun fill(card: Card) {
        from = card.from
        to = card.to
        card.doors.forEachIndexed { i, v ->
            if (i < 4) {
                doors[i] = v
                profiles[i] = profileText(v)
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (existing != null) stringResource(R.string.edit_card, existing.number) else stringResource(R.string.add_card),
            style = MaterialTheme.typography.titleLarge,
        )
        if (existing == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; problem = null },
                    label = { Text(stringResource(R.string.card_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = problem == CardProblem.NUMBER_MISSING || problem == CardProblem.NUMBER_INVALID,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { vm.lookup(number, ::fill) }) { Text(stringResource(R.string.look_up)) }
            }
            Text(stringResource(R.string.card_note_number), style = MaterialTheme.typography.bodySmall)
        }

        DateField(stringResource(R.string.valid_from, from.toString()), from) { from = it; problem = null }
        DateField(stringResource(R.string.valid_to, to.toString()), to) { to = it; problem = null }

        (1..controller.doorCount).forEach { d ->
            DoorAccess(
                controller.doorName(d, "Door $d"), doors[d - 1], profiles[d - 1],
                onChange = { doors[d - 1] = it; problem = null },
                onProfileText = { profiles[d - 1] = it },
            )
        }
        Text(stringResource(R.string.card_note_profiles), style = MaterialTheme.typography.bodySmall)

        problem?.let { p ->
            Text(
                stringResource(
                    when (p) {
                        CardProblem.NUMBER_MISSING -> R.string.err_card_number
                        CardProblem.NUMBER_INVALID -> R.string.err_card_invalid
                        CardProblem.DATES_INVALID -> R.string.err_card_date_invalid
                        CardProblem.DATES_REVERSED -> R.string.err_card_dates
                        CardProblem.PROFILE_INVALID -> R.string.err_card_profile
                    },
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.delete)) }
            }
            Button(
                onClick = { problem = vm.save(CardForm(number, from, to, doors.toList()), onDone = onClose) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_card),
            text = stringResource(R.string.delete_card_message, existing.number),
            confirm = stringResource(R.string.delete),
            onConfirm = { vm.delete(existing.number, onDone = onClose) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    if (open) {
        // A card with unreadable dates (LocalDate.MIN) starts the picker at today.
        val start = value.takeIf { it.year in 1900..2100 } ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

/** The profile field's text for a door value (a fresh profile field starts at 2). */
private fun profileText(value: Int): String = when {
    value >= 2 -> value.toString()
    value < 0 -> ""
    else -> "2"
}

/** The door value for a typed profile number; anything that is not 2-254 fails validation. */
private fun profileValue(text: String): Int = text.toIntOrNull()?.takeIf { it >= 2 } ?: -1

@Composable
private fun DoorAccess(name: String, value: Int, profileText: String, onChange: (Int) -> Unit, onProfileText: (String) -> Unit) {
    val access = when (value) {
        0 -> Access.NONE
        1 -> Access.ALLOWED
        else -> Access.PROFILE
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Access.entries.forEachIndexed { i, a ->
                SegmentedButton(
                    selected = access == a,
                    onClick = {
                        onChange(
                            when (a) {
                                Access.NONE -> 0
                                Access.ALLOWED -> 1
                                Access.PROFILE -> profileValue(profileText)
                            },
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, Access.entries.size),
                ) { Text(stringResource(ACCESS_LABELS[i]), maxLines = 1) }
            }
        }
        if (access == Access.PROFILE) {
            OutlinedTextField(
                value = profileText,
                onValueChange = { t ->
                    val text = t.filter(Char::isDigit).take(3)
                    onProfileText(text)
                    onChange(profileValue(text))
                },
                label = { Text(stringResource(R.string.profile_number)) },
                isError = value !in 2..254,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}
