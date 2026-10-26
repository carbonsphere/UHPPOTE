package com.carbonsphere.uhppote.ui

import com.carbonsphere.uhppote.net.ActivityLog
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Reported when the controller answers but rejects a command. */
class ControllerRefused : IOException("Controller rejected the command")

/** Throws [ControllerRefused] if the controller answered "no". */
fun Boolean.orRefused() {
    if (!this) throw ControllerRefused()
}

/**
 * Runs one controller action in this scope (a ViewModel's viewModelScope):
 * logs "✔ desc" or "✖ desc: reason", and hands the result to onResult, or
 * the reason to onError, only if the controller with this serial is still
 * the current one. A reply that arrives after the manager switched
 * controller is not shown under the new one.
 */
fun <T> CoroutineScope.runOn(
    serial: Long,
    desc: String,
    log: ActivityLog,
    current: () -> Long?,
    block: suspend () -> T,
    onResult: (T) -> Unit,
    onError: (String) -> Unit = {},
): Job = launch {
    try {
        val r = block()
        log.ok(desc)
        if (current() == serial) onResult(r)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        fail(desc, e.message ?: e.toString(), log, serial, current, onError)
    } catch (e: IllegalArgumentException) {
        fail(desc, e.message ?: e.toString(), log, serial, current, onError)
    }
}

private fun fail(desc: String, reason: String, log: ActivityLog, serial: Long, current: () -> Long?, onError: (String) -> Unit) {
    log.error("$desc: $reason")
    if (current() == serial) onError(reason)
}
