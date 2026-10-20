/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.util.concurrent.TimeUnit

sealed interface TogetherInviteResult {
    data class Accepted(val guestClientId: String, val guestName: String) : TogetherInviteResult
    data object Rejected : TogetherInviteResult
    data class Unreachable(val throwable: Throwable) : TogetherInviteResult
}

/** Host side: delivers a [TogetherInvite] to the guest whose QR was scanned. */
object TogetherInviteSender {
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(5, TimeUnit.SECONDS)
                    writeTimeout(5, TimeUnit.SECONDS)
                }
            }
        }
    }

    suspend fun send(card: TogetherGuestCard, invite: TogetherInvite): TogetherInviteResult =
        runCatching {
            val response =
                client.post(card.toInviteUrl()) {
                    contentType(ContentType.Application.Json)
                    setBody(TogetherJson.json.encodeToString(TogetherInvite.serializer(), invite))
                }
            val parsed =
                runCatching {
                    TogetherJson.json.decodeFromString(TogetherInviteResponse.serializer(), response.bodyAsText())
                }.getOrNull()
            if (parsed?.accepted == true) {
                TogetherInviteResult.Accepted(
                    guestClientId = parsed.guestClientId ?: card.clientId,
                    guestName = parsed.guestName ?: card.name,
                )
            } else {
                TogetherInviteResult.Rejected
            }
        }.getOrElse { TogetherInviteResult.Unreachable(it) }
}
