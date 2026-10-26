package com.carbonsphere.uhppote.data

import com.carbonsphere.uhppote.protocol.Card
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class CachedCard(val number: Long, val from: String, val to: String, val doors: List<Int>)

/** The last full card list read from each controller, so the Cards screen opens instantly. */
class CardCache(private val dir: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private fun file(serial: Long) = File(dir, "cards-$serial.json")

    /** The cached cards, or null if there are none or the file is unreadable. */
    suspend fun load(serial: Long): List<Card>? = withContext(io) {
        try {
            Json.decodeFromString<List<CachedCard>>(file(serial).readText())
                .map { Card(it.number, LocalDate.parse(it.from), LocalDate.parse(it.to), it.doors) }
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: DateTimeParseException) {
            null
        }
    }

    suspend fun save(serial: Long, cards: List<Card>) = withContext(io) {
        dir.mkdirs()
        val tmp = File(dir, "cards-$serial.json.tmp")
        tmp.writeText(Json.encodeToString(cards.map { CachedCard(it.number, it.from.toString(), it.to.toString(), it.doors) }))
        if (!tmp.renameTo(file(serial))) {
            file(serial).delete()
            tmp.renameTo(file(serial))
        }
    }
}
