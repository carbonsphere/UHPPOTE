package com.carbonsphere.uhppote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.AppState
import com.carbonsphere.uhppote.graph
import com.carbonsphere.uhppote.ui.theme.UhppoteTheme
import kotlinx.coroutines.launch

/** Picks the site, controller and door a new widget opens. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves no half-configured widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            UhppoteTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by graph.store.state.collectAsState(initial = AppState())
                    LazyColumn(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        item {
                            Text(stringResource(R.string.widget_pick_door), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                        }
                        if (state.profiles.none { it.controllers.isNotEmpty() }) {
                            item { Text(stringResource(R.string.widget_no_controllers), Modifier.padding(16.dp)) }
                        }
                        state.profiles.forEach { p ->
                            p.controllers.forEach { c ->
                                item(key = "${p.id}-${c.serial}") {
                                    Text(
                                        "${p.name} · ${c.displayName()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                                    )
                                }
                                (1..c.doorCount).forEach { d ->
                                    item(key = "${p.id}-${c.serial}-$d") {
                                        ListItem(
                                            headlineContent = { Text(c.doorName(d, getString(R.string.door_n, d))) },
                                            modifier = Modifier.clickable { choose(widgetId, p.id, c.serial, d) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(widgetId: Int, profileId: String, serial: Long, door: Int) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
                it[WidgetKeys.profileId] = profileId
                it[WidgetKeys.serial] = serial
                it[WidgetKeys.door] = door
                it[WidgetKeys.phase] = Phase.IDLE
            }
            DoorWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
