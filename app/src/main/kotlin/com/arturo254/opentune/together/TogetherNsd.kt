/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.together

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** A LAN session announced over NSD (mDNS) by another OpenTune device. */
@Immutable
data class TogetherDiscoveredSession(
    val serviceName: String,
    val hostClientId: String,
    val hostName: String,
    val host: String,
    val port: Int,
    val sessionId: String,
)

/**
 * Announces hosted LAN sessions and discovers sessions from other devices.
 *
 * Only identifiers travel in the TXT record (host clientId, session id, name). The
 * session key is never announced: contacts join with their pair key instead, and
 * [TogetherServer] only accepts that key from the matching contact.
 */
class TogetherNsd(context: Context) {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null

    fun register(port: Int, hostName: String, hostClientId: String, sessionId: String) {
        unregister()
        val info =
            NsdServiceInfo().apply {
                serviceName = hostName.take(MAX_NAME_CHARS).ifBlank { "OpenTune" }
                serviceType = SERVICE_TYPE
                setPort(port)
                setAttribute(ATTR_VERSION, TogetherProtocolVersion.toString())
                setAttribute(ATTR_CLIENT_ID, hostClientId)
                setAttribute(ATTR_SESSION_ID, sessionId)
                setAttribute(ATTR_NAME, hostName.take(MAX_NAME_CHARS))
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Timber.tag("Together").i("nsd registered name=${serviceInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Timber.tag("Together").w("nsd registration failed code=$errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Timber.tag("Together").w(it, "nsd register threw") }
    }

    fun unregister() {
        val listener = registration ?: return
        registration = null
        runCatching { nsd.unregisterService(listener) }
    }

    /**
     * Emits the current set of resolved sessions whenever it changes. Resolution runs one
     * service at a time because older Android versions reject concurrent resolves.
     */
    fun discover(): Flow<List<TogetherDiscoveredSession>> =
        callbackFlow {
            val found = linkedMapOf<String, TogetherDiscoveredSession>()
            val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

            val resolver =
                launch {
                    for (info in toResolve) {
                        val resolved = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { resolve(info) } ?: continue
                        val session = resolved.toDiscoveredSession() ?: continue
                        val snapshot = synchronized(found) {
                            found[info.serviceName] = session
                            found.values.toList()
                        }
                        trySend(snapshot)
                    }
                }

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        toResolve.trySend(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        val snapshot = synchronized(found) {
                            if (found.remove(serviceInfo.serviceName) == null) null else found.values.toList()
                        }
                        if (snapshot != null) trySend(snapshot)
                    }

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Timber.tag("Together").w("nsd discovery failed code=$errorCode")
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                }

            trySend(emptyList())
            runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { Timber.tag("Together").w(it, "nsd discover threw") }

            awaitClose {
                resolver.cancel()
                toResolve.close()
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            val listener =
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                }
            runCatching { nsd.resolveService(info, listener) }
                .onFailure { if (cont.isActive) cont.resume(null) }
        }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.toDiscoveredSession(): TogetherDiscoveredSession? {
        val attrs = attributes ?: return null
        fun attr(key: String): String? = attrs[key]?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotBlank() }

        if (attr(ATTR_VERSION)?.toIntOrNull() != TogetherProtocolVersion) return null
        val cid = attr(ATTR_CLIENT_ID) ?: return null
        val sid = attr(ATTR_SESSION_ID) ?: return null
        val address =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                hostAddresses.firstOrNull { it is java.net.Inet4Address } ?: hostAddresses.firstOrNull()
            } else {
                host
            }
        val ip = address?.hostAddress?.takeIf { it.isNotBlank() } ?: return null
        if (port !in 1..65535) return null

        return TogetherDiscoveredSession(
            serviceName = serviceName,
            hostClientId = cid,
            hostName = attr(ATTR_NAME) ?: serviceName,
            host = ip,
            port = port,
            sessionId = sid,
        )
    }

    companion object {
        const val SERVICE_TYPE = "_opentune-tg._tcp"
        private const val ATTR_VERSION = "v"
        private const val ATTR_CLIENT_ID = "cid"
        private const val ATTR_SESSION_ID = "sid"
        private const val ATTR_NAME = "n"
        private const val MAX_NAME_CHARS = 40
        private const val RESOLVE_TIMEOUT_MS = 5000L
    }
}
