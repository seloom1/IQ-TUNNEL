// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(
    kotlinx.coroutines.FlowPreview::class,
)

package features.singbox

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.AppServices
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.modes.RunModeBpf2Socks
import app.modes.RunModeEbpf
import app.modes.RunModeTproxy
import app.modes.RunModeTun
import app.modes.RunModeTun2Socks
import app.modes.SingBoxModeDirect
import app.modes.SingBoxModeGlobal
import app.modes.SingBoxModeRule
import engine.singbox.runtime.SingBoxTrafficSample
import engine.singbox.runtime.SingBoxTrafficState
import features.home.HomeControllerState
import features.home.HomeModeRuntimeAction
import features.home.HomeMonitoringOverviewState
import features.home.HomeNetworkActivityState
import features.home.HomeNetworkRowKind
import features.home.HomeServiceStatus
import features.home.buildHomeControllerState
import features.home.buildHomeModeChange
import features.home.buildHomeModeOperationState
import features.home.buildHomeMonitoringOverviewState
import features.home.buildHomeNetworkActivityState
import features.home.formatHomeRuntimeBytes
import features.home.homeFocusTone
import features.monitoring.MonitoringIntent
import features.monitoring.ObserveMonitoring
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import ui.components.AsteriskExpressiveCard
import ui.components.AsteriskFocusSurface
import ui.components.AsteriskPageCard
import ui.components.AsteriskScaffold
import ui.components.AsteriskSegmentItem
import ui.components.AsteriskSegmentedControl
import ui.components.AsteriskTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskShapeTokens
import ui.theme.ExpressiveShapeRole
import ui.theme.FocusDensity
import utils.toReadableBytes
import kotlin.time.Duration.Companion.milliseconds
import app.navigation.Route as AppRoute
import ui.icons.AsteriskIcons as Icons
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import app.LocalHomeServiceControl
import features.home.formatHomeServiceUptime
import kotlinx.coroutines.delay

private data class HomeTrafficPresentation(
    val traffic: SingBoxTrafficState,
    val samples: List<SingBoxTrafficSample>,
)

private fun AppServices.homeTrafficPresentationSnapshot(): HomeTrafficPresentation {
    val runtimeState = singBoxRuntime.state.value
    return HomeTrafficPresentation(
        traffic = runtimeState.traffic,
        samples = singBoxRuntime.trafficHistorySnapshot(HomeNetworkHistoryLimit),
    )
}

private fun AppServices.homeMonitoringOverviewSnapshot() =
    buildHomeMonitoringOverviewState(monitoring.state.value)

@Composable
fun SingBoxDashboardPage(
    padding: PaddingValues,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val trafficPresentation by remember(services.singBoxRuntime) {
        services.singBoxRuntime.state
            .map { state -> state.traffic }
            .sample(HomeUiSampleIntervalMillis.milliseconds)
            .map { traffic ->
                HomeTrafficPresentation(
                    traffic = traffic,
                    samples = services.singBoxRuntime.trafficHistorySnapshot(HomeNetworkHistoryLimit),
                )
            }
            .distinctUntilChanged()
    }.collectAsState(initial = services.homeTrafficPresentationSnapshot())
    val monitoringOverviewState by remember(services.monitoring) {
        services.monitoring.state
            .map(::buildHomeMonitoringOverviewState)
            .distinctUntilChanged()
            .sample(HomeUiSampleIntervalMillis.milliseconds)
    }.collectAsState(initial = services.homeMonitoringOverviewSnapshot())
    val runtimeState by services.singBoxRuntime.state.collectAsState()
    val pingState by services.outboundPingRuntime.state.collectAsState()
    val activeServer = resolveActiveServerInfo(
        appState = appState,
        runtimeState = runtimeState,
        pingState = pingState,
    )
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val listState = rememberLazyListState()
    ObserveMonitoring(MonitoringIntent.Home)
    val serviceControl = LocalHomeServiceControl.current
    val serviceOperationInProgress = serviceControl.serviceOperationInProgress
    val modeOperationInProgress = serviceControl.modeOperationInProgress
    val controllerState = remember(appState.proxyRunning, appState.runMode, appState.singBoxMode) {
        buildHomeControllerState(appState)
    }
    val networkActivityState = remember(appState.proxyRunning, trafficPresentation) {
        buildHomeNetworkActivityState(
            appState = appState,
            traffic = trafficPresentation.traffic,
            networkSamples = trafficPresentation.samples,
        )
    }
    val latestAppState = rememberUpdatedState(appState)

    val modeFailedMessage = stringResource(R.string.home_mode_change_failed)

    fun changeMode(mode: Int) {
        if (serviceControl.busy) return
        val stateSnapshot = latestAppState.value
        val modeChange = buildHomeModeChange(
            appState = stateSnapshot,
            currentMode = stateSnapshot.singBoxMode,
            requestedMode = mode,
        ) ?: return
        val previousMode = stateSnapshot.singBoxMode
        if (modeChange.persistSelection) {
            updateAppState { state -> state.copy(singBoxMode = mode) }
        }
        if (modeChange.runtimeAction != HomeModeRuntimeAction.None) {
            val operationState = buildHomeModeOperationState(modeChange.runtimeAction)
            serviceControl.modeOperationInProgress = operationState.modeOperationInProgress
            services.appScope.launch {
                try {
                    val failure = when (modeChange.runtimeAction) {
                        HomeModeRuntimeAction.None -> null
                        HomeModeRuntimeAction.PatchRuntime ->
                            services.singBoxRuntime.patchMode(modeChange.runtimeAppState).exceptionOrNull()
                    }
                    failure?.let { error ->
                        if (modeChange.persistSelection) {
                            updateAppState { state ->
                                if (state.singBoxMode == mode) {
                                    state.copy(singBoxMode = previousMode)
                                } else {
                                    state
                                }
                            }
                        }
                        services.tipNotifier.showError(error, modeFailedMessage)
                    }
                } finally {
                    serviceControl.modeOperationInProgress = false
                }
            }
        }
    }

    AsteriskScaffold(
        topBar = {},
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 0.dp)

        CyberAnimatedBackground {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = listPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item("top_spacing") {
                    Spacer(Modifier.height(2.dp))
                }
                item("cyber_header") {
                    CyberTopHeader(
                        activeServer = activeServer,
                        modifier = HomeContentModifier,
                    )
                }
                item("reactor_core") {
                    CyberReactorPowerButton(
                        activeServer = activeServer,
                        modifier = HomeContentModifier,
                    )
                }
                item("server_selector") {
                    CyberServerSelectorCard(
                        activeServer = activeServer,
                        runtimeState = runtimeState,
                        modifier = HomeContentModifier,
                    )
                }
                item("network_activity") {
                    CyberLiveTrafficCard(
                        networkActivityState = networkActivityState,
                        modifier = HomeContentModifier,
                    )
                }
                item("app_exclusion") {
                    CyberAppExclusionCard(
                        navigator = navigator,
                        modifier = HomeContentModifier,
                    )
                }
                item("modes") {
                    CyberModeSelector(
                        currentMode = controllerState.singBoxMode,
                        onModeSelected = ::changeMode,
                        modifier = HomeContentModifier,
                    )
                }
                item("metrics_grid") {
                    CyberMetricsGrid(
                        monitoringOverviewState = monitoringOverviewState,
                        navigator = navigator,
                        activeServerTag = activeServer.tag,
                        modifier = HomeContentModifier,
                    )
                }
                item("bottom_spacing") {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun IqTunnelHeaderCard(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    AsteriskPageCard(
        modifier = modifier.then(HomeContentModifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "IQ TUNNEL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "بروتوكولات وأنفاق العراق المتقدمة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "VIP PROTOCOL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { uriHandler.openUri("https://t.me/FREEVPSIRAQ") },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "@FREEVPSIRAQ",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Surface(
                    onClick = { uriHandler.openUri("https://t.me/SELOOM1") },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "@SELOOM1",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeControllerCard(
    controllerState: HomeControllerState,
    networkActivityState: HomeNetworkActivityState,
    serviceOperationInProgress: Boolean,
    modeOperationInProgress: Boolean,
    onModeSelected: (Int) -> Unit,
) {
    AsteriskFocusSurface(
        title = if (controllerState.serviceStatus == HomeServiceStatus.Enabled) {
            stringResource(R.string.home_service_enabled)
        } else {
            stringResource(R.string.home_service_disabled)
        },
        modifier = HomeContentModifier,
        density = FocusDensity.Large,
        tone = homeFocusTone(controllerState.serviceStatus),
        summary = runModeLabel(controllerState.runMode),
        stateIcon = Icons.Rounded.PowerSettingsNew,
        metrics = {
            HomeFocusMetric(
                icon = Icons.Rounded.Upload,
                label = stringResource(R.string.home_accumulated_upload),
                value = formatHomeRuntimeBytes(networkActivityState.accumulatedUploadBytes),
                modifier = Modifier.weight(1f),
            )
            HomeFocusMetric(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.home_accumulated_download),
                value = formatHomeRuntimeBytes(networkActivityState.accumulatedDownloadBytes),
                modifier = Modifier.weight(1f),
            )
        },
        primaryAction = { HomeServiceRuntimeSummary() },
        forcePrimaryActionStacked = shouldStackHomeServiceRuntimeSummary(),
    ) {
        Box(modifier = Modifier.offset(y = HomeModeControlOffset)) {
            AsteriskSegmentedControl(
                items = homeModeOptions().map { option ->
                    AsteriskSegmentItem(value = option.mode, label = option.label)
                },
                selectedValue = controllerState.singBoxMode,
                onSelected = onModeSelected,
                enabled = !serviceOperationInProgress && !modeOperationInProgress,
            )
        }
    }
}

@Composable
private fun HomeFocusMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    HomeControllerItemContent(
        modifier = modifier.offset(y = HomeAccumulatedTrafficOffset),
        icon = icon,
        iconSize = HomeControllerTrafficIconSize,
        iconOffsetY = HomeControllerTrafficIconOffsetY,
        label = label,
        value = value,
    )
}

@Composable
private fun HomeControllerItemContent(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = HomeControllerItemIconSize,
    iconOffsetY: Dp = 0.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(HomeControllerItemIconSlotSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize).offset(y = iconOffsetY),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = HomeControllerItemTextSpacing),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CyberRisingArrow(
    downloadBytes: Long,
    modifier: Modifier = Modifier,
) {
    val speedRatio = (downloadBytes.toFloat() / (5f * 1024f * 1024f)).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "arrow-motion")
    val motionOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (downloadBytes > 0) -3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arrow-bounce",
    )

    val animatedAngle by animateFloatAsState(
        targetValue = 25f + speedRatio * 60f,
        label = "arrow-angle",
    )
    val animatedLift by animateFloatAsState(
        targetValue = -speedRatio * 5f,
        label = "arrow-lift",
    )

    val arrowColor = if (downloadBytes > 0) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .offset(y = (motionOffset + animatedLift).dp)
            .size(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(13.dp)
                .rotate(-animatedAngle),
        ) {
            val stroke = 2.dp.toPx()
            drawLine(
                color = arrowColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, 0f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = arrowColor,
                start = Offset(size.width * 0.45f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = arrowColor,
                start = Offset(size.width, 0f),
                end = Offset(size.width, size.height * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun NetworkActivityCard(networkActivityState: HomeNetworkActivityState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF14151F),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF1744)),
        modifier = HomeContentModifier.height(104.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (networkActivityState.hasNetworkSamples) Color(0xFF00E676) else Color(0xFFFF1744)),
                    )
                    Text(
                        text = "نشاط الشبكة • LIVE TRAFFIC",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF1744),
                        letterSpacing = 0.5.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Upload
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = Color(0xFFFF1744),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = formatHomeSpeed(networkActivityState.uploadBytesPerSecond),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF5252),
                        )
                    }

                    // Download with Rising Tilted Arrow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        CyberRisingArrow(downloadBytes = networkActivityState.downloadBytesPerSecond ?: 0L)
                        Text(
                            text = formatHomeSpeed(networkActivityState.downloadBytesPerSecond),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if ((networkActivityState.downloadBytesPerSecond ?: 0L) > 0) Color(0xFF00E676) else Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (networkActivityState.hasNetworkSamples) {
                NetworkActivityChart(
                    state = networkActivityState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.home_no_network_activity),
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkActivityChart(
    state: HomeNetworkActivityState,
    modifier: Modifier = Modifier,
) {
    val uploadColor = Color(0xFFFF1744)
    val downloadColor = Color(0xFF00E676)
    val baselineColor = Color(0x22FFFFFF)
    Canvas(modifier = modifier) {
        val samples = state.networkSamples
        val maxValue = samples.maxOfOrNull { sample -> maxOf(sample.up, sample.down) }?.coerceAtLeast(1L) ?: 1L
        val baseline = size.height - 2.dp.toPx()
        drawLine(
            color = baselineColor,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        if (samples.size < 2) return@Canvas
        val step = size.width / (samples.lastIndex.coerceAtLeast(1))
        fun point(index: Int, value: Long): Offset {
            val fraction = value.toFloat() / maxValue.toFloat()
            return Offset(index * step, baseline - fraction.coerceIn(0f, 1f) * (baseline - 4.dp.toPx()))
        }
        samples.zipWithNext().forEachIndexed { index, (first, second) ->
            drawLine(
                color = uploadColor,
                start = point(index, first.up),
                end = point(index + 1, second.up),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = downloadColor,
                start = point(index, first.down),
                end = point(index + 1, second.down),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MonitoringEntryCard(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    AsteriskExpressiveCard(
        onClick = onClick,
        modifier = modifier.height(148.dp),
        role = if (prominent) ExpressiveShapeRole.GroupLarge else ExpressiveShapeRole.ContentCard,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = AsteriskShapeTokens.SmallContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun runModeLabel(runMode: Int): String {
    return stringResource(
        when (runMode) {
            RunModeTproxy -> R.string.settings_run_mode_tproxy
            RunModeTun -> R.string.settings_run_mode_tun
            RunModeTun2Socks -> R.string.settings_run_mode_tun2socks
            RunModeBpf2Socks -> R.string.settings_run_mode_bpf2socks
            RunModeEbpf -> R.string.settings_run_mode_ebpf
            else -> R.string.settings_run_mode_vpn_service
        },
    )
}

@Composable
private fun homeResourceSummary(monitoringState: HomeMonitoringOverviewState): String {
    if (!monitoringState.serviceRunning) return stringResource(R.string.home_value_unavailable)
    val cpu = monitoringState.resourceCpuPercent
        ?.let { value -> "%.1f%%".format(value) }
        ?: stringResource(R.string.home_value_unavailable)
    val memory = monitoringState.resourceMemoryBytes
        ?.toReadableBytes()
        ?: stringResource(R.string.home_value_unavailable)
    return stringResource(R.string.home_resource_summary, cpu, memory)
}

@Composable
private fun homeNetworkSummary(homeState: HomeMonitoringOverviewState): String {
    val unavailable = stringResource(R.string.home_value_unavailable)
    val ipv4 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv4 }
        ?.value ?: unavailable
    val ipv6 = homeState.networkRows
        .firstOrNull { row -> row.kind == HomeNetworkRowKind.Ipv6 }
        ?.value ?: unavailable
    return stringResource(R.string.home_network_summary, ipv4, ipv6)
}

@Composable
private fun homeModeOptions(): List<HomeModeOption> {
    return listOf(
        HomeModeOption(
            SingBoxModeRule,
            stringResource(R.string.sing_box_mode_rule),
        ),
        HomeModeOption(
            SingBoxModeGlobal,
            stringResource(R.string.sing_box_mode_global),
        ),
        HomeModeOption(
            SingBoxModeDirect,
            stringResource(R.string.sing_box_mode_direct),
        ),
    )
}

@Composable
private fun formatHomeSpeed(bytes: Long?): String {
    return if (bytes == null) {
        formatHomeRuntimeBytes(null)
    } else {
        stringResource(R.string.monitor_speed_per_second, formatHomeRuntimeBytes(bytes))
    }
}

private data class HomeModeOption(
    val mode: Int,
    val label: String,
)

private const val HomeUiSampleIntervalMillis = 1_000L
private const val HomeNetworkHistoryLimit = 60

private val HomeContentModifier = Modifier
    .fillMaxWidth()
    .widthIn(max = 840.dp)

private val HomeControllerItemIconSlotSize = 28.dp
private val HomeControllerItemIconSize = 24.dp
private val HomeControllerTrafficIconSize = 28.dp
private val HomeControllerTrafficIconOffsetY = 0.5.dp
private val HomeAccumulatedTrafficOffset = 6.dp
private val HomeModeControlOffset = 10.dp
private val HomeControllerItemTextSpacing = 14.dp

@Composable
private fun shouldStackHomeServiceRuntimeSummary(): Boolean =
    LocalWindowInfo.current.containerSize.width / LocalDensity.current.density < 360f

@Composable
private fun HomeServiceRuntimeSummary() {
    val appState by LocalAppStateStore.current.collectAppState()
    val services = LocalAppServices.current
    val uptimeMillis by produceState<Long?>(null, appState.proxyRunning, appState.runMode) {
        value = null
        if (!appState.proxyRunning) return@produceState
        while (true) {
            value = services.singBoxRuntime.state.value.serviceStartedAtMillis
                .takeIf { it > 0L }
                ?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) }
            delay(1_000L.milliseconds)
        }
    }
    val uptime = formatHomeServiceUptime(uptimeMillis)
    if (appState.proxyRunning && uptime != null) {
        val description = stringResource(R.string.home_service_uptime, uptime)
        Text(
            text = uptime,
            modifier = Modifier.semantics { contentDescription = description },
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
