package com.carbonsphere.uhppote.net

import com.carbonsphere.uhppote.protocol.PacketLog
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LogLine(val time: LocalTime, val text: String)

/** The in-app activity log: every action and, optionally, every packet. */
class ActivityLog(private val max: Int = 2000) {
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    fun info(text: String) = add(text)

    fun ok(text: String) = add("✔ $text")

    fun error(text: String) = add("✖ $text")

    fun clear() {
        _lines.value = emptyList()
    }

    /** A PacketLog that writes packets here while enabled() is true. */
    fun packetLog(enabled: () -> Boolean) = PacketLog { dir, address, packet ->
        if (enabled()) {
            val arrow = if (dir == "RX") "←" else "→"
            add("$dir $arrow ${address.hostAddress} ${packet.hex()}")
        }
    }

    private fun add(text: String) {
        val line = LogLine(LocalTime.now(), text)
        _lines.update { (it + line).takeLast(max) }
    }
}
