// Copyright 2026, IQ TUNNEL contributors
// SPDX-License-Identifier: GPL-3.0

package features.singbox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.LocalAppStateStore
import app.LocalAppServices
import app.LocalHomeServiceControl
import app.LocalMainDestinationState
import app.LocalUpdateAppState
import app.navigation.Navigator
import app.OutboundState
import app.R
import app.collectAppState
import app.managedOutboundGroupSelectorTag
import app.withSelectorSelection
import app.modes.SingBoxModeDirect
import app.modes.SingBoxModeGlobal
import app.modes.SingBoxModeRule
import app.navigation.MainDestination
import app.navigation.Route as AppRoute
import engine.singbox.config.APP_GLOBAL_SELECTOR
import engine.singbox.runtime.SingBoxRuntimeState
import features.home.HomeMonitoringOverviewState
import features.home.HomeNetworkActivityState
import features.home.formatHomeServiceUptime
import features.monitoring.network.countryCodeToEmoji
import features.outbound.OutboundPingRuntimeState
import engine.proxy.LocalProxyRuntime
import engine.proxy.toLocalProxyOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskModalBottomSheet
import ui.icons.AsteriskIcons as Icons
import utils.toReadableBytes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Theme & Cyber Palette
private val NeonPink = Color(0xFFFF4081)
private val NeonPinkGlow = Color(0xFFFF80AB)
private val NeonRed = Color(0xFFFF1744)

private val CyberGold = Color(0xFFFFD54F)
private val CyberGoldBright = Color(0xFFFFE082)

private val CyberCyan = Color(0xFF00E5FF)
private val CyberCyanSoft = Color(0xFF80DEEA)
private val SkyBlue = Color(0xFF00B0FF)
private val ConnectGreen = Color(0xFF00E676)
private val ConnectGreenBright = Color(0xFF69F0AE)

private val CyberPurple = Color(0xFFAB47BC)
private val CyberPurpleLight = Color(0xFFCE93D8)

internal data class ActiveServerInfo(
    val tag: String,
    val name: String,
    val protocol: String,
    val delayMs: Int?,
    val outbound: OutboundState?,
)

/**
 * Resolves active server name, real protocol, and real measured ping dynamically.
 */
@Composable
internal fun resolveActiveServerInfo(
    appState: AppState,
    runtimeState: SingBoxRuntimeState,
    pingState: OutboundPingRuntimeState,
): ActiveServerInfo {
    val globalGroup = runtimeState.proxies.groups.firstOrNull { it.name == APP_GLOBAL_SELECTOR }
        ?: runtimeState.proxies.groups.firstOrNull()

    var effectiveTag = globalGroup?.now?.takeIf(String::isNotBlank)
        ?: appState.selectorSelections[APP_GLOBAL_SELECTOR]
        ?: appState.selectorSelections.values.firstOrNull()

    // If effectiveTag is a group selector (e.g. outbound_group:1:Default), resolve the server selected inside that group
    if (effectiveTag != null && effectiveTag.startsWith("outbound_group:")) {
        val subGroup = runtimeState.proxies.groups.firstOrNull { it.name == effectiveTag }
        effectiveTag = subGroup?.now?.takeIf(String::isNotBlank)
            ?: appState.selectorSelections[effectiveTag]
            ?: effectiveTag
    }

    val activeOutbound = appState.outbounds.firstOrNull {
        it.tag == effectiveTag || it.remarks == effectiveTag
    } ?: appState.outbounds.firstOrNull()

    val activeNode = effectiveTag?.let { runtimeState.proxies.node(it) }

    val name = activeOutbound?.remarks?.takeIf(String::isNotBlank)
        ?: activeNode?.name?.takeIf(String::isNotBlank)
        ?: "اضغط لاختيار سيرفر"

    val rawType = activeOutbound?.type?.takeIf(String::isNotBlank)
        ?: activeNode?.type?.takeIf(String::isNotBlank)
        ?: "PROXY"
    val protocolBadge = rawType.displaySingBoxProtocolName(compact = true)

    val delayMs: Int? = activeOutbound?.id?.let { pingState.entries[it]?.latencyMillis?.toInt() }?.takeIf { it > 0 }
        ?: activeNode?.delay?.takeIf { it > 0 }

    return ActiveServerInfo(
        tag = effectiveTag.orEmpty(),
        name = name,
        protocol = protocolBadge,
        delayMs = delayMs,
        outbound = activeOutbound,
    )
}

/**
 * Top Cyber Bar matching screenshot:
 * Right in RTL: IQ TUNNEL + VIP PROTOCOLS (gold) + Glowing Circular Gold Shield
 * Left in RTL: Telegram Channel Circular Button + Status Pill `● متصل` (or `● غير متصل`)
 */
@Composable
internal fun CyberTopHeader(
    activeServer: ActiveServerInfo,
    modifier: Modifier = Modifier,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val isConnected = appState.proxyRunning
    val uriHandler = LocalUriHandler.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Right side in RTL (Start): Shield + IQ TUNNEL + VIP PROTOCOLS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF002238))
                        .border(1.2.dp, SkyBlue, CircleShape)
                        .clickable { uriHandler.openUri("https://t.me/FREEVPSIRAQ") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "Security",
                        tint = SkyBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "IQ TUNNEL",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = Color.White,
                    )
                    Text(
                        text = "VIP PROTOCOLS",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = SkyBlue,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            // Left side in RTL (End): Status Pill + Telegram Circular Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF141520),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) Color(0x5500E676) else Color(0x33FF1744),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF00E676) else Color(0xFFFF1744)),
                        )
                        Text(
                            text = if (isConnected) "متصل" else "غير متصل",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                // Telegram updates button with label
                Surface(
                    onClick = { uriHandler.openUri("https://t.me/FREEVPSIRAQ") },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isConnected) Color(0xFF003B57) else Color(0xFF2C0B14),
                    border = androidx.compose.foundation.BorderStroke(
                        1.2.dp,
                        if (isConnected) SkyBlue else Color(0xFF6A1727),
                    ),
                    modifier = Modifier.height(34.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Telegram Channel",
                            tint = if (isConnected) SkyBlue else Color(0xFFFF80AB),
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "قناة التحديثات",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact 3D Faceted Crystal Plasma Energy Orb with Concentric HUD Rings
 * and typography directly below the orb (fits comfortably on one screen).
 */
@Composable
internal fun CyberReactorPowerButton(
    activeServer: ActiveServerInfo,
    modifier: Modifier = Modifier,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val control = LocalHomeServiceControl.current
    val services = LocalAppServices.current
    val isRunning = appState.proxyRunning
    val isBusy = control.busy

    // Auto-ping active server when connected (continuously refreshed every 8 seconds)
    LaunchedEffect(isRunning, activeServer.outbound?.id, activeServer.tag) {
        if (isRunning) {
            while (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive != false) {
                if (activeServer.outbound != null) {
                    services.outboundPingRuntime.start(listOf(activeServer.outbound), supersede = true)
                }
                if (activeServer.tag.isNotBlank()) {
                    runCatching { services.singBoxRuntime.testProxyDelay(appState, activeServer.tag) }
                }
                kotlinx.coroutines.delay(8000L)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "reactor-animation")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRunning) 8000 else 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "outer-ring-rotation",
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRunning) 6000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inner-ring-rotation",
    )

    val orbPulse by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isRunning) 1.04f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )

    val lightningPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lightning-phase",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(152.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 1. Outer Concentric Cyber Ring with tick marks (144.dp)
            Canvas(
                modifier = Modifier
                    .size(144.dp)
                    .rotate(outerRotation),
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()
                val ringAccentColor = if (isRunning) ConnectGreen else Color(0xFFFFD54F)
                val strokeWidth = 1.4.dp.toPx()

                drawCircle(
                    color = ringAccentColor.copy(alpha = if (isRunning) 0.35f else 0.12f),
                    radius = radius,
                    style = Stroke(width = strokeWidth),
                )

                // High-tech tick marks around circumference (36 ticks)
                val tickCount = 36
                for (i in 0 until tickCount) {
                    val angle = (i * 360f / tickCount) * (PI.toFloat() / 180f)
                    val tickLen = if (i % 3 == 0) 5.dp.toPx() else 2.5.dp.toPx()
                    val tickAlpha = if (i % 3 == 0) (if (isRunning) 0.9f else 0.4f) else (if (isRunning) 0.5f else 0.2f)
                    val startX = center.x + (radius - tickLen) * cos(angle)
                    val startY = center.y + (radius - tickLen) * sin(angle)
                    val endX = center.x + radius * cos(angle)
                    val endY = center.y + radius * sin(angle)
                    drawLine(
                        color = ringAccentColor.copy(alpha = tickAlpha),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (i % 3 == 0) 1.4.dp.toPx() else 0.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // 3 Glowing accent arcs
                val arcColor = if (isRunning) ringAccentColor else ringAccentColor.copy(alpha = 0.5f)
                drawArc(
                    color = arcColor,
                    startAngle = 15f,
                    sweepAngle = 65f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = arcColor,
                    startAngle = 135f,
                    sweepAngle = 70f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = arcColor,
                    startAngle = 255f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 2. Inner Concentric Cyan HUD Counter-Rotating Ring (122.dp)
            Canvas(
                modifier = Modifier
                    .size(122.dp)
                    .rotate(innerRotation),
            ) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                val cyanColor = Color(0xFF00E5FF)
                val strokeWidth = 1.2.dp.toPx()

                drawCircle(
                    color = cyanColor.copy(alpha = if (isRunning) 0.2f else 0.1f),
                    radius = radius,
                    style = Stroke(width = strokeWidth),
                )

                drawArc(
                    color = if (isRunning) cyanColor else cyanColor.copy(alpha = 0.45f),
                    startAngle = 45f,
                    sweepAngle = 80f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 0.6.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = if (isRunning) cyanColor else cyanColor.copy(alpha = 0.45f),
                    startAngle = 200f,
                    sweepAngle = 85f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth + 0.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 3. 3D Faceted Crystal Plasma Energy Orb (100.dp)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(orbPulse),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w / 2f

                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38104E),
                                Color(0xFF1B1238),
                                Color(0xFF0C0E1A),
                            ),
                            center = Offset(cx, cy),
                            radius = r,
                        ),
                    )

                    val outerPts = List(8) { i ->
                        val a = (i * 45f) * (PI.toFloat() / 180f)
                        Offset(cx + r * 0.95f * cos(a), cy + r * 0.95f * sin(a))
                    }
                    val midAngleOffset = outerRotation * (PI.toFloat() / 180f) * 0.5f
                    val midPts = List(6) { i ->
                        val a = (i * 60f) * (PI.toFloat() / 180f) + midAngleOffset
                        Offset(cx + r * 0.55f * cos(a), cy + r * 0.55f * sin(a))
                    }

                    val facetColors = listOf(
                        Color(0x3300E5FF),
                        Color(0x35E040FB),
                        Color(0x28FFD54F),
                        Color(0x303F51B5),
                    )

                    for (i in 0 until 6) {
                        val m1 = midPts[i]
                        val m2 = midPts[(i + 1) % 6]
                        val o1 = outerPts[i % 8]
                        val o2 = outerPts[(i + 1) % 8]

                        val path1 = Path().apply {
                            moveTo(m1.x, m1.y)
                            lineTo(m2.x, m2.y)
                            lineTo(o1.x, o1.y)
                            close()
                        }
                        drawPath(path1, facetColors[i % facetColors.size])

                        val path2 = Path().apply {
                            moveTo(m2.x, m2.y)
                            lineTo(o1.x, o1.y)
                            lineTo(o2.x, o2.y)
                            close()
                        }
                        drawPath(path2, facetColors[(i + 1) % facetColors.size])

                        drawLine(
                            color = Color(0x6600E5FF),
                            start = m1,
                            end = m2,
                            strokeWidth = 1.dp.toPx(),
                        )
                        drawLine(
                            color = Color(0x55E040FB),
                            start = m1,
                            end = o1,
                            strokeWidth = 0.8.dp.toPx(),
                        )
                    }

                    // Electric arcs / lightning discharge
                    if (isRunning) {
                        val arcAlpha = 0.5f + 0.4f * sin(lightningPhase * 2 * PI.toFloat())
                        val p1 = midPts[(lightningPhase * 5).toInt() % 6]
                        val p2 = outerPts[((lightningPhase * 7).toInt() + 2) % 8]
                        val midX = (p1.x + p2.x) / 2f + (sin(lightningPhase * 10) * 6.dp.toPx())
                        val midY = (p1.y + p2.y) / 2f + (cos(lightningPhase * 10) * 6.dp.toPx())

                        val lightningPath = Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(midX, midY)
                            lineTo(p2.x, p2.y)
                        }
                        drawPath(
                            path = lightningPath,
                            color = Color(0xFF00E5FF).copy(alpha = arcAlpha),
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }

                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = if (isRunning) 0.4f else 0.2f),
                        radius = r - 1.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }

                // 4. Center Metallic Power Hub (44.dp)
                Surface(
                    onClick = control.toggleService,
                    enabled = !isBusy,
                    shape = CircleShape,
                    color = Color(0xFF12141D),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.6.dp,
                        brush = Brush.sweepGradient(
                            colors = if (isRunning) {
                                listOf(ConnectGreen, Color(0xFF00E5FF), ConnectGreenBright, ConnectGreen)
                            } else {
                                listOf(Color(0xFF455A64), Color(0xFF90A4AE), Color(0xFF455A64))
                            },
                        ),
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.5.dp, if (isRunning) ConnectGreen.copy(alpha = 0.4f) else Color(0x3300E5FF), CircleShape),
                    shadowElevation = if (isRunning) 12.dp else 4.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (control.serviceOperationInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (isRunning) ConnectGreen else Color(0xFF00E5FF),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = "Toggle VPN",
                                tint = if (isRunning) ConnectGreen else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        // 5. Typography directly below the orb (compact)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isRunning) "CONNECTED" else "DISCONNECTED",
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = if (isRunning) ConnectGreenBright else Color.White.copy(alpha = 0.5f),
        )
        Text(
            text = if (isRunning) "متصل ⚡" else "غير متصل",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRunning) ConnectGreen else Color.White.copy(alpha = 0.45f),
        )
        val pingVal = when {
            activeServer.delayMs != null && activeServer.delayMs > 0 -> "${activeServer.delayMs} ms"
            isRunning -> "جاري الفحص..."
            else -> "-- ms"
        }
        Text(
            text = "Ping: $pingVal",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = if (isRunning) CyberCyanSoft else Color.White.copy(alpha = 0.4f),
        )
    }
}

/**
 * Dynamic Server Selector Card:
 * Fixes protocol text vertical wrapping (no VLESS vertical squishing).
 * Displays protocol badge, checkmark, flag, server name with ellipsis,
 * and ping test pill button.
 */
@Composable
internal fun CyberServerSelectorCard(
    activeServer: ActiveServerInfo,
    runtimeState: SingBoxRuntimeState,
    modifier: Modifier = Modifier,
) {
    val mainDestinationState = LocalMainDestinationState.current
    val services = LocalAppServices.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val pingState by services.outboundPingRuntime.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showServerPicker by remember { mutableStateOf(false) }

    Surface(
        onClick = { showServerPicker = true },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10111A),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, NeonPink),
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Right side in RTL (Start): Globe Icon + Server Name + Flag + Protocol badge + Subtitle
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF281533))
                            .border(1.dp, Color(0xFF7B1FA2), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = activeServer.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(13.dp),
                            )
                            // Protocol badge - fixed horizontal wrap, NEVER wraps vertically
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = CyberGold,
                            ) {
                                Text(
                                    text = activeServer.protocol,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.5.sp,
                                    color = Color.Black,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "انقر لتغيير السيرفر مباشرة",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.5.sp,
                            color = Color(0xFFB39DDB),
                            maxLines = 1,
                        )
                    }
                }

                // Left side in RTL (End): فحص ms > Pill Button
                Surface(
                    onClick = {
                        scope.launch {
                            if (activeServer.outbound != null) {
                                services.outboundPingRuntime.start(listOf(activeServer.outbound), supersede = true)
                            }
                            if (activeServer.tag.isNotBlank() && runtimeState.running) {
                                runCatching { services.singBoxRuntime.testProxyDelay(appState, activeServer.tag) }
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = NeonRed,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = if (activeServer.delayMs != null && activeServer.delayMs > 0) "${activeServer.delayMs} ms" else "فحص ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Ping",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showServerPicker) {
        AsteriskModalBottomSheet(
            show = showServerPicker,
            onDismissRequest = { showServerPicker = false },
            title = "اختيار الخادم • CHOOSE SERVER",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = {
                        showServerPicker = false
                        mainDestinationState?.select(MainDestination.Proxies)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x22FF1744),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonRed),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = NeonRed,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "إضافة سيرفر جديد (+)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonRed,
                        )
                    }
                }

                appState.outbounds.forEach { outbound ->
                    val isSelected = activeServer.tag == outbound.tag || activeServer.tag == outbound.remarks || activeServer.name == outbound.remarks
                    val delayMs = pingState.entries[outbound.id]?.latencyMillis?.toInt()
                    Surface(
                        onClick = {
                            val targetTag = outbound.tag
                            val group = appState.outboundGroups.firstOrNull { it.id == outbound.groupId }
                            val groupTag = group?.let { managedOutboundGroupSelectorTag(it.id, it.name) }
                            
                            updateAppState { state ->
                                var next = state.withSelectorSelection(APP_GLOBAL_SELECTOR, targetTag)
                                if (groupTag != null) {
                                    next = next.withSelectorSelection(groupTag, targetTag)
                                }
                                next
                            }
                            if (appState.proxyRunning) {
                                scope.launch {
                                    if (groupTag != null) {
                                        runCatching {
                                            services.singBoxRuntime.selectProxy(appState, groupTag, targetTag)
                                        }
                                    }
                                    runCatching {
                                        services.singBoxRuntime.selectProxy(appState, APP_GLOBAL_SELECTOR, targetTag)
                                    }
                                    // Trigger immediate delay test on the newly selected server
                                    services.outboundPingRuntime.start(listOf(outbound), supersede = true)
                                    if (targetTag.isNotBlank()) {
                                        runCatching { services.singBoxRuntime.testProxyDelay(appState, targetTag) }
                                    }
                                }
                            }
                            showServerPicker = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0x2BFF1744) else Color(0xFF161722),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) NeonRed else Color(0x22FFFFFF),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.VpnLock,
                                    contentDescription = null,
                                    tint = if (isSelected) NeonRed else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = outbound.remarks,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = outbound.type.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonRed,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                            if (delayMs != null && delayMs > 0) {
                                Text(
                                    text = "$delayMs ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = {
                        showServerPicker = false
                        mainDestinationState?.select(MainDestination.Proxies)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E1F2C),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "فتح قائمة البروكسيات بالكامل ➔",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/**
 * Multi-harmonic animated dual oscilloscope waveform:
 * Actively undulating cyan & magenta waves with traveling glowing pulse nodes.
 */
@Composable
internal fun CyberDualWaveform(
    downloadBytes: Long,
    uploadBytes: Long,
    modifier: Modifier = Modifier,
) {
    val effectiveDlRatio = when {
        downloadBytes <= 0L -> 0f
        downloadBytes < 1024L -> 0.16f
        else -> {
            val logSpeed = kotlin.math.log10(downloadBytes.toFloat())
            ((logSpeed - 3f) / 3.7f).coerceIn(0.18f, 1f)
        }
    }
    val effectiveUlRatio = when {
        uploadBytes <= 0L -> 0f
        uploadBytes < 1024L -> 0.16f
        else -> {
            val logSpeed = kotlin.math.log10(uploadBytes.toFloat())
            ((logSpeed - 3f) / 3.7f).coerceIn(0.18f, 1f)
        }
    }

    val animatedDlAmp by animateFloatAsState(
        targetValue = if (downloadBytes > 0L) 0.22f + effectiveDlRatio * 0.78f else 0.08f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "dl-amp",
    )
    val animatedUlAmp by animateFloatAsState(
        targetValue = if (uploadBytes > 0L) 0.20f + effectiveUlRatio * 0.80f else 0.06f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "ul-amp",
    )

    var wavePhase1 by remember { mutableFloatStateOf(0f) }
    var wavePhase2 by remember { mutableFloatStateOf(0f) }
    var harmonicPhase by remember { mutableFloatStateOf(0f) }

    val dlSpeedFactor = 1.0f + effectiveDlRatio * 4.5f
    val ulSpeedFactor = 1.0f + effectiveUlRatio * 4.5f
    val currentDlSpeed by rememberUpdatedState(dlSpeedFactor)
    val currentUlSpeed by rememberUpdatedState(ulSpeedFactor)

    LaunchedEffect(Unit) {
        var lastTimeNanos = 0L
        while (true) {
            withFrameNanos { timeNanos ->
                if (lastTimeNanos != 0L) {
                    val dt = (timeNanos - lastTimeNanos) / 1_000_000_000f
                    wavePhase1 = (wavePhase1 + dt * (1.8f * currentDlSpeed)) % (2f * PI.toFloat())
                    wavePhase2 = (wavePhase2 - dt * (1.4f * currentUlSpeed)) % (2f * PI.toFloat())
                    harmonicPhase = (harmonicPhase + dt * (3.0f * (currentDlSpeed + currentUlSpeed) * 0.5f)) % (2f * PI.toFloat())
                }
                lastTimeNanos = timeNanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas
        val midY = height * 0.52f

        // Subtle horizontal grid lines
        val gridColor = Color(0x1800E5FF)
        drawLine(
            color = gridColor,
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = gridColor.copy(alpha = 0.08f),
            start = Offset(0f, height * 0.25f),
            end = Offset(width, height * 0.25f),
            strokeWidth = 0.8.dp.toPx(),
        )
        drawLine(
            color = gridColor.copy(alpha = 0.08f),
            start = Offset(0f, height * 0.75f),
            end = Offset(width, height * 0.75f),
            strokeWidth = 0.8.dp.toPx(),
        )

        // 1. Cyan Wave (Download) - Compound Harmonic Undulation + Traffic Ripples
        val dlPath = Path()
        val dlFillPath = Path()
        val dlColor = Color(0xFF00E5FF)
        val dlMaxAmp = height * 0.42f * animatedDlAmp

        dlFillPath.moveTo(0f, height)
        var peakDlX = 0f
        var peakDlY = midY
        var minDlY = Float.MAX_VALUE

        val stepPx = 3
        for (x in 0..width.toInt() step stepPx) {
            val progress = x / width
            val baseAngle = progress * (3.2f * PI.toFloat()) + wavePhase1
            val harmonicAngle = progress * (6.4f * PI.toFloat()) - harmonicPhase
            val highFreqRipple = if (effectiveDlRatio > 0.05f) {
                sin(progress * (16f * PI.toFloat()) + wavePhase1 * 3f) * (0.12f * effectiveDlRatio)
            } else 0f
            val y = midY - (sin(baseAngle) * 0.72f + sin(harmonicAngle) * 0.28f + highFreqRipple) * dlMaxAmp
            if (y < minDlY) {
                minDlY = y
                peakDlX = x.toFloat()
                peakDlY = y
            }
            if (x == 0) {
                dlPath.moveTo(x.toFloat(), y)
                dlFillPath.lineTo(x.toFloat(), y)
            } else {
                dlPath.lineTo(x.toFloat(), y)
                dlFillPath.lineTo(x.toFloat(), y)
            }
        }
        dlFillPath.lineTo(width, height)
        dlFillPath.close()

        drawPath(
            path = dlFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(dlColor.copy(alpha = 0.12f + effectiveDlRatio * 0.22f), Color.Transparent),
                startY = (midY - dlMaxAmp).coerceAtLeast(0f),
                endY = height,
            ),
        )
        drawPath(
            path = dlPath,
            color = dlColor,
            style = Stroke(width = if (effectiveDlRatio > 0.1f) 2.2.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Glowing energy nodes tracking the wave
        val dlHaloRadius = (3.5f + effectiveDlRatio * 4.5f).dp.toPx()
        val dlCoreRadius = (2.0f + effectiveDlRatio * 1.5f).dp.toPx()
        drawCircle(
            color = dlColor.copy(alpha = 0.4f + effectiveDlRatio * 0.45f),
            radius = dlHaloRadius,
            center = Offset(peakDlX, peakDlY),
        )
        drawCircle(
            color = Color.White,
            radius = dlCoreRadius,
            center = Offset(peakDlX, peakDlY),
        )

        // 2. Magenta Wave (Upload) - Compound Harmonic Undulation + Traffic Ripples
        val ulPath = Path()
        val ulFillPath = Path()
        val ulColor = Color(0xFFFF4081)
        val ulMaxAmp = height * 0.38f * animatedUlAmp

        ulFillPath.moveTo(0f, height)
        var peakUlX = 0f
        var peakUlY = midY
        var maxUlY = Float.MIN_VALUE

        for (x in 0..width.toInt() step stepPx) {
            val progress = x / width
            val baseAngle = progress * (4.0f * PI.toFloat()) + wavePhase2
            val harmonicAngle = progress * (7.2f * PI.toFloat()) + harmonicPhase * 0.7f
            val highFreqRippleUl = if (effectiveUlRatio > 0.05f) {
                cos(progress * (18f * PI.toFloat()) - wavePhase2 * 3f) * (0.12f * effectiveUlRatio)
            } else 0f
            val y = midY + (sin(baseAngle) * 0.70f + cos(harmonicAngle) * 0.30f + highFreqRippleUl) * ulMaxAmp
            if (y > maxUlY) {
                maxUlY = y
                peakUlX = x.toFloat()
                peakUlY = y
            }
            if (x == 0) {
                ulPath.moveTo(x.toFloat(), y)
                ulFillPath.lineTo(x.toFloat(), y)
            } else {
                ulPath.lineTo(x.toFloat(), y)
                ulFillPath.lineTo(x.toFloat(), y)
            }
        }
        ulFillPath.lineTo(width, height)
        ulFillPath.close()

        drawPath(
            path = ulFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(ulColor.copy(alpha = 0.10f + effectiveUlRatio * 0.20f), Color.Transparent),
                startY = midY,
                endY = height,
            ),
        )
        drawPath(
            path = ulPath,
            color = ulColor,
            style = Stroke(width = if (effectiveUlRatio > 0.1f) 2.2.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Glowing energy node on upload wave
        val ulHaloRadius = (3.2f + effectiveUlRatio * 4.0f).dp.toPx()
        val ulCoreRadius = (1.8f + effectiveUlRatio * 1.4f).dp.toPx()
        drawCircle(
            color = ulColor.copy(alpha = 0.4f + effectiveUlRatio * 0.45f),
            radius = ulHaloRadius,
            center = Offset(peakUlX, peakUlY),
        )
        drawCircle(
            color = Color.White,
            radius = ulCoreRadius,
            center = Offset(peakUlX, peakUlY),
        )
    }
}

private fun formatSpeed(bytes: Long?): String {
    return bytes?.toReadableBytes()?.let { "$it/s" } ?: "0 B/s"
}

/**
 * Real-time Live Network Activity Card:
 * Renders actual discrete network traffic samples (download & upload speed graph)
 * directly from sing-box runtime measurements with smooth curves and gradient fills.
 */
@Composable
internal fun CyberLiveTrafficCard(
    networkActivityState: HomeNetworkActivityState,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF10111A),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, CyberCyan),
        modifier = modifier.fillMaxWidth().height(92.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Right in RTL: Live Indicator + نشاط الشبكة • LIVE TRAFFIC
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (networkActivityState.hasNetworkSamples) ConnectGreen else CyberCyan),
                        )
                        Text(
                            text = "نشاط الشبكة • LIVE TRAFFIC",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan,
                            letterSpacing = 0.4.sp,
                        )
                    }

                    // Left in RTL: Directional Speed indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Upload
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Upload,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = formatSpeed(networkActivityState.uploadBytesPerSecond),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = NeonPinkGlow,
                            )
                        }

                        // Download
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = formatSpeed(networkActivityState.downloadBytesPerSecond),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyanSoft,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Real Network Samples Chart or Dynamic Dual Waveform when samples are building
                if (networkActivityState.networkSamples.size >= 2) {
                    CyberRealNetworkChart(
                        state = networkActivityState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    CyberDualWaveform(
                        downloadBytes = networkActivityState.downloadBytesPerSecond ?: 0L,
                        uploadBytes = networkActivityState.uploadBytesPerSecond ?: 0L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Authentic real-time network activity graph drawn using actual sing-box traffic samples.
 */
@Composable
internal fun CyberRealNetworkChart(
    state: HomeNetworkActivityState,
    modifier: Modifier = Modifier,
) {
    val uploadColor = NeonPink
    val downloadColor = CyberCyan
    val baselineColor = Color(0x1800E5FF)

    Canvas(modifier = modifier) {
        val samples = state.networkSamples
        if (samples.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val baseline = h - 2.dp.toPx()

        // Subtle baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, baseline),
            end = Offset(w, baseline),
            strokeWidth = 1.dp.toPx(),
        )

        // Peak bandwidth across window
        val peakVal = samples.maxOfOrNull { maxOf(it.up, it.down) }?.coerceAtLeast(1024L) ?: 1024L
        val maxGraphH = baseline - 3.dp.toPx()
        val step = w / (samples.lastIndex.coerceAtLeast(1))

        // 1. Download Path & Gradient Fill
        val dlPath = Path()
        val dlFillPath = Path()
        dlFillPath.moveTo(0f, baseline)

        samples.forEachIndexed { i, s ->
            val x = i * step
            val fraction = (s.down.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f)
            val y = baseline - fraction * maxGraphH
            if (i == 0) {
                dlPath.moveTo(x, y)
                dlFillPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * step
                val prevFrac = (samples[i - 1].down.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f)
                val prevY = baseline - prevFrac * maxGraphH
                val cx = (prevX + x) / 2f
                dlPath.cubicTo(cx, prevY, cx, y, x, y)
                dlFillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
        }
        dlFillPath.lineTo(w, baseline)
        dlFillPath.close()

        drawPath(
            path = dlFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(downloadColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = baseline,
            ),
        )
        drawPath(
            path = dlPath,
            color = downloadColor,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
        )

        // 2. Upload Path & Gradient Fill
        val ulPath = Path()
        val ulFillPath = Path()
        ulFillPath.moveTo(0f, baseline)

        samples.forEachIndexed { i, s ->
            val x = i * step
            val fraction = (s.up.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f)
            val y = baseline - fraction * maxGraphH
            if (i == 0) {
                ulPath.moveTo(x, y)
                ulFillPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * step
                val prevFrac = (samples[i - 1].up.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f)
                val prevY = baseline - prevFrac * maxGraphH
                val cx = (prevX + x) / 2f
                ulPath.cubicTo(cx, prevY, cx, y, x, y)
                ulFillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
        }
        ulFillPath.lineTo(w, baseline)
        ulFillPath.close()

        drawPath(
            path = ulFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(uploadColor.copy(alpha = 0.20f), Color.Transparent),
                startY = 0f,
                endY = baseline,
            ),
        )
        drawPath(
            path = ulPath,
            color = uploadColor,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
        )

        // Draw active tracking dots on the latest sample
        val lastIndex = samples.lastIndex
        val lastDownY = baseline - (samples[lastIndex].down.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f) * maxGraphH
        val lastUpY = baseline - (samples[lastIndex].up.toFloat() / peakVal.toFloat()).coerceIn(0f, 1f) * maxGraphH

        drawCircle(
            color = downloadColor,
            radius = 2.5.dp.toPx(),
            center = Offset(w, lastDownY),
        )
        drawCircle(
            color = Color.White,
            radius = 1.2.dp.toPx(),
            center = Offset(w, lastDownY),
        )

        drawCircle(
            color = uploadColor,
            radius = 2.2.dp.toPx(),
            center = Offset(w, lastUpY),
        )
    }
}

/**
 * Direct shortcut card for Proxy App List matching screenshot:
 * Purple glowing border, right purple square with 9-dot grid icon,
 * title, subtitle, and left chevron `<`.
 */
@Composable
internal fun CyberAppExclusionCard(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { navigator.push(AppRoute.ProxyAppList) },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF10111A),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, CyberPurple),
        modifier = modifier.fillMaxWidth(),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF33164D))
                            .border(0.8.dp, Color(0xFF7B1FA2), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = CyberPurpleLight,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "استثناء وتوجيه التطبيقات",
                            style = MaterialTheme.typography.titleSmall,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "تحديد التطبيقات المشمولة بالبروكسي • PROXY APPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            color = Color(0xFFB39DDB),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = null,
                    tint = CyberPurpleLight,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Compact Cyber Clash Mode Selector (Rule, Global, Direct) matching screenshot:
 * Selected item glows in red-amber gradient, unselected in sleek dark surface.
 */
@Composable
internal fun CyberModeSelector(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = listOf(
        Triple(SingBoxModeRule, "توجيه (Rule) ⇅", Icons.Rounded.Route),
        Triple(SingBoxModeGlobal, "شامل (Global) 🌍", Icons.Rounded.Public),
        Triple(SingBoxModeDirect, "مباشر (Direct) 🛡", Icons.Rounded.Security),
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            modes.forEach { (mode, label, icon) ->
                val isSelected = currentMode == mode
                Surface(
                    onClick = { onModeSelected(mode) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF8B1228) else Color(0xFF12131D),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFFFF5252) else Color(0x22FFFFFF),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) {
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFC2185B), Color(0xFFD50000), Color(0xFFFF5722))
                                    )
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF13141F), Color(0xFF13141F)))
                                }
                            )
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun collectLocalIpv4(): String {
    return runCatching {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return ""
        for (iface in interfaces) {
            if (iface.isUp && !iface.isLoopback && !iface.isVirtual) {
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank()) return host
                    }
                }
            }
        }
        ""
    }.getOrDefault("")
}

/**
 * Compact Cyber Metrics Grid (2x2 Panel) matching screenshot:
 * Row 1: Resources (Cyan) & Connections (Purple)
 * Row 2: Traffic Today (Gold) & Network/IP (Blue)
 */
@Composable
internal fun CyberMetricsGrid(
    monitoringOverviewState: HomeMonitoringOverviewState,
    navigator: Navigator,
    activeServerTag: String = "",
    modifier: Modifier = Modifier,
) {
    val appServices = LocalAppServices.current
    val appState by LocalAppStateStore.current.collectAppState()
    val isRunning = appState.proxyRunning
    val monitoringState by appServices.monitoring.state.collectAsState()

    var detectedIp by remember { mutableStateOf<String?>(null) }
    var detectedCountryCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(monitoringState.network.publicProbe) {
        val probe = monitoringState.network.publicProbe.ipv4
        if (probe.address.isNotBlank() && isRunning) {
            detectedIp = probe.address
            if (probe.countryCode.isNotBlank()) {
                detectedCountryCode = probe.countryCode
            }
        }
    }

    LaunchedEffect(isRunning, activeServerTag) {
        if (!isRunning) {
            withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URI("https://cloudflare.com/cdn-cgi/trace").toURL()
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 3000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val lines = text.lines()
                    val ip = lines.firstOrNull { it.startsWith("ip=") }?.substringAfter("ip=")?.trim()
                    val loc = lines.firstOrNull { it.startsWith("loc=") }?.substringAfter("loc=")?.trim()
                    if (!ip.isNullOrBlank()) {
                        detectedIp = ip
                        detectedCountryCode = loc ?: "IQ"
                        return@withContext
                    }
                } catch (_: Throwable) {}
                val local = collectLocalIpv4()
                if (local.isNotBlank()) {
                    detectedIp = local
                    detectedCountryCode = "IQ"
                }
            }
            return@LaunchedEffect
        }

        appServices.monitoring.refreshPublicNetworkProbe()
        delay(1200)

        withContext(Dispatchers.IO) {
            val localProxyOptions = LocalProxyRuntime.current() ?: appState.toLocalProxyOptions()
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress("127.0.0.1", localProxyOptions.port),
            )

            // Attempt 1: Cloudflare trace through proxy
            try {
                val url = java.net.URI("https://cloudflare.com/cdn-cgi/trace").toURL()
                val conn = (url.openConnection(proxy) as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    if (localProxyOptions.username.isNotBlank()) {
                        val auth = "${localProxyOptions.username}:${localProxyOptions.password}".toByteArray()
                        val encoded = android.util.Base64.encodeToString(auth, android.util.Base64.NO_WRAP)
                        setRequestProperty("Proxy-Authorization", "Basic $encoded")
                    }
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val lines = text.lines()
                val ip = lines.firstOrNull { it.startsWith("ip=") }?.substringAfter("ip=")?.trim()
                val loc = lines.firstOrNull { it.startsWith("loc=") }?.substringAfter("loc=")?.trim()
                if (!ip.isNullOrBlank()) {
                    detectedIp = ip
                    if (!loc.isNullOrBlank()) detectedCountryCode = loc
                    return@withContext
                }
            } catch (_: Throwable) {}

            // Attempt 2: ip-api through proxy
            try {
                val url = java.net.URI("http://ip-api.com/line/?fields=countryCode,query").toURL()
                val conn = (url.openConnection(proxy) as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
                if (lines.size >= 2) {
                    detectedCountryCode = lines[0]
                    detectedIp = lines[1]
                    return@withContext
                } else if (lines.size == 1) {
                    detectedIp = lines[0]
                    return@withContext
                }
            } catch (_: Throwable) {}

            // Attempt 3: api.ipify.org through proxy
            try {
                val url = java.net.URI("https://api.ipify.org").toURL()
                val conn = (url.openConnection(proxy) as java.net.HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val ip = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                conn.disconnect()
                if (ip.isNotBlank()) {
                    detectedIp = ip
                }
            } catch (_: Throwable) {}
        }
    }

    val flagEmoji = countryCodeToEmoji(detectedCountryCode).ifBlank { if (isRunning) "🌐" else "🇮🇶" }
    val networkDisplayValue = when {
        !detectedIp.isNullOrBlank() -> "$flagEmoji $detectedIp"
        isRunning -> "$flagEmoji جاري الفحص..."
        else -> {
            val local = collectLocalIpv4()
            if (local.isNotBlank()) "$flagEmoji $local" else "$flagEmoji جاهز"
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "مؤشرات الشبكة والنظام • SYSTEM METRICS",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = NeonPink,
                letterSpacing = 0.5.sp,
            )

            // Row 1: Resources (Right in RTL) & Connections (Left in RTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val cpuText = monitoringOverviewState.resourceCpuPercent?.let { "CPU ${it.toInt()}%" } ?: "0.0%"
                val memText = monitoringOverviewState.resourceMemoryBytes?.toReadableBytes()?.let { " • $it" } ?: " • 0 MB"

                CyberMetricTile(
                    title = "الموارد",
                    value = "$cpuText$memText",
                    icon = Icons.Rounded.Memory,
                    iconBgColor = Color(0xFF0A2A35),
                    accentColor = CyberCyan,
                    onClick = { navigator.push(AppRoute.ResourceMonitor) },
                    modifier = Modifier.weight(1f),
                )

                val connsText = monitoringOverviewState.activeConnectionCount?.let { "$it نشط" } ?: "0 نشط"
                CyberMetricTile(
                    title = "الاتصالات",
                    value = connsText,
                    icon = Icons.Rounded.Hub,
                    iconBgColor = Color(0xFF2E1045),
                    accentColor = CyberPurpleLight,
                    onClick = { navigator.push(AppRoute.ConnectionsMonitor) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Row 2: Traffic Today (Right in RTL) & Network/IP (Left in RTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CyberMetricTile(
                    title = "البيانات اليوم",
                    value = monitoringOverviewState.todayTrafficBytes.toReadableBytes(),
                    icon = Icons.Rounded.Sync,
                    iconBgColor = Color(0xFF302208),
                    accentColor = CyberGold,
                    onClick = { navigator.push(AppRoute.TrafficMonitor) },
                    modifier = Modifier.weight(1f),
                )

                CyberMetricTile(
                    title = "الشبكة والـ IP",
                    value = networkDisplayValue,
                    icon = Icons.Rounded.Public,
                    iconBgColor = Color(0xFF0D2040),
                    accentColor = Color(0xFF448AFF),
                    onClick = { navigator.push(AppRoute.NetworkMonitor) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CyberMetricTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF10111A),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0x33FFFFFF)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconBgColor)
                    .border(0.8.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Animated cyber motion background with pulsing ambient gradient orbs
 * matching dark futuristic OLED style.
 */
@Composable
internal fun CyberAnimatedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "cyber-bg-animation")

    val orbOffset1 by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bg-orb-1",
    )

    val orbOffset2 by infiniteTransition.animateFloat(
        initialValue = 15f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bg-orb-2",
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bg-pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8E24AA).copy(alpha = pulseAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(width * 0.2f + orbOffset1, height * 0.15f + orbOffset2 * 0.5f),
                    radius = width * 0.65f,
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = pulseAlpha * 0.6f),
                        Color.Transparent,
                    ),
                    center = Offset(width * 0.85f + orbOffset2, height * 0.55f + orbOffset1 * 0.4f),
                    radius = width * 0.55f,
                ),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        themeColor.copy(alpha = pulseAlpha * 0.5f),
                        Color.Transparent,
                    ),
                    center = Offset(width * 0.35f, height * 0.85f + orbOffset1),
                    radius = width * 0.5f,
                ),
            )
        }

        content()
    }
}
