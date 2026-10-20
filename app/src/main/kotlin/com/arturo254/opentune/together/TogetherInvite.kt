/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable

/**
 * What a guest shows as a QR so that a host can invite them by scanning it.
 *
 * [host]/[port] point to the guest's temporary invite listener ([TogetherInviteListener]),
 * [token] is a one-time secret that the host must echo back, and [clientId]/[name]
 * identify the guest so the host can keep them as a contact.
 */
data class TogetherGuestCard(
    val host: String,
    val port: Int,
    val token: String,
    val clientId: String,
    val name: String,
) {
    fun toInviteUrl(): String = "http://$host:$port${TogetherGuestCardLink.INVITE_PATH}"
}

/**
 * Sent by the host to the guest's invite listener after scanning the guest QR.
 *
 * [pairKey] is a long-lived secret shared by both devices from now on. A contact
 * can later join a session discovered on the local network using it instead of
 * the session key, so the session key is never broadcast.
 */
@Serializable
data class TogetherInvite(
    val token: String,
    val hostClientId: String,
    val hostName: String,
    val joinLink: String,
    val pairKey: String,
)

@Serializable
data class TogetherInviteResponse(
    val accepted: Boolean,
    val guestClientId: String? = null,
    val guestName: String? = null,
    val error: String? = null,
)

object TogetherGuestCardLink {
    const val INVITE_PATH = "/together/invite"
    private const val SCHEME = "opentune"
    private const val AUTHORITY = "together-invite"

    fun encode(card: TogetherGuestCard): String {
        val charset = StandardCharsets.UTF_8.name()
        val q =
            listOf(
                "host" to card.host,
                "port" to card.port.toString(),
                "token" to card.token,
                "cid" to card.clientId,
                "name" to card.name,
            ).joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, charset)}=${URLEncoder.encode(v, charset)}"
            }
        return "$SCHEME://$AUTHORITY?$q"
    }

    fun decode(raw: String): TogetherGuestCard? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        val authority = uri.host?.lowercase() ?: uri.authority?.lowercase() ?: return null
        if (authority != AUTHORITY) return null

        val params = parseQuery(uri.rawQuery)
        val host = params["host"]?.trim().orEmpty()
        val port = params["port"]?.toIntOrNull() ?: return null
        val token = params["token"]?.trim().orEmpty()
        val cid = params["cid"]?.trim().orEmpty()
        val name = params["name"]?.trim().orEmpty().ifBlank { "Guest" }

        if (host.isBlank() || token.isBlank() || cid.isBlank()) return null
        if (port !in 1..65535) return null

        return TogetherGuestCard(host = host, port = port, token = token, clientId = cid, name = name)
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val charset = StandardCharsets.UTF_8.name()
        return rawQuery.split("&")
            .asSequence()
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = URLDecoder.decode(pair.substring(0, idx), charset)
                val v = URLDecoder.decode(pair.substring(idx + 1), charset)
                k to v
            }
            .toMap()
    }
}
