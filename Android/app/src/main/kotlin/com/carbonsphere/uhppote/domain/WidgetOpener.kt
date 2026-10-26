package com.carbonsphere.uhppote.domain

import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.protocol.Device
import java.io.IOException

/** The widget phase saved while a door is being opened. */
const val PHASE_OPENING = "opening"

/**
 * Whether a tap must wait for an open already running. An "opening" phase
 * older than staleMs (the process was killed mid-open) no longer blocks.
 */
fun openingInProgress(phase: String?, sinceMs: Long?, nowMs: Long, staleMs: Long = 15_000): Boolean =
    phase == PHASE_OPENING && sinceMs != null && nowMs - sinceMs in 0 until staleMs

sealed interface WidgetResult {
    data object Opened : WidgetResult
    data object Refused : WidgetResult
    data object NoReply : WidgetResult

    /** Opened after the controller was found at a new address, which should be saved. */
    data class OpenedAtNewIp(val ip: String) : WidgetResult
}

/**
 * Opens a door for the home-screen widget: at the saved IP first and, if
 * that gets no answer, once more wherever a quick search finds the same
 * controller.
 */
suspend fun openFromWidget(
    saved: SavedController,
    door: Int,
    open: suspend (ip: String) -> Boolean,
    search: suspend () -> List<Device>,
): WidgetResult {
    try {
        return if (open(saved.ip)) WidgetResult.Opened else WidgetResult.Refused
    } catch (e: IOException) {
        // No reply (or no route) at the saved address: look for the controller.
    }
    val ip = try {
        search().firstOrNull { it.serial == saved.serial }?.ip?.hostAddress
    } catch (e: IOException) {
        null
    } ?: return WidgetResult.NoReply
    return try {
        when {
            !open(ip) -> WidgetResult.Refused
            ip == saved.ip -> WidgetResult.Opened
            else -> WidgetResult.OpenedAtNewIp(ip)
        }
    } catch (e: IOException) {
        WidgetResult.NoReply
    }
}
