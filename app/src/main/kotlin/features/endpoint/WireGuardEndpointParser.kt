// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package features.endpoint

import engine.singbox.config.SingBoxJson
import features.importing.ImportIssue
import features.importing.ImportIssueReason
import features.importing.ImportIssueSeverity
import features.importing.ImportLimitException
import features.importing.ImportMutation
import features.importing.ImportOutcome
import features.importing.ImportStage
import features.importing.MaxImportBytes
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object WireGuardEndpointParser {
    fun parseUrlOutcomeOrNull(content: String): RecognizedEndpointImport? {
        val normalized = decodeWrappedUrlOrSelf(content.trim()) ?: content.trim()
        val scheme = normalized.substringBefore(':', missingDelimiterValue = "").lowercase()
        android.util.Log.d("IQ_TUNNEL", "WireGuardEndpointParser.parseUrlOutcomeOrNull: scheme=$scheme")
        if (scheme !in RecognizedWireGuardSchemes) return null
        if (scheme in RejectedWireGuardSchemes) {
            return RecognizedEndpointImport(
                wireGuardFailure(
                    format = EndpointImportFormat.WIREGUARD_URL,
                    reason = ImportIssueReason.UNSUPPORTED_TYPE,
                    message = "AmneziaWG and WARP URLs are not supported",
                ),
            )
        }
        return RecognizedEndpointImport(
            runCatching { parseWireGuardUrl(normalized) }
                .onFailure { error ->
                    android.util.Log.e("IQ_TUNNEL", "parseWireGuardUrl failed: ${error.message}", error)
                }
                .getOrElse { error ->
                    wireGuardFailure(
                        format = EndpointImportFormat.WIREGUARD_URL,
                        reason = if (error is ImportLimitException) {
                            error.reason
                        } else {
                            ImportIssueReason.INVALID_FIELD
                        },
                        message = error.message ?: "Invalid WireGuard URL",
                    )
                },
        )
    }

    fun parseConfigOutcomeOrNull(content: String): RecognizedEndpointImport? {
        if (
            !content.lineSequence().any { line ->
                line.trim().equals("[Interface]", ignoreCase = true)
            }
        ) {
            return null
        }
        return RecognizedEndpointImport(
            runCatching { parseWireGuardConfig(content) }
                .getOrElse { error ->
                    wireGuardFailure(
                        format = EndpointImportFormat.WIREGUARD_CONFIG,
                        reason = ImportIssueReason.INVALID_FIELD,
                        message = error.message ?: "Invalid WireGuard configuration",
                    )
                },
        )
    }

    private fun parseWireGuardUrl(content: String): ImportOutcome<ImportedSingBoxEndpoint> {
        val trimmed = content.trim()
        val fragment = if ('#' in trimmed) {
            val raw = trimmed.substringAfter('#')
            runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw).trim()
        } else {
            ""
        }
        val withoutFragment = trimmed.substringBefore('#')
        val queryPart = if ('?' in withoutFragment) withoutFragment.substringAfter('?') else ""
        val authorityPart = withoutFragment.substringBefore('?').substringAfter("://").trimEnd('/')

        val query = parseQuery(queryPart)

        // Split userInfo and hostPort: authority might be "BASE64KEY@HOST:PORT" or "PRIV:PUB@HOST:PORT"
        val (authorityUserInfo, authorityHostPort) = if ('@' in authorityPart) {
            authorityPart.substringBeforeLast('@') to authorityPart.substringAfterLast('@')
        } else {
            null to authorityPart
        }

        val decodedUserInfo = authorityUserInfo?.let { raw ->
            if ('%' in raw) {
                runCatching {
                    URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8.name())
                }.getOrDefault(raw).trim()
            } else {
                raw.trim()
            }
        }
        val userInfoPart1 = decodedUserInfo?.substringBefore(':')?.trim()?.takeIf(String::isNotBlank)
        val userInfoPart2 = if (decodedUserInfo != null && ':' in decodedUserInfo) {
            decodedUserInfo.substringAfter(':').trim().takeIf(String::isNotBlank)
        } else {
            null
        }

        val parsedAuthority = parseHostPort(authorityHostPort)
        val hostFromQuery = query.firstValue("endpoint", "server", "host")?.let { parseHostPort(it).first }
        val portFromQuery = query.firstValue("endpoint", "server", "host", "port")?.let {
            it.toIntOrNull() ?: parseHostPort(it).second
        }

        val host = (hostFromQuery ?: parsedAuthority.first).trim().takeIf(String::isNotBlank)
            ?: error("WireGuard peer host is required")
        val port = portFromQuery ?: parsedAuthority.second

        val privateKeyFromQuery = query.firstValue(
            "privatekey", "private_key", "private-key", "pk", "priv", "private", "secretkey", "secret_key", "secret-key", "sk", "client_key", "client_private_key"
        )
        val publicKeyFromQuery = query.firstValue(
            "publickey", "public_key", "public-key", "peerpublickey", "peer_public_key", "peer_key", "peerkey",
            "pub", "peerpub", "serverkey", "server_key", "serverpublickey", "server_public_key", "server_pub", "pubkey", "peer"
        )

        val resolvedPrivateKey = privateKeyFromQuery
            ?: (if (publicKeyFromQuery != null || userInfoPart2 != null) userInfoPart1 else null)
            ?: (if (userInfoPart1 != null && userInfoPart1.length in 42..45) userInfoPart1 else null)

        val resolvedPublicKey = publicKeyFromQuery
            ?: userInfoPart2
            ?: (if (privateKeyFromQuery != null && userInfoPart1 != null && userInfoPart1 != resolvedPrivateKey) userInfoPart1 else null)

        val privateKey = sanitizeWireGuardKey(resolvedPrivateKey)
            ?: generateRandomWireGuardKey()

        val publicKey = sanitizeWireGuardKey(resolvedPublicKey)
            ?: error("WireGuard peer public key is required")

        val rawAddresses = query.values(
            "address", "ip", "ips", "localaddress", "local_address", "client_ip", "clientip", "interface_ip"
        )
            .flatMap(::splitCommaValues)
            .map(::normalizeToCidr)
            .filter(String::isNotBlank)

        val addresses = if (rawAddresses.isNotEmpty()) rawAddresses else listOf("10.0.0.2/32")

        val rawAllowedIps = query.values("allowedips", "allowed_ips", "allowed")
            .flatMap(::splitCommaValues)
            .map(::normalizeToCidr)
            .filter(String::isNotBlank)

        val allowedIps = if (rawAllowedIps.isNotEmpty()) rawAllowedIps else DefaultAllowedIps

        val preSharedKey = sanitizeWireGuardKey(
            query.firstValue("presharedkey", "preshared_key", "psk", "preshared")
        )
        val keepalive = query.firstValue("keepalive", "persistentkeepalive", "persistent_keepalive")
            ?.toIntOrNull()?.coerceAtLeast(0)
        val mtu = query.firstValue("mtu")?.toIntOrNull()?.coerceIn(1, 65535)
        val listenPort = query.firstValue("listenport", "listen_port")?.toIntOrNull()?.coerceIn(1, 65535)
        val workers = query.firstValue("workers")?.toIntOrNull()?.coerceAtLeast(1)
        val reserved = query.firstValue("reserved")?.let { runCatching { parseReservedBytes(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

        val remarks = fragment.ifBlank {
            query.firstValue("name", "remarks", "title", "tag").orEmpty()
        }.ifBlank {
            "WireGuard ($host)"
        }

        return singleWireGuardOutcome(
            format = EndpointImportFormat.WIREGUARD_URL,
            remarks = remarks,
            addresses = addresses,
            privateKey = privateKey.trim(),
            listenPort = listenPort,
            mtu = mtu,
            workers = workers,
            peers = listOf(
                WireGuardPeer(
                    address = host,
                    port = port,
                    publicKey = publicKey.trim(),
                    preSharedKey = preSharedKey?.trim(),
                    allowedIps = allowedIps,
                    keepalive = keepalive,
                    reserved = reserved,
                ),
            ),
        )
    }

    private fun parseWireGuardConfig(content: String): ImportOutcome<ImportedSingBoxEndpoint> {
        val interfaceValues = linkedMapOf<String, MutableList<String>>()
        val peerValues = mutableListOf<MutableMap<String, MutableList<String>>>()
        val mutations = mutableListOf<ImportMutation>()
        val ignoredOptions = linkedSetOf<String>()
        var section: String? = null
        var interfaceCount = 0
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').substringBefore(';').trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                when (section) {
                    "interface" -> {
                        interfaceCount += 1
                    }
                    "peer" -> peerValues += linkedMapOf()
                    else -> ignoredOptions += "section_$section"
                }
                return@forEach
            }
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (value.isBlank()) return@forEach
            when (section) {
                "interface" -> {
                    when (key) {
                        "address", "privatekey", "listenport", "mtu" ->
                            interfaceValues.getOrPut(key) { mutableListOf() } += value
                        "dns", "table", "saveconfig" -> ignoredOptions += key
                        "preup", "postup", "predown", "postdown" -> ignoredOptions += key
                        else -> ignoredOptions += key
                    }
                }
                "peer" -> {
                    if (peerValues.isEmpty()) peerValues += linkedMapOf()
                    when (key) {
                        "endpoint", "publickey", "presharedkey", "allowedips",
                        "persistentkeepalive", "reserved",
                        -> peerValues.last().getOrPut(key) { mutableListOf() } += value
                        else -> ignoredOptions += key
                    }
                }
                else -> {
                    // ignore options before section
                }
            }
        }
        val rawPrivateKey = interfaceValues.singleValue("privatekey", "WireGuard private key")
        val privateKey = sanitizeWireGuardKey(rawPrivateKey) ?: rawPrivateKey
        requireWireGuardKey(privateKey, "WireGuard private key")
        val addresses = interfaceValues["address"].orEmpty().flatMap(::splitCommaValues).map(::normalizeToCidr)
        val finalAddresses = if (addresses.isNotEmpty()) addresses else listOf("10.0.0.2/32")
        finalAddresses.forEach(::requireCidr)
        require(peerValues.isNotEmpty()) { "At least one WireGuard Peer is required" }
        val peers = peerValues.map { values ->
            val endpointStr = values.singleValue("endpoint", "WireGuard peer endpoint")
            val endpoint = parseHostPort(endpointStr)
            val rawPublicKey = values.singleValue("publickey", "WireGuard peer public key")
            val publicKey = sanitizeWireGuardKey(rawPublicKey) ?: rawPublicKey
            requireWireGuardKey(publicKey, "WireGuard peer public key")
            val rawAllowedIps = values["allowedips"].orEmpty().flatMap(::splitCommaValues).map(::normalizeToCidr)
            val allowedIps = if (rawAllowedIps.isNotEmpty()) rawAllowedIps else DefaultAllowedIps
            allowedIps.forEach(::requireCidr)
            val psk = values.optionalSingleValue("presharedkey")?.let { sanitizeWireGuardKey(it) ?: it }
            psk?.let { requireWireGuardKey(it, "WireGuard pre-shared key") }
            WireGuardPeer(
                address = endpoint.first,
                port = endpoint.second,
                publicKey = publicKey,
                preSharedKey = psk,
                allowedIps = allowedIps,
                keepalive = values.optionalSingleValue("persistentkeepalive")
                    ?.requireIntIn("WireGuard keepalive", 0, Int.MAX_VALUE),
                reserved = values.optionalSingleValue("reserved")?.let(::parseReservedBytes)?.takeIf { it.isNotEmpty() },
            )
        }
        mutations += ignoredEndpointOptionMutations("WireGuard", ignoredOptions)
        return singleWireGuardOutcome(
            format = EndpointImportFormat.WIREGUARD_CONFIG,
            remarks = "WireGuard Config",
            addresses = finalAddresses,
            privateKey = privateKey,
            listenPort = interfaceValues.optionalSingleValue("listenport")
                ?.requireIntIn("WireGuard listen port", 1, 65_535),
            mtu = interfaceValues.optionalSingleValue("mtu")
                ?.requireIntIn("WireGuard MTU", 1, 65_535),
            workers = null,
            peers = peers,
            mutations = mutations,
        )
    }
}

private fun singleWireGuardOutcome(
    format: EndpointImportFormat,
    remarks: String,
    addresses: List<String>,
    privateKey: String,
    listenPort: Int?,
    mtu: Int?,
    workers: Int?,
    peers: List<WireGuardPeer>,
    mutations: List<ImportMutation> = emptyList(),
): ImportOutcome<ImportedSingBoxEndpoint> {
    val primaryPeer = peers.firstOrNull()
    val json = buildJsonObject {
        put("type", "wireguard")
        put("system", false)
        primaryPeer?.let { peer ->
            put("server", peer.address)
            put("server_port", peer.port)
            put("peer_public_key", peer.publicKey)
            peer.preSharedKey?.let { psk -> put("pre_shared_key", psk) }
            peer.reserved?.let { r -> put("reserved", JsonArray(r.map(::JsonPrimitive))) }
        }
        put("address", JsonArray(addresses.map(::JsonPrimitive)))
        put("local_address", JsonArray(addresses.map(::JsonPrimitive)))
        put("private_key", privateKey)
        listenPort?.let { put("listen_port", it) }
        mtu?.let { put("mtu", it) }
        workers?.let { put("workers", it) }
        put(
            "peers",
            buildJsonArray {
                peers.forEach { peer ->
                    add(
                        buildJsonObject {
                            put("address", peer.address)
                            put("port", peer.port)
                            put("public_key", peer.publicKey)
                            peer.preSharedKey?.let { put("pre_shared_key", it) }
                            put(
                                "allowed_ips",
                                JsonArray(peer.allowedIps.map(::JsonPrimitive)),
                            )
                            peer.keepalive?.let {
                                put("persistent_keepalive_interval", it)
                            }
                            peer.reserved?.let {
                                put("reserved", JsonArray(it.map(::JsonPrimitive)))
                            }
                        },
                    )
                }
            },
        )
    }
    return ImportOutcome(
        format = format,
        detectedCount = 1,
        accepted = listOf(
            ImportedSingBoxEndpoint(
                sourceIndex = 0,
                remarks = remarks,
                type = "wireguard",
                json = SingBoxJson.encodeToString(JsonObject.serializer(), json),
            ),
        ),
        mutations = mutations,
    )
}

private fun wireGuardFailure(
    format: EndpointImportFormat,
    reason: ImportIssueReason,
    message: String,
) = ImportOutcome<ImportedSingBoxEndpoint>(
    format = format,
    detectedCount = 1,
    accepted = emptyList(),
    issues = listOf(
        ImportIssue(
            reason = reason,
            severity = ImportIssueSeverity.ERROR,
            stage = ImportStage.PARSE,
            sourceIndex = 0,
            detectedType = "wireguard",
            message = message,
        ),
    ),
)

private fun decodeWrappedUrlOrSelf(content: String): String? {
    if (content.substringBefore(':').lowercase() in RecognizedWireGuardSchemes) return content
    val compact = content.filterNot(Char::isWhitespace)
    if (compact.length * 3L / 4L > MaxImportBytes) {
        throw ImportLimitException(
            reason = ImportIssueReason.INPUT_TOO_LARGE,
            message = "Decoded WireGuard URL exceeds the allowed size",
        )
    }
    val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
    return runCatching {
        Base64.UrlSafe.decode(padded).decodeToString()
    }.getOrNull()?.takeIf {
        it.substringBefore(':').lowercase() in RecognizedWireGuardSchemes
    }
}

private fun parseQuery(rawQuery: String?): Map<String, List<String>> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return buildMap<String, MutableList<String>> {
        rawQuery.split('&').forEach { part ->
            val key = part.substringBefore('=').percentDecodedOnce().normalizedOption()
            val value = part.substringAfter('=', "").percentDecodedOnce()
            getOrPut(key) { mutableListOf() } += value
        }
    }
}

private fun Map<String, List<String>>.firstValue(vararg names: String): String? =
    names.asSequence()
        .map(String::normalizedOption)
        .mapNotNull { name -> get(name)?.lastOrNull() }
        .firstOrNull()
        ?.takeIf(String::isNotBlank)

private fun Map<String, List<String>>.values(vararg names: String): List<String> =
    names.flatMap { name -> get(name.normalizedOption()).orEmpty() }

private fun String.normalizedOption(): String =
    lowercase().replace("_", "").replace("-", "")

private fun String.percentDecodedOnce(): String =
    URLDecoder.decode(replace("+", "%2B"), Charsets.UTF_8.name())

private fun splitCommaValues(value: String): List<String> =
    value.split(',', ';', ' ', '\n', '\r').map(String::trim).filter(String::isNotBlank)

private fun sanitizeWireGuardKey(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var clean = raw.trim().removeSurrounding("\"", "").removeSurrounding("'", "")
    if ('%' in clean) {
        clean = runCatching {
            URLDecoder.decode(clean.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(clean).trim()
    }
    clean = clean.replace(' ', '+').replace('-', '+').replace('_', '/')
    clean = clean.filterNot(Char::isWhitespace)
    val remainder = clean.length % 4
    if (remainder != 0) {
        clean += "=".repeat(4 - remainder)
    }
    return clean
}

private fun generateRandomWireGuardKey(): String {
    val bytes = ByteArray(WireGuardKeyBytes)
    java.security.SecureRandom().nextBytes(bytes)
    return Base64.Default.encode(bytes)
}

private fun requireWireGuardKey(value: String?, label: String) {
    require(!value.isNullOrBlank()) { "$label is required" }
    val sanitized = sanitizeWireGuardKey(value) ?: value
    val padded = sanitized + "=".repeat((4 - sanitized.length % 4) % 4)
    val decoded = sequenceOf(Base64.Default, Base64.UrlSafe)
        .mapNotNull { decoder -> runCatching { decoder.decode(padded) }.getOrNull() }
        .firstOrNull()
    require(decoded != null && decoded.size == WireGuardKeyBytes) { "$label is invalid" }
}

private fun normalizeToCidr(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    if ('/' in trimmed) return trimmed
    return if (':' in trimmed) "$trimmed/128" else "$trimmed/32"
}

private fun requireCidr(value: String) {
    val normalized = normalizeToCidr(value)
    val separator = normalized.lastIndexOf('/')
    require(separator > 0) { "WireGuard address must use CIDR notation" }
    val address = normalized.substring(0, separator)
    val prefix = normalized.substring(separator + 1).toIntOrNull()
        ?: error("WireGuard CIDR prefix is invalid")
    val numeric = if (':' in address) {
        runCatching { InetAddress.getByName(address) as? Inet6Address }.getOrNull()
    } else {
        if (
            address.split('.').size == 4 &&
            address.split('.').all { part -> part.toIntOrNull() in 0..255 }
        ) {
            InetAddress.getByAddress(address.split('.').map(String::toInt).map(Int::toByte).toByteArray())
        } else {
            null
        }
    }
    require(numeric != null) { "WireGuard address is invalid" }
    val maximum = if (numeric is Inet6Address) 128 else 32
    require(prefix in 0..maximum) { "WireGuard CIDR prefix is invalid" }
}

private fun parseHostPort(value: String): Pair<String, Int> {
    val clean = value.trim()
        .removePrefix("wg://")
        .removePrefix("wireguard://")
        .removePrefix("warp://")
        .removePrefix("awg://")
        .substringBefore('/')
    val host = when {
        clean.startsWith("[") && "]:" in clean -> clean.substringAfter("[").substringBefore("]:")
        clean.startsWith("[") && clean.endsWith("]") -> clean.removeSurrounding("[", "]")
        ':' in clean -> clean.substringBeforeLast(':')
        else -> clean
    }.removeSurrounding("[", "]").trim()

    val port = when {
        clean.startsWith("[") && "]:" in clean -> clean.substringAfter("]:").toIntOrNull()
        ':' in clean -> clean.substringAfterLast(':').toIntOrNull()
        else -> null
    } ?: 51820

    return host to port.coerceIn(1, 65535)
}

private fun Int.requirePort(label: String): Int {
    return if (this in 1..65_535) this else 51820
}

private fun String.requireIntIn(label: String, minimum: Int, maximum: Int): Int {
    val parsed = toIntOrNull()
    require(parsed != null && parsed in minimum..maximum) { "$label is invalid" }
    return parsed
}

private fun parseReservedBytes(value: String): List<Int> {
    val clean = value.trim().removeSurrounding("[", "]").removeSurrounding("\"", "")
    val values = clean.split(',').mapNotNull { it.trim().toIntOrNull() }
    if (values.size == 3 && values.all { it in 0..255 }) {
        return values
    }
    val b64 = sequenceOf(Base64.Default, Base64.UrlSafe)
        .mapNotNull { decoder -> runCatching { decoder.decode(clean) }.getOrNull() }
        .firstOrNull()
    if (b64 != null && b64.size == 3) {
        return b64.map { it.toInt() and 0xFF }
    }
    return emptyList()
}

private fun Map<String, List<String>>.singleValue(key: String, label: String): String {
    val values = get(key).orEmpty()
    require(values.size == 1) { "$label must appear exactly once" }
    return values.single()
}

private fun Map<String, List<String>>.optionalSingleValue(key: String): String? {
    val values = get(key).orEmpty()
    require(values.size <= 1) { "WireGuard option must not be repeated" }
    return values.singleOrNull()
}

private data class WireGuardPeer(
    val address: String,
    val port: Int,
    val publicKey: String,
    val preSharedKey: String?,
    val allowedIps: List<String>,
    val keepalive: Int?,
    val reserved: List<Int>?,
)

private val AllowedWireGuardSchemes = setOf("wg", "wireguard", "warp", "awg")
private val RejectedWireGuardSchemes = emptySet<String>()
private val RecognizedWireGuardSchemes = AllowedWireGuardSchemes + RejectedWireGuardSchemes
private val DefaultAllowedIps = listOf("0.0.0.0/0", "::/0")
private const val WireGuardKeyBytes = 32
private val AwgOnlyFields = setOf(
    "jc", "jmin", "jmax", "s1", "s2", "h1", "h2", "h3", "h4",
)
private val WireGuardUrlFields = setOf(
    "privatekey",
    "pk",
    "publickey",
    "peerpublickey",
    "pub",
    "peerpub",
    "address",
    "ip",
    "allowedips",
    "localaddress",
    "presharedkey",
    "psk",
    "keepalive",
    "persistentkeepalive",
    "reserved",
    "workers",
    "mtu",
    "listenport",
) + AwgOnlyFields
