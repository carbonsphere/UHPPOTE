package com.carbonsphere.uhppote

import android.content.Context
import com.carbonsphere.uhppote.data.AppStore
import com.carbonsphere.uhppote.data.CardCache
import com.carbonsphere.uhppote.data.DataStoreAppStore
import com.carbonsphere.uhppote.data.SavedController
import com.carbonsphere.uhppote.net.ActivityLog
import com.carbonsphere.uhppote.net.WifiNetwork
import com.carbonsphere.uhppote.net.parseIpv4
import com.carbonsphere.uhppote.protocol.Controller
import com.carbonsphere.uhppote.protocol.ControllerApi
import com.carbonsphere.uhppote.protocol.Device
import com.carbonsphere.uhppote.protocol.search as searchNetwork
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The app's long-lived objects, shared by the activity and the widget. */
class AppGraph(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store: AppStore = DataStoreAppStore(context.applicationContext)
    val wifi = WifiNetwork(context)
    val log = ActivityLog()
    val cardCache = CardCache(File(context.applicationContext.filesDir, "cards"))

    @Volatile
    private var rawPackets = false

    init {
        scope.launch { store.state.collect { rawPackets = it.showRawPackets } }
    }

    private val packetLog = log.packetLog { rawPackets }

    /** A client for a saved controller, bound to Wi-Fi and logging packets. */
    fun controller(saved: SavedController): ControllerApi {
        val address = parseIpv4(saved.ip) ?: InetAddress.getByName("0.0.0.0")
        return Controller(saved.serial, address, sockets = wifi.socketFactory(), log = packetLog)
    }

    /** Searches the Wi-Fi network, or one address if target is given. */
    suspend fun search(target: InetAddress? = null): List<Device> = wifi.withMulticastLock {
        searchNetwork(
            targets = target?.let { listOf(it) } ?: wifi.broadcastTargets(),
            sockets = wifi.socketFactory(),
            log = packetLog,
        )
    }
}
