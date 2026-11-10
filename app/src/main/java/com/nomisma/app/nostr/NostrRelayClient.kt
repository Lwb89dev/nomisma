package com.nomisma.app.nostr

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface RelayMessage {
    data object Connected : RelayMessage
    data class Event(val subscriptionId: String, val event: NostrEvent) : RelayMessage
    data class Ack(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage
    data class EndOfStoredEvents(val subscriptionId: String) : RelayMessage
    data class Notice(val message: String) : RelayMessage
    data class ProtocolError(val message: String) : RelayMessage
    data class Failure(val message: String) : RelayMessage
    /** NIP-42: the relay wants proof of the reader's key before serving some events. */
    data class AuthChallenge(val challenge: String) : RelayMessage
    /** The relay ended a subscription, e.g. "auth-required: …". */
    data class SubscriptionClosed(val subscriptionId: String, val message: String) : RelayMessage
    data object Closed : RelayMessage
}

object RelayReconnectPolicy {
    private const val BASE_DELAY_MILLIS = 1_000L
    private const val MAX_DELAY_MILLIS = 30_000L

    fun delayMillis(attempt: Int): Long {
        require(attempt > 0) { "Reconnect attempt must be positive" }
        val exponent = (attempt - 1).coerceAtMost(5)
        return (BASE_DELAY_MILLIS shl exponent).coerceAtMost(MAX_DELAY_MILLIS)
    }
}

enum class RelayConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class NostrRelayClient(
    val relayUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // Without pings a connection dropped by the network stays "open" forever: nothing
        // arrives, publishes time out, and no reconnect ever starts.
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    /**
     * Unbounded on purpose: each inbound gift wrap is decrypted through Amber, so the collector
     * is slower than the relay during the backlog. A bounded buffer made tryEmit drop events and
     * the EOSE, which left the session stuck in "recupero storico" and never published.
     */
    private val _messages = MutableSharedFlow<RelayMessage>(extraBufferCapacity = Int.MAX_VALUE)
    val messages: SharedFlow<RelayMessage> = _messages.asSharedFlow()
    private val _connectionState = MutableStateFlow(RelayConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RelayConnectionState> = _connectionState.asStateFlow()
    private var socket: WebSocket? = null
    private val pendingAcks = ConcurrentHashMap<String, CompletableDeferred<RelayMessage.Ack>>()

    init {
        require(RelayTransportPolicy.isSupported(relayUrl)) {
            "Relay URL must use wss:// or the authorized LAN endpoint"
        }
    }

    fun connect() {
        if (socket != null) return
        _connectionState.value = RelayConnectionState.CONNECTING
        val request = Request.Builder().url(relayUrl).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                SyncLog.d("socket open")
                _connectionState.value = RelayConnectionState.CONNECTED
                _messages.tryEmit(RelayMessage.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.length > NostrLimits.MAX_RELAY_MESSAGE_LENGTH) {
                    _messages.tryEmit(RelayMessage.ProtocolError("Relay message exceeds the supported size"))
                    return
                }
                parseMessage(text)?.let { message ->
                    if (message is RelayMessage.Ack) {
                        pendingAcks.remove(message.eventId)?.complete(message)
                    }
                    trace(message)
                    if (!_messages.tryEmit(message)) SyncLog.w("dropped ${message.javaClass.simpleName}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                SyncLog.w("socket failure", t)
                socket = null
                _connectionState.value = RelayConnectionState.DISCONNECTED
                failPendingAcks(t)
                _messages.tryEmit(RelayMessage.Failure(t.message ?: "Relay connection failed"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                SyncLog.d("socket closed $code $reason")
                socket = null
                _connectionState.value = RelayConnectionState.DISCONNECTED
                failPendingAcks(IllegalStateException("Relay closed: $reason"))
                _messages.tryEmit(RelayMessage.Closed)
            }
        })
    }

    suspend fun connectAndAwait(timeoutMillis: Long = 10_000L) {
        if (_connectionState.value != RelayConnectionState.CONNECTED) connect()
        withTimeout(timeoutMillis) {
            _connectionState.filter { it == RelayConnectionState.CONNECTED }.first()
        }
    }

    fun subscribe(subscriptionId: String = UUID.randomUUID().toString(), filter: RelayFilter): String {
        SyncLog.d("-> REQ kinds=${filter.kinds} since=${filter.since} limit=${filter.limit}")
        send(JSONArray().put("REQ").put(subscriptionId).put(filter.toJson()).toString())
        return subscriptionId
    }

    fun publish(event: NostrEvent) {
        send(JSONArray().put("EVENT").put(JSONObject(event.toJson())).toString())
    }

    suspend fun publishAndAwaitAck(
        event: NostrEvent,
        timeoutMillis: Long = 10_000L
    ): RelayMessage.Ack = sendAndAwaitAck(event, "EVENT", timeoutMillis)

    /** Answers a NIP-42 challenge with a signed kind-22242 event. */
    suspend fun authenticate(event: NostrEvent, timeoutMillis: Long = 10_000L): RelayMessage.Ack =
        sendAndAwaitAck(event, "AUTH", timeoutMillis)

    private suspend fun sendAndAwaitAck(
        event: NostrEvent,
        verb: String,
        timeoutMillis: Long
    ): RelayMessage.Ack {
        val deferred = CompletableDeferred<RelayMessage.Ack>()
        check(pendingAcks.putIfAbsent(event.id, deferred) == null) {
            "An acknowledgement is already pending for ${event.id}"
        }
        try {
            SyncLog.d("-> $verb kind=${event.kind} id=${SyncLog.shortId(event.id)}")
            send(JSONArray().put(verb).put(JSONObject(event.toJson())).toString())
            val acknowledgement = withTimeout(timeoutMillis) { deferred.await() }
            check(acknowledgement.accepted) {
                "Relay rejected ${event.id}: ${acknowledgement.message.ifBlank { "no reason provided" }}"
            }
            return acknowledgement
        } finally {
            pendingAcks.remove(event.id, deferred)
        }
    }

    fun close() {
        socket?.close(1000, "client close")
        socket = null
        _connectionState.value = RelayConnectionState.DISCONNECTED
        failPendingAcks(IllegalStateException("Relay closed by client"))
    }

    private fun send(message: String) {
        check(socket != null) { "Relay is not connected" }
        check(socket?.send(message) == true) { "Relay rejected outgoing message" }
    }

    private fun trace(message: RelayMessage) {
        when (message) {
            is RelayMessage.Event -> SyncLog.d("<- EVENT kind=${message.event.kind} id=${SyncLog.shortId(message.event.id)}")
            is RelayMessage.Ack -> SyncLog.d("<- OK id=${SyncLog.shortId(message.eventId)} accepted=${message.accepted} ${message.message}")
            is RelayMessage.EndOfStoredEvents -> SyncLog.d("<- EOSE")
            is RelayMessage.Notice -> SyncLog.d("<- NOTICE ${message.message}")
            is RelayMessage.AuthChallenge -> SyncLog.d("<- AUTH")
            is RelayMessage.SubscriptionClosed -> SyncLog.d("<- CLOSED ${message.message}")
            is RelayMessage.ProtocolError -> SyncLog.w("<- protocol error ${message.message}")
            else -> Unit
        }
    }

    private fun failPendingAcks(cause: Throwable) {
        pendingAcks.values.forEach { it.completeExceptionally(cause) }
        pendingAcks.clear()
    }

    private fun parseMessage(raw: String): RelayMessage? = runCatching {
        val message = JSONArray(raw)
        when (message.optString(0)) {
            "EVENT" -> RelayMessage.Event(message.getString(1), NostrEvent.fromJson(message.getJSONObject(2).toString()))
            "OK" -> RelayMessage.Ack(message.getString(1), message.getBoolean(2), message.optString(3))
            "EOSE" -> RelayMessage.EndOfStoredEvents(message.getString(1))
            "NOTICE" -> RelayMessage.Notice(message.optString(1))
            "AUTH" -> RelayMessage.AuthChallenge(message.getString(1))
            "CLOSED" -> RelayMessage.SubscriptionClosed(message.getString(1), message.optString(2))
            else -> null
        }
    }.getOrElse { RelayMessage.ProtocolError("Malformed relay message: ${it.message}") }
}
