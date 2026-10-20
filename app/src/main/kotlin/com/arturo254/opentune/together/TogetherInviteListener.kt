/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Small HTTP endpoint a guest opens while showing their QR. The host posts a
 * [TogetherInvite] to it after scanning; the token is single-use and expires.
 */
class TogetherInviteListener(
    private val clientId: String,
    private val displayName: String,
    private val tokenTtlMs: Long = DEFAULT_TOKEN_TTL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var engine: EmbeddedServer<*, *>? = null
    private var token: String? = null
    private var tokenExpiresAtMs: Long = 0L

    var onInvite: ((TogetherInvite) -> Unit)? = null

    /** Starts the listener and returns the card to render as a QR, or null if no port could be bound. */
    suspend fun start(hostAddress: String, preferredPort: Int = DEFAULT_PORT): TogetherGuestCard? =
        mutex.withLock {
            stopLocked()
            val newToken = UUID.randomUUID().toString().replace("-", "")
            for (port in preferredPort until preferredPort + PORT_ATTEMPTS) {
                val server =
                    runCatching {
                        embeddedServer(CIO, port = port, host = "0.0.0.0") {
                            routing {
                                post(TogetherGuestCardLink.INVITE_PATH) {
                                    val body = runCatching { call.receiveText() }.getOrDefault("")
                                    val (status, response) = handleInvite(body)
                                    call.respondText(
                                        TogetherJson.json.encodeToString(TogetherInviteResponse.serializer(), response),
                                        ContentType.Application.Json,
                                        status,
                                    )
                                }
                            }
                        }.also { it.start(wait = false) }
                    }.getOrNull() ?: continue

                engine = server
                token = newToken
                tokenExpiresAtMs = nowMs() + tokenTtlMs
                return@withLock TogetherGuestCard(
                    host = hostAddress,
                    port = port,
                    token = newToken,
                    clientId = clientId,
                    name = displayName,
                )
            }
            null
        }

    suspend fun stop() {
        mutex.withLock { stopLocked() }
    }

    private fun stopLocked() {
        token = null
        val eng = engine ?: return
        engine = null
        runCatching { eng.stop(200, 500) }
    }

    private suspend fun handleInvite(body: String): Pair<HttpStatusCode, TogetherInviteResponse> {
        val invite =
            runCatching { TogetherJson.json.decodeFromString(TogetherInvite.serializer(), body) }.getOrNull()
                ?: return HttpStatusCode.BadRequest to TogetherInviteResponse(accepted = false, error = "bad_request")

        val accepted =
            mutex.withLock {
                val expected = token
                val valid =
                    expected != null &&
                        invite.token == expected &&
                        nowMs() <= tokenExpiresAtMs &&
                        TogetherLink.decode(invite.joinLink) != null &&
                        invite.pairKey.isNotBlank()
                // Single use: once a valid invite arrives, the token is burned.
                if (valid) token = null
                valid
            }

        if (!accepted) {
            return HttpStatusCode.Forbidden to TogetherInviteResponse(accepted = false, error = "invalid_token")
        }

        onInvite?.invoke(invite)
        return HttpStatusCode.OK to
            TogetherInviteResponse(accepted = true, guestClientId = clientId, guestName = displayName)
    }

    companion object {
        const val DEFAULT_PORT = 42118
        const val PORT_ATTEMPTS = 10
        const val DEFAULT_TOKEN_TTL_MS = 5 * 60 * 1000L
    }
}
