// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package features.outbound

import app.OutboundState
import engine.singbox.config.SingBoxJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds

internal fun OutboundState.pingHostOrNull(): String? {
    val outbound = runCatching {
        SingBoxJson.parseToJsonElement(json) as? JsonObject
    }.getOrNull() ?: return null
    var server = (outbound["server"] as? JsonPrimitive)?.contentOrNull
    if (server.isNullOrBlank()) {
        val firstPeer = (outbound["peers"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (firstPeer != null) {
            server = (firstPeer["address"] as? JsonPrimitive)?.contentOrNull
                ?: (firstPeer["server"] as? JsonPrimitive)?.contentOrNull
        }
    }
    return server
        ?.trim()
        ?.removeSurrounding("[", "]")
        ?.takeIf(String::isNotEmpty)
}

internal fun OutboundState.pingPortOrNull(): Int? {
    val outbound = runCatching {
        SingBoxJson.parseToJsonElement(json) as? JsonObject
    }.getOrNull() ?: return null
    var port = (outbound["server_port"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        ?: (outbound["port"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    if (port == null) {
        val firstPeer = (outbound["peers"] as? JsonArray)?.firstOrNull() as? JsonObject
        if (firstPeer != null) {
            port = (firstPeer["port"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: (firstPeer["server_port"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        }
    }
    return port?.takeIf { it in 1..65535 }
}

internal fun parsePingMillis(output: String): Long? {
    return PingTimeRegex.find(output)
        ?.groupValues
        ?.getOrNull(1)
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.roundToLong()
}

internal fun buildPingCommand(host: String): List<String> = listOf(
    if (':' in host) Ping6Executable else PingExecutable,
    "-c",
    "1",
    "-W",
    PingTimeoutSeconds.toString(),
    host,
)

internal suspend fun pingOrFailure(ping: suspend () -> Long): Long {
    return try {
        ping()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        FailedPingMillis
    }
}

internal fun interface OutboundPinger {
    suspend fun ping(outbound: OutboundState): Long
}

internal class AndroidOutboundPinger : OutboundPinger {
    override suspend fun ping(outbound: OutboundState): Long {
        val host = outbound.pingHostOrNull() ?: return FailedPingMillis
        val port = outbound.pingPortOrNull()

        // 1. Prefer real TCP connect test if port is known (accurately reflects proxy port reachability)
        if (port != null) {
            val tcpElapsed = tcpPingOnce(host, port)
            if (tcpElapsed >= 0L) {
                return tcpElapsed
            }
        }

        // 2. Fallback to ICMP ping
        var bestMillis = FailedPingMillis
        repeat(PingAttempts) {
            currentCoroutineContext().ensureActive()
            val elapsedMillis = pingOnce(host)
            if (elapsedMillis >= 0L && (bestMillis !in 0L..elapsedMillis)) {
                bestMillis = elapsedMillis
            }
        }
        return bestMillis
    }

    private suspend fun tcpPingOnce(host: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 2500)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                socket.close()
                elapsed.coerceAtLeast(1L)
            }.getOrDefault(FailedPingMillis)
        }
    }

    private suspend fun pingOnce(host: String): Long {
        return withTimeoutOrNull(PingProcessTimeoutMillis.milliseconds) {
            withContext(Dispatchers.IO) {
                var process: Process? = null
                try {
                    val startedAtNanos = System.nanoTime()
                    val activeProcess = ProcessBuilder(buildPingCommand(host))
                        .redirectErrorStream(true)
                        .start()
                        .also { startedProcess -> process = startedProcess }
                    runInterruptible { activeProcess.waitFor() }
                    val output = activeProcess.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                    if (activeProcess.exitValue() == 0) {
                        parsePingMillis(output)
                            ?: TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
                    } else {
                        FailedPingMillis
                    }
                } finally {
                    process?.destroy()
                }
            }
        } ?: FailedPingMillis
    }
}

internal const val FailedPingMillis = -1L
private const val PingAttempts = 2
private const val PingTimeoutSeconds = 3L
private const val PingProcessTimeoutMillis = (PingTimeoutSeconds + 1L) * 1_000L
private const val PingExecutable = "/system/bin/ping"
private const val Ping6Executable = "/system/bin/ping6"
private val PingTimeRegex = Regex("""time[=<]\s*(\d+(?:[.,]\d+)?)\s*ms""", RegexOption.IGNORE_CASE)
