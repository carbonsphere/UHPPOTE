package com.carbonsphere.uhppote.widget

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.carbonsphere.uhppote.data.findController
import com.carbonsphere.uhppote.data.updateControllerIp
import com.carbonsphere.uhppote.domain.WidgetResult
import com.carbonsphere.uhppote.domain.openFromWidget
import com.carbonsphere.uhppote.graph
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Opens the widget's door in the background, without launching the app. */
class OpenDoorAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, glanceId)
        if (prefs[WidgetKeys.phase] == Phase.OPENING) return
        val serial = prefs[WidgetKeys.serial] ?: return
        val door = prefs[WidgetKeys.door] ?: return
        val g = context.graph
        val saved = g.store.state.first().findController(serial) ?: return
        val name = saved.doorName(door, "Door $door")

        setPhase(context, glanceId, Phase.OPENING)
        val result = openFromWidget(
            saved, door,
            open = { ip -> g.controller(saved.copy(ip = ip)).openDoor(door) },
            search = { g.search() },
        )
        val phase = when (result) {
            WidgetResult.Opened, is WidgetResult.OpenedAtNewIp -> Phase.OPENED
            WidgetResult.Refused -> Phase.REFUSED
            WidgetResult.NoReply -> Phase.NO_REPLY
        }
        when (result) {
            WidgetResult.Opened -> g.log.ok("Widget: open $name")
            is WidgetResult.OpenedAtNewIp -> {
                g.store.update { it.updateControllerIp(serial, result.ip) }
                g.log.ok("Widget: open $name (controller found at ${result.ip})")
            }
            WidgetResult.Refused -> g.log.error("Widget: open $name: controller rejected the command")
            WidgetResult.NoReply -> g.log.error("Widget: open $name: no reply")
        }
        if (phase == Phase.OPENED) vibrate(context)
        setPhase(context, glanceId, phase)
        delay(3000)
        setPhase(context, glanceId, Phase.IDLE)
    }

    private suspend fun setPhase(context: Context, id: GlanceId, phase: String) {
        updateAppWidgetState(context, id) { it[WidgetKeys.phase] = phase }
        DoorWidget().update(context, id)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
