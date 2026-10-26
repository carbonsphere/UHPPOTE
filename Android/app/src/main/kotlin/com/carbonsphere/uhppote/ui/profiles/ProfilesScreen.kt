package com.carbonsphere.uhppote.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.Profile
import com.carbonsphere.uhppote.data.activeProfile
import com.carbonsphere.uhppote.data.deleteProfile
import com.carbonsphere.uhppote.data.moveProfile
import com.carbonsphere.uhppote.data.renameProfile
import com.carbonsphere.uhppote.ui.MainViewModel

/** The list of sites (profiles): pick one, rename, reorder, delete, or add controllers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val hasActive = state.activeProfile() != null
    var adding by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

    BackHandler(enabled = hasActive) { vm.closeProfiles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sites)) },
                navigationIcon = {
                    if (hasActive) {
                        IconButton(onClick = vm::closeProfiles) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_controllers)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.profiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_sites),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp).widthIn(max = 600.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            ) {
                itemsIndexed(state.profiles, key = { _, p -> p.id }) { index, p ->
                    ProfileCard(
                        profile = p,
                        active = p.id == state.activeProfileId,
                        onClick = { vm.switchProfile(p.id) },
                        onRename = { renaming = p },
                        onMoveUp = { vm.updateState { it.moveProfile(p.id, index - 1) } }.takeIf { index > 0 },
                        onMoveDown = { vm.updateState { it.moveProfile(p.id, index + 1) } }.takeIf { index < state.profiles.size - 1 },
                        onDelete = { deleting = p },
                    )
                }
            }
        }
    }

    if (adding) SearchSheet(vm, onDismiss = { adding = false; vm.clearSearch() })

    renaming?.let { p ->
        NameDialog(
            title = stringResource(R.string.rename_site),
            initial = p.name,
            onDismiss = { renaming = null },
            onSave = { name -> vm.updateState { it.renameProfile(p.id, name) }; renaming = null },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_site)) },
            text = { Text(stringResource(R.string.delete_site_message, p.name)) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { vm.updateState { it.deleteProfile(p.id) }; deleting = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (active) "● " else "") + profile.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(
                        R.string.controllers_count,
                        profile.controllers.size,
                        profile.controllers.joinToString { it.ip },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                    onMoveUp?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; it() }) }
                    onMoveDown?.let { DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** A dialog asking for a name. */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.save)) } },
    )
}
