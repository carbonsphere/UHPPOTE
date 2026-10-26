package com.carbonsphere.uhppote.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.carbonsphere.uhppote.MainActivity
import com.carbonsphere.uhppote.R
import com.carbonsphere.uhppote.data.findController
import com.carbonsphere.uhppote.domain.PHASE_OPENING
import com.carbonsphere.uhppote.graph

/** Per-widget state: which door it opens and what it is showing. */
object WidgetKeys {
    val profileId = stringPreferencesKey("profileId")
    val serial = longPreferencesKey("serial")
    val door = intPreferencesKey("door")
    val phase = stringPreferencesKey("phase")
    val openingSince = longPreferencesKey("openingSince")
}

object Phase {
    const val IDLE = "idle"
    const val OPENING = PHASE_OPENING
    const val OPENED = "opened"
    const val REFUSED = "refused"
    const val NO_REPLY = "noreply"
}

/** A one-tap "open this door" button for the home screen. */
class DoorWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = context.graph.store
        provideContent {
            val app by store.state.collectAsState(initial = null)
            GlanceTheme { Content(context, app?.let { s -> currentState<Preferences>()[WidgetKeys.serial]?.let(s::findController) }, app != null) }
        }
    }

    @Composable
    private fun Content(context: Context, controller: com.carbonsphere.uhppote.data.SavedController?, loaded: Boolean) {
        val prefs = currentState<Preferences>()
        val door = prefs[WidgetKeys.door] ?: 1
        val phase = prefs[WidgetKeys.phase] ?: Phase.IDLE
        val known = controller != null && door in 1..4

        val name = when {
            known -> controller!!.doorName(door, context.getString(R.string.door_n, door))
            loaded -> context.getString(R.string.widget_set_up_again)
            else -> ""
        }
        val status = when (phase) {
            Phase.OPENING -> context.getString(R.string.widget_opening)
            Phase.OPENED -> context.getString(R.string.widget_opened)
            Phase.REFUSED -> context.getString(R.string.widget_refused)
            Phase.NO_REPLY -> context.getString(R.string.widget_no_reply)
            else -> null
        }
        val background = when (phase) {
            Phase.OPENED -> ColorProvider(OPENED_GREEN)
            Phase.REFUSED, Phase.NO_REPLY -> GlanceTheme.colors.errorContainer
            else -> GlanceTheme.colors.primaryContainer
        }
        val textColor = when (phase) {
            Phase.OPENED -> ColorProvider(androidx.compose.ui.graphics.Color.White)
            Phase.REFUSED, Phase.NO_REPLY -> GlanceTheme.colors.onErrorContainer
            else -> GlanceTheme.colors.onPrimaryContainer
        }
        val click = if (known) actionRunCallback<OpenDoorAction>() else actionStartActivity<MainActivity>()

        Row(
            GlanceModifier.fillMaxSize().background(background).cornerRadius(16.dp).padding(12.dp).clickable(click),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                ImageProvider(R.drawable.ic_door),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp),
                colorFilter = androidx.glance.ColorFilter.tint(textColor),
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(name, maxLines = 1, style = TextStyle(color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                if (status != null) Text(status, maxLines = 2, style = TextStyle(color = textColor, fontSize = 13.sp))
            }
        }
    }

    companion object {
        private val OPENED_GREEN = androidx.compose.ui.graphics.Color(0xFF2E7D32)
    }
}
