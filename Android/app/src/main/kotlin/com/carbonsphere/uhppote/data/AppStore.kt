package com.carbonsphere.uhppote.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Where the app state lives. */
interface AppStore {
    val state: Flow<AppState>
    suspend fun update(transform: (AppState) -> AppState)
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun encodeAppState(s: AppState): String = json.encodeToString(AppState.serializer(), s)

/** Decodes the stored state; anything unreadable gives an empty state. */
fun decodeAppState(text: String): AppState = try {
    json.decodeFromString(AppState.serializer(), text)
} catch (e: SerializationException) {
    AppState()
} catch (e: IllegalArgumentException) {
    AppState()
}

private object AppStateSerializer : Serializer<AppState> {
    override val defaultValue = AppState()

    override suspend fun readFrom(input: InputStream): AppState = try {
        decodeAppState(input.readBytes().decodeToString())
    } catch (e: Exception) {
        throw CorruptionException("Cannot read app state", e)
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(encodeAppState(t).encodeToByteArray())
    }
}

/** AppStore backed by DataStore in the file app_state.json. */
class DataStoreAppStore(context: Context) : AppStore {
    private val store: DataStore<AppState> = DataStoreFactory.create(
        serializer = AppStateSerializer,
        produceFile = { context.dataStoreFile("app_state.json") },
    )

    override val state: Flow<AppState> = store.data

    override suspend fun update(transform: (AppState) -> AppState) {
        store.updateData(transform)
    }
}
