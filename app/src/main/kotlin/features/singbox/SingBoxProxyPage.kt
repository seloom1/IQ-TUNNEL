// Copyright 2026, AsteriskBOX contributors
// SPDX-License-Identifier: GPL-3.0

package features.singbox

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import app.AppServices
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.isManagedSingBoxTag
import app.managedOutboundGroupSelectorTag
import app.modes.SingBoxProxyLayoutAuto
import app.modes.SingBoxProxyLayoutDouble
import app.modes.SingBoxProxyLayoutMultiple
import app.modes.SingBoxProxyLayoutSingle
import app.modes.SingBoxProxySortDefault
import app.modes.SingBoxProxySortDelay
import app.modes.SingBoxProxySortName
import app.navigation.Route
import app.selectableManagedOutbounds
import app.withSelectorSelection
import engine.singbox.config.APP_DIRECT_OUTBOUND
import engine.singbox.config.APP_GLOBAL_SELECTOR
import features.outbound.OutboundCommandResult
import features.outbound.OutboundPingRuntimeState
import features.outbound.parseOutboundImportContent
import features.outbound.planOutboundImport
import engine.singbox.runtime.SingBoxProxiesState
import engine.singbox.runtime.SingBoxProxyGroup
import engine.singbox.runtime.SingBoxProxyNode
import engine.singbox.runtime.SingBoxRuntimeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.components.AsteriskDropdownMenuItem
import ui.components.AsteriskFilterChip
import ui.components.AsteriskInfoChip
import ui.components.AsteriskScaffold
import ui.components.AsteriskSearchTopAppBar
import ui.components.AsteriskSelectionCard
import ui.components.AsteriskTopBarControls
import ui.components.localizedLabel
import ui.isInDarkTheme
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.theme.AsteriskMotion
import kotlin.math.roundToInt
import ui.icons.AsteriskIcons as Icons

private val NeonRed = Color(0xFFFF1744)

private data class SingBoxProxyPageRuntimeState(
    val proxies: SingBoxProxiesState,
    val proxiesRefreshing: Boolean,
    val delayTestingTarget: String?,
    val delayTestingCompleted: Int,
    val delayTestingTotal: Int,
    val delayTestingBaselines: Map<String, Long>,
    val delayFailedNodes: Set<String>,
    val lastError: String,
)

private fun SingBoxRuntimeState.toProxyPageRuntimeState() = SingBoxProxyPageRuntimeState(
    proxies = proxies,
    proxiesRefreshing = proxiesRefreshing,
    delayTestingTarget = delayTestingTarget,
    delayTestingCompleted = delayTestingCompleted,
    delayTestingTotal = delayTestingTotal,
    delayTestingBaselines = delayTestingBaselines,
    delayFailedNodes = delayFailureBaselines.keys,
    lastError = lastError,
)

private fun AppServices.singBoxProxyPageRuntimeSnapshot() =
    singBoxRuntime.state.value.toProxyPageRuntimeState()

private fun buildOfflineProxies(
    appState: app.AppState,
    pingState: OutboundPingRuntimeState,
    globalGroupName: String,
): SingBoxProxiesState {
    val pingMap = pingState.entries
    val outboundNodes = appState.outbounds.map { outbound ->
        val delay = pingMap[outbound.id]?.latencyMillis?.toInt()
        SingBoxProxyNode(
            name = outbound.tag,
            type = outbound.type,
            udp = true,
            delay = delay,
        )
    }
    val endpointNodes = appState.endpoints.map { endpoint ->
        SingBoxProxyNode(
            name = endpoint.tag,
            type = endpoint.type,
            udp = true,
            delay = null,
        )
    }
    val directNode = SingBoxProxyNode(
        name = APP_DIRECT_OUTBOUND,
        type = "Direct",
        udp = true,
        delay = null,
    )
    val allNodes = (listOf(directNode) + endpointNodes + outboundNodes).distinctBy { it.name }
    val nodeByName = allNodes.associateBy { it.name }

    val globalCandidateTags = allNodes.map { it.name }
    val globalNow = appState.selectorSelections[APP_GLOBAL_SELECTOR]
        ?.takeIf(globalCandidateTags::contains)
        ?: appState.outbounds.firstOrNull()?.tag
        ?: globalCandidateTags.firstOrNull().orEmpty()

    val globalGroup = SingBoxProxyGroup(
        name = APP_GLOBAL_SELECTOR,
        displayName = globalGroupName,
        type = "Selector",
        now = globalNow,
        all = globalCandidateTags,
    )

    val customGroups = appState.outboundGroups.mapNotNull { group ->
        val groupTag = managedOutboundGroupSelectorTag(group.id, group.name)
        val members = appState.outbounds.filter { it.groupId == group.id }.map { it.tag }
        if (members.isEmpty()) return@mapNotNull null
        val now = appState.selectorSelections[groupTag]?.takeIf(members::contains)
            ?: members.firstOrNull().orEmpty()
        SingBoxProxyGroup(
            name = groupTag,
            displayName = group.name,
            type = "Selector",
            now = now,
            all = members,
        )
    }

    val groups = listOf(globalGroup) + customGroups

    return SingBoxProxiesState(
        groups = groups,
        nodes = allNodes,
        nodeByName = nodeByName,
        updatedAtMillis = System.currentTimeMillis(),
    )
}

@Composable
fun SingBoxProxyPage(
    padding: PaddingValues,
) {
    val isWideScreen = LocalIsWideScreen.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val runtimeState by remember(services.singBoxRuntime) {
        services.singBoxRuntime.state
            .map(SingBoxRuntimeState::toProxyPageRuntimeState)
            .distinctUntilChanged()
    }.collectAsState(initial = services.singBoxProxyPageRuntimeSnapshot())
    val scope = rememberCoroutineScope()
    val tipNotifier = services.tipNotifier
    val runtimeUnavailableMessage = stringResource(R.string.sing_box_proxies_runtime_unavailable)
    val selectFailedMessage = stringResource(R.string.sing_box_proxies_select_failed)
    val delayFailedMessage = stringResource(R.string.sing_box_proxies_delay_failed)
    val delayDoneMessage = stringResource(R.string.sing_box_proxies_delay_done)

    LaunchedEffect(
        services.singBoxRuntime,
        appState.proxyRunning,
        appState.runMode,
    ) {
        if (appState.proxyRunning) {
            services.singBoxRuntime.refreshProxies(appState)
        }
    }
    val runtimeProxies = runtimeState.proxies
    val pingState by services.outboundPingRuntime.state.collectAsState()
    val globalGroupName = stringResource(R.string.routing_global)
    val synthesizedProxies = remember(appState, pingState, globalGroupName) {
        buildOfflineProxies(appState, pingState, globalGroupName)
    }
    val proxies = if (runtimeProxies.groups.isNotEmpty() && appState.proxyRunning) {
        runtimeProxies
    } else {
        synthesizedProxies
    }
    val contentState = if (proxies.groups.isNotEmpty() && proxies.nodes.isNotEmpty()) {
        SingBoxProxyContentState.Ready
    } else if (runtimeState.proxiesRefreshing) {
        SingBoxProxyContentState.Loading
    } else {
        SingBoxProxyContentState.Empty
    }
    val unavailableLabel = stringResource(R.string.common_unavailable)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    var importMenuExpanded by remember { mutableStateOf(false) }

    suspend fun importRawContent(raw: String) {
        val trimmed = raw.trim()
        android.util.Log.d("IQ_TUNNEL", "importRawContent: $trimmed")
        if (trimmed.isBlank()) {
            tipNotifier.show("الحافظة فارغة أو لا تحتوي على بيانات سيرفر")
            return
        }
        try {
            val result = withContext(Dispatchers.Default) {
                parseOutboundImportContent(trimmed)
            }
            android.util.Log.d("IQ_TUNNEL", "parseOutboundImportContent result: accepted=${result.accepted.size}, issues=${result.issues.map { it.message }}")
            val snapshot = stateStore.state.value
            val effectiveSnapshot = if (snapshot.outboundGroups.isEmpty()) {
                val defaultGroup = app.OutboundGroupState(
                    id = 1,
                    name = "Default",
                    url = "",
                )
                snapshot.copy(
                    outboundGroups = listOf(defaultGroup),
                    nextOutboundGroupId = 2,
                )
            } else {
                snapshot
            }
            val targetGroupId = effectiveSnapshot.outboundGroups.firstOrNull()?.id ?: 1
            val plan = effectiveSnapshot.planOutboundImport(
                groupId = targetGroupId,
                parsed = result,
                replaceGroup = false,
                strict = false,
            )
            android.util.Log.d("IQ_TUNNEL", "planOutboundImport: committed=${plan.committed}, issues=${plan.outcome.issues.map { it.message }}")
            if (!plan.committed) {
                val issueMsg = plan.outcome.issues.firstOrNull()?.message
                tipNotifier.show("فشل استيراد السيرفر: ${issueMsg ?: "التنسيق غير مدعوم"}")
                return
            }
            val persistResult = services.outboundRepository.persistImport(snapshot, plan.state)
            android.util.Log.d("IQ_TUNNEL", "persistResult: $persistResult")
            when (persistResult) {
                OutboundCommandResult.ImportPersisted -> {
                    val count = plan.outcome.accepted.size
                    tipNotifier.show(if (count > 0) "تمت إضافة $count خادم بنجاح ⚡" else "تم استيراد الخادم بنجاح")
                }
                is OutboundCommandResult.Invalid -> {
                    android.util.Log.e("IQ_TUNNEL", "persistResult Invalid error", persistResult.error)
                    tipNotifier.show("خطأ في تكوين الخادم: ${persistResult.error.message?.take(60) ?: "بيانات غير صالحة"}")
                }
                else -> {
                    tipNotifier.show("تعذر حفظ السيرفر")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("IQ_TUNNEL", "importRawContent exception", e)
            tipNotifier.show("خطأ أثناء الاستيراد: ${e.message?.take(60) ?: "تنسيق غير مدعوم"}")
        }
    }

    val outboundChoices = selectableManagedOutbounds(appState)
    val outboundDisplayNames = outboundChoices.associate { choice ->
        choice.tag to choice.localizedLabel()
    }
    val outboundDisplayNamesWithoutGroup = outboundChoices.associate { choice ->
        choice.tag to choice.localizedLabel(includeGroupName = false)
    }
    val managedGroupNames = appState.outboundGroups.associate { group ->
        managedOutboundGroupSelectorTag(group.id, group.name) to group.name
    }
    val visibleProxies = remember(
        proxies,
        managedGroupNames,
        globalGroupName,
        outboundDisplayNames,
        unavailableLabel,
    ) {
        val prioritized = prioritizeGlobalSingBoxProxyGroup(proxies)
        prioritized.copy(
            groups = prioritized.groups.map { group ->
                group.copy(
                    displayName = managedGroupNames[group.name]
                        ?: globalGroupName.takeIf { group.name == APP_GLOBAL_SELECTOR }
                        ?: outboundDisplayNames[group.name]
                        ?: group.name.visibleRuntimeName(unavailableLabel),
                )
            },
        )
    }
    val runtimeAvailable = contentState == SingBoxProxyContentState.Ready
    val runtimeErrorDetails = stringResource(
        R.string.common_error_details,
        runtimeUnavailableMessage,
        runtimeState.lastError,
    )
    val groupNames = visibleProxies.groups.map(SingBoxProxyGroup::name)
    var selectedGroupName by rememberSaveable { mutableStateOf(groupNames.firstOrNull().orEmpty()) }
    val resolvedSelectedGroupName = selectedGroupName.takeIf { groupName -> groupName in groupNames }
        ?: groupNames.firstOrNull().orEmpty()
    val testingTarget = runtimeState.delayTestingTarget
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedGroup = visibleProxies.groups.firstOrNull { group -> group.name == resolvedSelectedGroupName }
    val proxyLayout = resolveSingBoxProxyLayout(appState.singBoxProxyLayout, isWideScreen)
    val columns = resolveSingBoxProxyColumns(proxyLayout)
    var pendingSelections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var locateRequestTrigger by remember { mutableIntStateOf(0) }
    val resolvedSelectedGroupIndex = groupNames.indexOf(resolvedSelectedGroupName).coerceAtLeast(0)
    val groupPagerState = key(groupNames) {
        rememberPagerState(
            initialPage = resolvedSelectedGroupIndex,
            pageCount = { groupNames.size.coerceAtLeast(1) },
        )
    }
    val pagerMotion = AsteriskMotion.spatial<Float>()
    val contentEffectsMotion = AsteriskMotion.effects<Float>()

    LaunchedEffect(groupNames) {
        pendingSelections = pendingSelections.filterKeys { groupName -> groupName in groupNames }
        if (selectedGroupName !in groupNames) {
            selectedGroupName = groupNames.firstOrNull().orEmpty()
        }
        val lastIndex = groupNames.lastIndex
        if (lastIndex >= 0 && groupPagerState.currentPage > lastIndex) {
            groupPagerState.scrollToPage(lastIndex)
        }
    }

    LaunchedEffect(visibleProxies.groups) {
        if (pendingSelections.isEmpty()) return@LaunchedEffect
        pendingSelections = pendingSelections.filter { (groupName, proxyName) ->
            val group = visibleProxies.groups.firstOrNull { item -> item.name == groupName } ?: return@filter false
            group.now != proxyName && proxyName in group.all
        }
    }

    LaunchedEffect(resolvedSelectedGroupName, groupNames) {
        val selectedIndex = groupNames.indexOf(resolvedSelectedGroupName)
        if (
            selectedIndex >= 0 &&
            !groupPagerState.isScrollInProgress &&
            groupPagerState.currentPage != selectedIndex
        ) {
            groupPagerState.animateScrollToPage(
                page = selectedIndex,
                animationSpec = pagerMotion,
            )
        }
    }

    LaunchedEffect(groupPagerState, groupNames) {
        snapshotFlow { groupPagerState.targetPage }
            .collect { page ->
                groupNames.getOrNull(page)?.let { groupName ->
                    if (selectedGroupName != groupName) {
                        selectedGroupName = groupName
                    }
                }
            }
    }

    fun requireRuntime(): Boolean {
        if (!runtimeAvailable) {
            val message = runtimeState.lastError.takeIf(String::isNotBlank)
                ?.let { runtimeErrorDetails }
                ?: runtimeUnavailableMessage
            scope.launch { tipNotifier.show(message) }
            return false
        }
        return true
    }

    fun selectProxy(group: SingBoxProxyGroup, node: SingBoxProxyNode) {
        val matchedOutbound = appState.outbounds.firstOrNull { it.tag == node.name || it.remarks == node.name }
        val targetTag = matchedOutbound?.tag ?: node.name
        val groupObj = appState.outboundGroups.firstOrNull { it.id == matchedOutbound?.groupId }
        val groupTag = groupObj?.let { managedOutboundGroupSelectorTag(it.id, it.name) }

        pendingSelections = pendingSelections + (group.name to targetTag) + (APP_GLOBAL_SELECTOR to targetTag)
        if (groupTag != null) {
            pendingSelections = pendingSelections + (groupTag to targetTag)
        }

        updateAppState { state ->
            var next = state.withSelectorSelection(APP_GLOBAL_SELECTOR, targetTag)
                .withSelectorSelection(group.name, targetTag)
            if (groupTag != null) {
                next = next.withSelectorSelection(groupTag, targetTag)
            }
            next
        }

        if (appState.proxyRunning) {
            scope.launch {
                runCatching {
                    services.singBoxRuntime.selectProxy(appState, group.name, targetTag)
                }
                if (groupTag != null && groupTag != group.name) {
                    runCatching {
                        services.singBoxRuntime.selectProxy(appState, groupTag, targetTag)
                    }
                }
                if (group.name != APP_GLOBAL_SELECTOR) {
                    runCatching {
                        services.singBoxRuntime.selectProxy(appState, APP_GLOBAL_SELECTOR, targetTag)
                    }
                }
            }
        }

        val displayName = matchedOutbound?.remarks?.ifBlank { matchedOutbound.type } ?: node.name
        scope.launch {
            tipNotifier.show("تم تحديد الخادم: $displayName ⚡")
        }
    }

    fun testGroup(group: SingBoxProxyGroup) {
        if (testingTarget != null) return
        val groupOutbounds = appState.outbounds.filter { it.tag in group.all }
        if (groupOutbounds.isNotEmpty()) {
            services.appScope.launch {
                services.outboundPingRuntime.start(groupOutbounds)
            }
        }
        if (appState.proxyRunning) {
            val stateSnapshot = appState
            val groupName = group.name
            services.appScope.launch {
                services.singBoxRuntime.testGroupDelay(stateSnapshot, groupName)
                    .onSuccess { tipNotifier.show(delayDoneMessage) }
                    .onFailure { error -> tipNotifier.showError(error, delayFailedMessage) }
            }
        }
    }

    fun testProxy(node: SingBoxProxyNode) {
        if (testingTarget != null) return
        val matchedOutbound = appState.outbounds.firstOrNull { it.tag == node.name }
        if (matchedOutbound != null) {
            services.appScope.launch {
                services.outboundPingRuntime.start(listOf(matchedOutbound))
            }
        }
        if (appState.proxyRunning) {
            val stateSnapshot = appState
            val proxyName = node.name
            services.appScope.launch {
                services.singBoxRuntime.testProxyDelay(stateSnapshot, proxyName)
                    .onSuccess { result ->
                        val message = if (didSingBoxProxyDelayTestSucceed(result)) {
                            delayDoneMessage
                        } else {
                            delayFailedMessage
                        }
                        tipNotifier.show(message)
                    }
                    .onFailure { error -> tipNotifier.showError(error, delayFailedMessage) }
            }
        }
    }

    AsteriskScaffold(
        topBar = {
            Column {
                AsteriskSearchTopAppBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = stringResource(R.string.sing_box_proxies_search),
                    searchAvailable = contentState == SingBoxProxyContentState.Ready,
                    title = { Text(stringResource(R.string.sing_box_proxies_title)) },
                    actions = {
                        Box {
                            IconButton(
                                onClick = { importMenuExpanded = true },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "إضافة سيرفر",
                                    tint = NeonRed,
                                )
                            }
                            DropdownMenu(
                                expanded = importMenuExpanded,
                                onDismissRequest = { importMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("استيراد من الحافظة (Clipboard)") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = NeonRed) },
                                    onClick = {
                                        importMenuExpanded = false
                                        scope.launch {
                                            val text = clipboard.getText()?.text.orEmpty()
                                            importRawContent(text)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("مسح رمز QR") },
                                    leadingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = NeonRed) },
                                    onClick = {
                                        importMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val qr = services.qrCodeScanner()?.trim()
                                                if (!qr.isNullOrBlank()) {
                                                    importRawContent(qr)
                                                }
                                            } catch (_: Exception) {
                                                tipNotifier.show("تعذر مسح رمز QR")
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("استيراد ملف إعدادات") },
                                    leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = NeonRed) },
                                    onClick = {
                                        importMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val uri = services.importFilePicker() ?: return@launch
                                                val content = withContext(Dispatchers.IO) {
                                                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                                }
                                                if (!content.isNullOrBlank()) {
                                                    importRawContent(content)
                                                }
                                            } catch (_: Exception) {
                                                tipNotifier.show("تعذر قراءة الملف")
                                            }
                                        }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("إدارة الخوادم وإضافة يدوية...") },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        importMenuExpanded = false
                                        navigator.push(Route.OutboundList)
                                    },
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = contentState == SingBoxProxyContentState.Ready,
                            enter = AsteriskMotion.fadeEnter(contentEffectsMotion),
                            exit = AsteriskMotion.fadeExit(contentEffectsMotion),
                        ) {
                            SingBoxProxyOptionsMenu(
                                layout = appState.singBoxProxyLayout,
                                sort = resolveSingBoxProxySort(appState.singBoxProxySort),
                                onLayoutChange = { layout ->
                                    updateAppState { state ->
                                        state.copy(singBoxProxyLayout = layout)
                                    }
                                },
                                onSortChange = { sort ->
                                    updateAppState { state ->
                                        state.copy(singBoxProxySort = sort)
                                    }
                                },
                            )
                        }
                    },
                )
                AnimatedVisibility(
                    visible = contentState == SingBoxProxyContentState.Ready && visibleProxies.groups.size > 1,
                    enter = AsteriskMotion.contentEnter(),
                    exit = AsteriskMotion.contentExit(),
                ) {
                    AsteriskTopBarControls {
                        if (visibleProxies.groups.size > 1) {
                            ProxyGroupTabs(
                                groups = visibleProxies.groups,
                                selectedGroupName = resolvedSelectedGroupName,
                                onSelectedGroupNameChange = { selectedGroupName = it },
                                onReselectCurrentGroup = { locateRequestTrigger++ },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { importMenuExpanded = true },
                containerColor = NeonRed,
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "إضافة سيرفر",
                )
            }
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding, bottomExtra = 104.dp)

        AnimatedContent(
            targetState = contentState == SingBoxProxyContentState.Empty,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = AsteriskMotion.fadeThrough(contentEffectsMotion),
            label = "proxy-service-availability",
        ) { empty ->
            if (empty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    SingBoxProxyEmptyCard(onAddClick = { importMenuExpanded = true })
                }
            } else {
                Box {
                    HorizontalPager(
                        state = groupPagerState,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        val group = visibleProxies.groups.getOrNull(page)
                        val pageDisplayNames = if (
                            group != null && group.name in managedGroupNames
                        ) {
                            outboundDisplayNamesWithoutGroup
                        } else {
                            outboundDisplayNames
                        }
                        val pageNodes = remember(
                            group,
                            visibleProxies,
                            searchQuery,
                            appState.singBoxProxySort,
                            pageDisplayNames,
                            runtimeState.delayTestingBaselines,
                            runtimeState.delayFailedNodes,
                        ) {
                            reduceSingBoxProxyNodeNames(
                                group = group,
                                proxies = visibleProxies,
                                query = searchQuery,
                                sort = resolveSingBoxProxySort(appState.singBoxProxySort),
                                displayNames = pageDisplayNames,
                                testingBaselines = runtimeState.delayTestingBaselines,
                                failedNodes = runtimeState.delayFailedNodes,
                            )
                        }
                        val selectedNodeName = (pendingSelections[group?.name] ?: group?.now).orEmpty().takeIf(String::isNotBlank)
                        val pageGridState = rememberLazyGridState()
                        val isCurrentPage = groupPagerState.currentPage == page

                        LaunchedEffect(locateRequestTrigger) {
                            if (locateRequestTrigger == 0 || !isCurrentPage) return@LaunchedEffect
                            if (group == null || pageNodes.isEmpty() || selectedNodeName == null) return@LaunchedEffect
                            val selectedIndex = pageNodes.indexOf(selectedNodeName)
                            if (selectedIndex < 0) return@LaunchedEffect
                            val targetItemIndex = (selectedIndex / columns) * columns
                            val firstVisible = pageGridState.firstVisibleItemIndex
                            val distance = kotlin.math.abs(firstVisible - targetItemIndex)
                            if (distance > 12) {
                                val preIndex = if (targetItemIndex > firstVisible) {
                                    (targetItemIndex - columns).coerceAtLeast(0)
                                } else {
                                    (targetItemIndex + columns).coerceAtMost(pageNodes.lastIndex)
                                }
                                pageGridState.scrollToItem(preIndex)
                            }
                            pageGridState.animateScrollToItem(targetItemIndex)
                        }

                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = pageGridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = listPadding,
                                verticalArrangement = Arrangement.spacedBy(SingBoxProxyNodeGridSpacing),
                                horizontalArrangement = Arrangement.spacedBy(SingBoxProxyNodeGridSpacing),
                            ) {
                                if (group == null) {
                                    item(
                                        key = "empty",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        if (contentState == SingBoxProxyContentState.Loading) {
                                            SingBoxProxyLoadingCard()
                                        } else {
                                            SingBoxProxyEmptyCard(onAddClick = { importMenuExpanded = true })
                                        }
                                    }
                                } else if (pageNodes.isEmpty()) {
                                    item(
                                        key = "group_empty:${group.name}",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        SingBoxProxyEmptyCard(onAddClick = { importMenuExpanded = true })
                                    }
                                } else {
                                    items(
                                        items = pageNodes,
                                        key = { nodeName -> "${group.name}:$nodeName" },
                                    ) { nodeName ->
                                        val node = proxies.node(nodeName)
                                        val selectionBehavior =
                                            resolveSingBoxProxySelectionBehavior(
                                                group = group,
                                                runtimeAvailable = true,
                                            )
                                        val onSelect: (() -> Unit)? =
                                            if (selectionBehavior.canSelect) {
                                                { selectProxy(group, node) }
                                            } else {
                                                null
                                            }
                                        SingBoxProxyNodeCard(
                                            modifier = Modifier
                                                .animateItem()
                                                .fillMaxWidth(),
                                            node = node,
                                            displayName = pageDisplayNames[node.name]
                                                ?: node.name.visibleRuntimeName(unavailableLabel),
                                            selected = isSingBoxProxyNodeCurrent(
                                                group = group,
                                                nodeName = node.name,
                                                pendingSelections = pendingSelections,
                                            ),
                                            selectionEnabled = selectionBehavior.cardEnabled,
                                            delayTestEnabled = testingTarget == null,
                                            compact = columns > 1,
                                            delayStatus = resolveSingBoxProxyDelayStatus(
                                                nodeName = node.name,
                                                delay = node.delay,
                                                delayUpdatedAtEpochSeconds =
                                                    node.delayUpdatedAtEpochSeconds,
                                                testingBaselines =
                                                    runtimeState.delayTestingBaselines,
                                                failedNodes = runtimeState.delayFailedNodes,
                                            ),
                                            testing = testingTarget == node.name,
                                            onSelect = onSelect,
                                            onDelayTest = { testProxy(node) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    selectedGroup?.let { group ->
                        ProxyFloatingActions(
                            runtimeAvailable = runtimeAvailable,
                            testing = testingTarget == group.name,
                            testingCompleted = runtimeState.delayTestingCompleted,
                            testingTotal = runtimeState.delayTestingTotal,
                            canLocate = (pendingSelections[group.name] ?: group.now).isNotBlank(),
                            onLocate = { locateRequestTrigger++ },
                            onDelayTest = { testGroup(group) },
                            bottomPadding = contentPadding.calculateBottomPadding(),
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SingBoxProxyServiceStoppedState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = ui.theme.AsteriskShapeTokens.Pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.sing_box_proxies_service_stopped_title),
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.sing_box_proxies_service_stopped_summary),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProxyGroupTabs(
    groups: List<SingBoxProxyGroup>,
    selectedGroupName: String,
    onSelectedGroupNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReselectCurrentGroup: () -> Unit = {},
) {
    if (groups.isEmpty()) return
    val tabScrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var tabBounds by remember { mutableStateOf<Map<String, ProxyGroupTabBounds>>(emptyMap()) }
    val selectedBounds = tabBounds[selectedGroupName]

    LaunchedEffect(selectedGroupName, selectedBounds, viewportWidthPx, tabScrollState.maxValue) {
        if (selectedBounds == null || viewportWidthPx <= 0) return@LaunchedEffect
        val targetScroll = resolveProxyTabScrollTarget(
            visibleStart = tabScrollState.value,
            viewportWidth = viewportWidthPx,
            tabStart = selectedBounds.leftPx,
            tabEnd = selectedBounds.leftPx + selectedBounds.widthPx,
            maxScroll = tabScrollState.maxValue,
        )
        if (targetScroll != tabScrollState.value) {
            tabScrollState.animateScrollTo(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size -> viewportWidthPx = size.width }
            .horizontalScroll(tabScrollState),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                AsteriskFilterChip(
                    selected = group.name == selectedGroupName,
                    onClick = {
                        if (group.name == selectedGroupName) {
                            onReselectCurrentGroup()
                        } else {
                            onSelectedGroupNameChange(group.name)
                        }
                    },
                    label = group.displayName,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val bounds = ProxyGroupTabBounds(
                            leftPx = coordinates.positionInParent().x.roundToInt(),
                            widthPx = coordinates.size.width,
                        )
                        if (tabBounds[group.name] != bounds) {
                            tabBounds = tabBounds + (group.name to bounds)
                        }
                    },
                )
            }
        }
    }
}

internal fun resolveProxyTabScrollTarget(
    visibleStart: Int,
    viewportWidth: Int,
    tabStart: Int,
    tabEnd: Int,
    maxScroll: Int,
): Int {
    val visibleEnd = visibleStart + viewportWidth
    return when {
        tabStart < visibleStart -> tabStart
        tabEnd > visibleEnd -> tabEnd - viewportWidth
        else -> visibleStart
    }.coerceIn(0, maxScroll)
}

private data class ProxyGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

@Composable
private fun SingBoxProxyOptionsMenu(
    layout: Int,
    sort: Int,
    onLayoutChange: (Int) -> Unit,
    onSortChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var level by rememberSaveable { mutableStateOf(ProxyOptionsLevel.Main) }
    val dismissMenu = {
        expanded = false
        level = ProxyOptionsLevel.Main
    }
    val layoutLabel = stringResource(
        when (layout) {
            SingBoxProxyLayoutSingle -> R.string.sing_box_proxies_option_layout_single
            SingBoxProxyLayoutDouble -> R.string.sing_box_proxies_option_layout_double
            SingBoxProxyLayoutMultiple -> R.string.sing_box_proxies_option_layout_multiple
            else -> R.string.sing_box_proxies_option_layout_auto
        },
    )
    val sortLabel = stringResource(
        when (sort) {
            SingBoxProxySortName -> R.string.sing_box_proxies_option_sort_name
            SingBoxProxySortDelay -> R.string.sing_box_proxies_option_sort_delay
            else -> R.string.sing_box_proxies_option_sort_default
        },
    )
    val menuSpatialMotion = AsteriskMotion.fastSpatial<IntOffset>()
    val menuSizeMotion = AsteriskMotion.fastSpatial<IntSize>()
    val menuEffectsMotion = AsteriskMotion.fastEffects<Float>()
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.sing_box_proxies_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = dismissMenu,
            modifier = Modifier.width(SingBoxProxyOptionsMenuWidth),
        ) {
            AnimatedContent(
                targetState = level,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = AsteriskMotion.horizontalSlideFade(
                    spatialSpec = menuSpatialMotion,
                    effectsSpec = menuEffectsMotion,
                    sizeSpec = menuSizeMotion,
                ) {
                    if (targetState == ProxyOptionsLevel.Main) -1 else 1
                },
                contentAlignment = Alignment.TopStart,
                label = "proxy-options-level",
            ) { currentLevel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (currentLevel) {
                ProxyOptionsLevel.Main -> {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.sing_box_proxies_option_layout))
                                Text(
                                    layoutLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { level = ProxyOptionsLevel.Layout },
                        leadingIcon = { Icon(Icons.Rounded.ViewModule, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.sing_box_proxies_option_sort))
                                Text(
                                    sortLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { level = ProxyOptionsLevel.Sort },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
                        trailingIcon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                    )
                }

                ProxyOptionsLevel.Layout -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sing_box_proxies_option_layout)) },
                        onClick = { level = ProxyOptionsLevel.Main },
                        leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                    )
                    HorizontalDivider()
                    listOf(
                        Triple(
                            SingBoxProxyLayoutAuto,
                            R.string.sing_box_proxies_option_layout_auto,
                            Icons.Rounded.AutoAwesome,
                        ),
                        Triple(
                            SingBoxProxyLayoutSingle,
                            R.string.sing_box_proxies_option_layout_single,
                            Icons.Rounded.ViewAgenda,
                        ),
                        Triple(
                            SingBoxProxyLayoutDouble,
                            R.string.sing_box_proxies_option_layout_double,
                            Icons.Rounded.ViewColumn,
                        ),
                        Triple(
                            SingBoxProxyLayoutMultiple,
                            R.string.sing_box_proxies_option_layout_multiple,
                            Icons.Rounded.GridView,
                        ),
                    ).forEach { (value, label, _) ->
                        AsteriskDropdownMenuItem(
                            text = stringResource(label),
                            selected = layout == value,
                            onClick = {
                                dismissMenu()
                                onLayoutChange(value)
                            },
                        )
                    }
                }

                ProxyOptionsLevel.Sort -> {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sing_box_proxies_option_sort)) },
                        onClick = { level = ProxyOptionsLevel.Main },
                        leadingIcon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                    )
                    HorizontalDivider()
                    listOf(
                        Triple(
                            SingBoxProxySortDefault,
                            R.string.sing_box_proxies_option_sort_default,
                            Icons.AutoMirrored.Rounded.Sort,
                        ),
                        Triple(
                            SingBoxProxySortName,
                            R.string.sing_box_proxies_option_sort_name,
                            Icons.Rounded.SortByAlpha,
                        ),
                        Triple(
                            SingBoxProxySortDelay,
                            R.string.sing_box_proxies_option_sort_delay,
                            Icons.Rounded.Speed,
                        ),
                    ).forEach { (value, label, _) ->
                        AsteriskDropdownMenuItem(
                            text = stringResource(label),
                            selected = sort == value,
                            onClick = {
                                dismissMenu()
                                onSortChange(value)
                            },
                        )
                    }
                }
                    }
                }
            }
        }
    }
}

private enum class ProxyOptionsLevel {
    Main,
    Layout,
    Sort,
}

private val SingBoxProxyOptionsMenuWidth = 224.dp

@Composable
private fun SingBoxProxyNodeCard(
    node: SingBoxProxyNode,
    displayName: String,
    selected: Boolean,
    selectionEnabled: Boolean,
    delayTestEnabled: Boolean,
    compact: Boolean,
    delayStatus: SingBoxProxyDelayStatus,
    testing: Boolean,
    onSelect: (() -> Unit)?,
    onDelayTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize().padding(SingBoxProxyNodeCardPadding),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayName,
                    modifier = Modifier.weight(1f),
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                        )
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            ProtocolDelayLine(
                protocol = node.type,
                delay = node.delay,
                delayStatus = delayStatus,
                selected = selected,
                testing = testing,
                enabled = delayTestEnabled,
                compact = compact,
                onClick = onDelayTest,
            )
        }
    }
    val cardModifier = modifier
        .height(SingBoxProxyNodeCardHeight)
        .semantics { this.selected = selected }
    AsteriskSelectionCard(
        selected = selected,
        enabled = selectionEnabled,
        onClick = onSelect,
        modifier = cardModifier,
    ) {
        content()
    }
}

@Composable
private fun ProtocolDelayLine(
    protocol: String,
    delay: Int?,
    delayStatus: SingBoxProxyDelayStatus,
    selected: Boolean,
    testing: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val delayText = when (delayStatus) {
        SingBoxProxyDelayStatus.NotTested ->
            stringResource(R.string.sing_box_proxies_delay_not_tested)
        SingBoxProxyDelayStatus.Testing ->
            stringResource(R.string.sing_box_proxies_delay_testing)
        SingBoxProxyDelayStatus.Measured -> delay?.let { measuredDelay ->
            stringResource(R.string.monitor_milliseconds, measuredDelay)
        } ?: stringResource(R.string.sing_box_proxies_delay_not_tested)
        SingBoxProxyDelayStatus.Failed ->
            stringResource(R.string.sing_box_proxies_delay_status_failed)
    }.trim()
    val viewConfiguration = LocalViewConfiguration.current
    val chipTouchTargetViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val minimumTouchTargetSize = DpSize(28.dp, 28.dp)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides chipTouchTargetViewConfiguration) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(ui.theme.AsteriskShapeTokens.Pill)
                .clickable(enabled = enabled, onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsteriskInfoChip(
                text = protocol.displaySingBoxProtocolName(compact = compact),
                modifier = Modifier.weight(1f, fill = false),
                emphasized = selected,
                textStyle = if (compact) {
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                } else {
                    MaterialTheme.typography.labelSmall
                },
            )
            Box(
                modifier = Modifier.padding(end = 8.dp).height(28.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(
                        text = delayText,
                        style = if (compact) {
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        fontWeight = FontWeight.Medium,
                        color = delayColor(delayStatus, delay),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun String.visibleRuntimeName(unavailableLabel: String): String {
    return if (isManagedSingBoxTag(this)) {
        unavailableLabel
    } else {
        this
    }
}

@Composable
private fun ProxyFloatingActions(
    runtimeAvailable: Boolean,
    testing: Boolean,
    testingCompleted: Int,
    testingTotal: Int,
    canLocate: Boolean,
    onLocate: () -> Unit,
    onDelayTest: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + SingBoxFloatingToolbarBottomSpacing,
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canLocate) {
            FloatingActionButton(
                onClick = onLocate,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.sing_box_proxies_locate_selected),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!testing) onDelayTest() },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(
                alpha = if (!testing) 1f else 0.45f,
            ),
            icon = {
                // Fixed 24.dp slot: the spinner (20.dp) and the glyph (24.dp)
                // must occupy the same footprint, or the button length
                // changes when the test starts/stops.
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        DelayToolbarGlyph()
                    }
                }
            },
            text = {
                // The text slot is pinned to a fixed width — the wider of
                // "整组测试" and the widest progress value ("total/total") —
                // and centered, so the button length is IDENTICAL whether
                // it shows the label or the ticking progress. softWrap off:
                // the numbers must never fold onto a second line.
                val labelText = stringResource(R.string.sing_box_proxies_group_test)
                val testingInProgress = testing && testingTotal > 0
                val progressText = if (testingInProgress) {
                    stringResource(
                        R.string.sing_box_proxies_delay_testing_progress,
                        testingCompleted.coerceAtMost(testingTotal),
                        testingTotal,
                    )
                } else {
                    null
                }
                val widestProgressText = if (testingInProgress) {
                    stringResource(
                        R.string.sing_box_proxies_delay_testing_progress,
                        testingTotal,
                        testingTotal,
                    )
                } else {
                    null
                }
                val textMeasurer = rememberTextMeasurer()
                val textStyle = LocalTextStyle.current
                val slotWidthPx = remember(textMeasurer, labelText, widestProgressText, textStyle) {
                    val labelWidth = textMeasurer.measure(labelText, textStyle).size.width
                    val progressWidth = widestProgressText?.let { widest ->
                        textMeasurer.measure(widest, textStyle).size.width
                    } ?: 0
                    maxOf(labelWidth, progressWidth)
                }
                val density = LocalDensity.current
                Text(
                    text = progressText ?: labelText,
                    modifier = Modifier.width(with(density) { slotWidthPx.toDp() }),
                    textAlign = TextAlign.Center,
                    softWrap = false,
                    maxLines = 1,
                )
            },
        )
    }
}

@Composable
private fun DelayToolbarGlyph() {
    Icon(
        imageVector = Icons.Rounded.Speed,
        contentDescription = stringResource(R.string.sing_box_proxies_group_test),
    )
}

@Composable
private fun SingBoxProxyEmptyCard(onAddClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.VpnLock,
            contentDescription = null,
            tint = NeonRed.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp),
        )
        Text(
            text = "لا توجد خوادم مضافة حالياً",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "يمكنك إضافة خوادم فوراً بدون الحاجة للاتصال مسبقاً",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ExtendedFloatingActionButton(
            onClick = onAddClick,
            containerColor = NeonRed,
            contentColor = Color.White,
            shape = CircleShape,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("إضافة خادم جديد (+)", fontWeight = FontWeight.Bold) },
        )
    }
}

@Composable
private fun SingBoxProxyLoadingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(R.string.sing_box_proxies_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun delayColor(
    delayStatus: SingBoxProxyDelayStatus,
    delay: Int?,
): Color {
    val darkTheme = isInDarkTheme()
    return when (delayStatus) {
        SingBoxProxyDelayStatus.NotTested, SingBoxProxyDelayStatus.Testing -> MaterialTheme.colorScheme.onSurfaceVariant
        SingBoxProxyDelayStatus.Failed -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
        SingBoxProxyDelayStatus.Measured -> when {
            delay == null -> MaterialTheme.colorScheme.onSurfaceVariant
            delay < 0 -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
            delay < 300 -> if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C)
            delay < 600 -> if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00)
            delay < 900 -> if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400)
            else -> if (darkTheme) Color(0xFFF12522) else Color(0xFFE94634)
        }
    }
}

private val SingBoxProxyNodeCardHeight = 112.dp
private val SingBoxProxyNodeCardPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 10.dp)
private val SingBoxProxyNodeGridSpacing = 12.dp
private val SingBoxFloatingToolbarBottomSpacing = 16.dp
