package com.carbonsphere.uhppote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The main frame: bottom navigation bar on narrow screens (phone portrait),
 * navigation rail on wider ones (phone landscape, tablets).
 */
@Composable
fun AppScaffold(topBar: @Composable () -> Unit, content: @Composable (Destination) -> Unit) {
    var current by rememberSaveable { mutableStateOf(Destination.DOORS) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Destination.entries.forEach { d ->
                item(
                    selected = d == current,
                    onClick = { current = d },
                    icon = { Icon(d.icon, contentDescription = null) },
                    label = { Text(stringResource(d.label)) },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            topBar()
            content(current)
        }
    }
}
