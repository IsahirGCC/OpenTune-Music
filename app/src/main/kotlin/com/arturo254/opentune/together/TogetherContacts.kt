/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.arturo254.opentune.constants.TogetherContactsKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * A device paired by scanning a QR. Both sides store each other with the same [pairKey],
 * so either one can host and the other can join from the local network without a link.
 */
@Serializable
@Immutable
data class TogetherContact(
    val clientId: String,
    val name: String,
    val pairKey: String,
    val addedAtMs: Long = 0L,
)

object TogetherContactsCodec {
    private val serializer = ListSerializer(TogetherContact.serializer())

    fun decode(raw: String?): List<TogetherContact> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { TogetherJson.json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun encode(contacts: List<TogetherContact>): String = TogetherJson.json.encodeToString(serializer, contacts)

    /** Inserts or replaces by clientId, keeping the most recent pairing. */
    fun upsert(contacts: List<TogetherContact>, contact: TogetherContact): List<TogetherContact> =
        contacts.filterNot { it.clientId == contact.clientId } + contact
}

class TogetherContactsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val contacts: Flow<List<TogetherContact>> =
        dataStore.data
            .map { TogetherContactsCodec.decode(it[TogetherContactsKey]) }
            .distinctUntilChanged()

    suspend fun current(): List<TogetherContact> = contacts.first()

    suspend fun upsert(contact: TogetherContact) {
        dataStore.edit { prefs ->
            val list = TogetherContactsCodec.decode(prefs[TogetherContactsKey])
            prefs[TogetherContactsKey] = TogetherContactsCodec.encode(TogetherContactsCodec.upsert(list, contact))
        }
    }

    suspend fun remove(clientId: String) {
        dataStore.edit { prefs ->
            val list = TogetherContactsCodec.decode(prefs[TogetherContactsKey])
            prefs[TogetherContactsKey] = TogetherContactsCodec.encode(list.filterNot { it.clientId == clientId })
        }
    }
}
