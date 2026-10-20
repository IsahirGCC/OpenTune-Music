/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TogetherInviteTest {
    private val card =
        TogetherGuestCard(
            host = "192.168.1.42",
            port = 42118,
            token = "abc123",
            clientId = "client-1",
            name = "Pixel de Ana & co",
        )

    @Test
    fun guestCard_roundTrip_keepsSpecialCharacters() {
        val decoded = TogetherGuestCardLink.decode(TogetherGuestCardLink.encode(card))
        assertEquals(card, decoded)
    }

    @Test
    fun guestCard_inviteUrl() {
        assertEquals("http://192.168.1.42:42118/together/invite", card.toInviteUrl())
    }

    @Test
    fun guestCard_rejectsSessionLinksAndGarbage() {
        val sessionLink = TogetherLink.encode(TogetherJoinInfo("192.168.1.20", 42117, "sid", "key"))
        assertNull(TogetherGuestCardLink.decode(sessionLink))
        assertNull(TogetherGuestCardLink.decode("hello"))
        assertNull(TogetherGuestCardLink.decode("opentune://together-invite?host=1.2.3.4&port=0&token=t&cid=c"))
        assertNull(TogetherGuestCardLink.decode("opentune://together-invite?host=1.2.3.4&port=42118&cid=c"))
    }

    @Test
    fun guestCard_blankNameFallsBack() {
        val raw = "opentune://together-invite?host=1.2.3.4&port=42118&token=t&cid=c"
        assertEquals("Guest", TogetherGuestCardLink.decode(raw)?.name)
    }

    @Test
    fun contacts_upsertReplacesByClientId() {
        val a = TogetherContact(clientId = "a", name = "A", pairKey = "k1")
        val b = TogetherContact(clientId = "b", name = "B", pairKey = "k2")
        val a2 = a.copy(name = "A2", pairKey = "k3")

        val result = TogetherContactsCodec.upsert(listOf(a, b), a2)

        assertEquals(listOf(b, a2), result)
    }

    @Test
    fun contacts_codecRoundTripAndBadInput() {
        val list = listOf(TogetherContact(clientId = "a", name = "A", pairKey = "k1", addedAtMs = 5L))
        assertEquals(list, TogetherContactsCodec.decode(TogetherContactsCodec.encode(list)))
        assertEquals(emptyList<TogetherContact>(), TogetherContactsCodec.decode("not json"))
        assertEquals(emptyList<TogetherContact>(), TogetherContactsCodec.decode(null))
    }

    @Test
    fun invite_serializationRoundTrip() {
        val invite =
            TogetherInvite(
                token = "t",
                hostClientId = "h",
                hostName = "Host",
                joinLink = TogetherLink.encode(TogetherJoinInfo("192.168.1.20", 42117, "sid", "pair")),
                pairKey = "pair",
            )
        val json = TogetherJson.json.encodeToString(TogetherInvite.serializer(), invite)
        assertEquals(invite, TogetherJson.json.decodeFromString(TogetherInvite.serializer(), json))
    }
}
