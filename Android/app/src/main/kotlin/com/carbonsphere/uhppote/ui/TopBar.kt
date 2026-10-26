package com.carbonsphere.uhppote.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.Profile

/**
 * The site name (tap to switch site) and, for sections that work on one
 * controller, a controller picker when the site has several.
 */
@Composable
fun TopBar(vm: MainViewModel, profile: Profile, destination: Destination) {
    val selected by vm.selectedController.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = vm::showProfiles) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.switch_site))
        }
        Spacer(Modifier.weight(1f))
        if (destination != Destination.DOORS && profile.controllers.size > 1) {
            Box {
                TextButton(onClick = { menu = true }) {
                    Text(selected?.displayName() ?: stringResource(R.string.controller))
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    profile.controllers.forEach { c ->
                        DropdownMenuItem(
                            text = { Text("${c.displayName()} · ${c.ip}") },
                            onClick = { vm.selectController(c.serial); menu = false },
                        )
                    }
                }
            }
        }
    }
}
