package com.example.presentation.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.RouterProfileEntity
import com.example.domain.model.LogEntry
import com.example.domain.model.LogLevel
import com.example.presentation.home.HomeViewModel
import com.example.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenCompose(
    viewModel: HomeViewModel,
    onDrawerClick: () -> Unit,
    onNavigateToRouters: () -> Unit,
    onNavigateToTest: () -> Unit,
    onStopTest: () -> Unit = {},
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val routers by viewModel.routers.collectAsState()
    val selectedRouterId by viewModel.selectedRouterId.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val stats by viewModel.statistics.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var prefix by remember { mutableStateOf("D") }
    var codeLength by remember { mutableStateOf("6") }
    var charset by remember { mutableStateOf("0123456789") }
    var count by remember { mutableStateOf("50") }
    var showRouterDialog by remember { mutableStateOf(false) }

    // Load initial settings
    LaunchedEffect(Unit) {
        val s = viewModel.getInitialSettings()
        prefix = s.prefix
        codeLength = s.length.toString()
        charset = s.charset
        count = s.count.toString()
    }

    // Auto-select router if not yet selected
    LaunchedEffect(routers) {
        if (selectedRouterId == -1L && routers.isNotEmpty()) {
            val initial = viewModel.getInitialSettings()
            val def = routers.firstOrNull { it.id == initial.defaultRouterId } ?: routers.first()
            viewModel.selectRouter(def.id)
        }
    }

    val currentRouter = routers.firstOrNull { it.id == selectedRouterId } ?: routers.firstOrNull()
    val isServiceRunning by viewModel.isServiceRunning().collectAsState()
    val isTesting = (uiState is HomeViewModel.UiState.Testing) && isServiceRunning

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // 1. Authentic Home Header (Image as-is, ONLY Drawer button overlay)
        if (showTopHeader) {
            HomeHeader(onDrawerClick = onDrawerClick)
        }

        // 2. Scrollable Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // A. Connection Status Card
            val isConnected = connectionState == HomeViewModel.ConnectionState.CONNECTED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(
                        1.5.dp,
                        if (isConnected) NeonGreen else NeonRed.copy(alpha = 0.5f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isConnected) NeonGreen.copy(alpha = 0.12f) else NeonRed.copy(alpha = 0.12f))
                                .border(1.dp, if (isConnected) NeonGreen.copy(alpha = 0.35f) else NeonRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = if (isConnected) R.drawable.ic_wifi_connected else R.drawable.ic_wifi_disconnected),
                                contentDescription = "Connection",
                                tint = if (isConnected) NeonGreen else NeonRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isConnected) stringResource(R.string.home_router_connected) else stringResource(R.string.home_router_disconnected),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (isConnected) stringResource(R.string.home_router_connected_desc) else stringResource(R.string.home_router_disconnected_desc),
                                color = if (isConnected) NeonGreen else NeonRed.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Detail",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // B. Active Router Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: "اختر ملف الراوتر المستهدف" with red target icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.home_select_target_router),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_router),
                        contentDescription = "Target Router",
                        tint = NeonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Dropdown Card (Router Selector)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070A10))
                        .border(1.dp, Color(0xFF161E2E), RoundedCornerShape(12.dp))
                        .clickable { showRouterDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "▾",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = currentRouter?.name ?: "-",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_router_3d),
                                contentDescription = "Router",
                                tint = NeonRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // IP & Login Path Bar with Copy Button
                val clipboard = LocalClipboardManager.current
                val ipPathText = "IP : ${currentRouter?.ip ?: "www.web.com"} | Path : ${currentRouter?.loginPath ?: "/login"}"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070A10))
                        .border(1.dp, Color(0xFF161E2E), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ipPathText,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_export),
                            contentDescription = "Copy",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    clipboard.setText(AnnotatedString(ipPathText))
                                }
                        )
                    }
                }
            }

            // C. Hotspot Card Generation Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_settings),
                        contentDescription = "Settings",
                        tint = NeonRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_card_gen_settings),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Prefix Row
                InputRowItem(
                    label = stringResource(R.string.home_hint_prefix),
                    iconRes = R.drawable.ic_tag,
                    value = prefix,
                    onValueChange = {
                        prefix = it
                        viewModel.saveSettingsQuickly(prefix, codeLength.toIntOrNull() ?: 6, count.toIntOrNull() ?: 50, charset, selectedRouterId)
                    }
                )

                // Length Row
                InputRowItem(
                    label = stringResource(R.string.home_hint_length),
                    iconRes = R.drawable.ic_hash,
                    value = codeLength,
                    onValueChange = {
                        codeLength = it
                        viewModel.saveSettingsQuickly(prefix, codeLength.toIntOrNull() ?: 6, count.toIntOrNull() ?: 50, charset, selectedRouterId)
                    }
                )

                // Charset Row
                InputRowItem(
                    label = stringResource(R.string.home_hint_charset),
                    iconRes = R.drawable.ic_info,
                    value = charset,
                    onValueChange = {
                        charset = it
                        viewModel.saveSettingsQuickly(prefix, codeLength.toIntOrNull() ?: 6, count.toIntOrNull() ?: 50, charset, selectedRouterId)
                    }
                )

                // Count Row
                InputRowItem(
                    label = stringResource(R.string.home_hint_count),
                    iconRes = R.drawable.ic_timer,
                    value = count,
                    onValueChange = {
                        count = it
                        viewModel.saveSettingsQuickly(prefix, codeLength.toIntOrNull() ?: 6, count.toIntOrNull() ?: 50, charset, selectedRouterId)
                    }
                )
            }

            // D. Metric Statistics Triad (Total, Success with 2-devices breakdown, Failed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Failed (Red)
                StatCardItem(
                    title = stringResource(R.string.home_stat_failure),
                    count = stats.failure.toString(),
                    subText = stringResource(R.string.home_failed_cards_sub),
                    iconRes = R.drawable.ic_cross_circle_red,
                    color = NeonRed,
                    modifier = Modifier.weight(1f)
                )

                // Success (Green)
                val successSub = if (stats.twoDevicesSuccessCount > 0) {
                    stringResource(R.string.home_stat_success_with_two_devices, stats.normalSuccessCount, stats.twoDevicesSuccessCount)
                } else {
                    stringResource(R.string.home_success_cards_sub)
                }
                StatCardItem(
                    title = stringResource(R.string.home_stat_success),
                    count = stats.success.toString(),
                    subText = successSub,
                    iconRes = R.drawable.ic_check_circle_green,
                    color = NeonGreen,
                    modifier = Modifier.weight(1f)
                )

                // Total (Blue)
                StatCardItem(
                    title = stringResource(R.string.home_stat_total),
                    count = stats.total.toString(),
                    subText = stringResource(R.string.home_total_cards_sub),
                    iconRes = R.drawable.ic_antenna_tower,
                    color = NeonBlue,
                    modifier = Modifier.weight(1f)
                )
            }

            // E. Progress Indicator (shown ONLY when test is actively running)
            if (isTesting) {
                val pct = if (stats.total > 0) ((stats.success + stats.failure) * 100 / stats.total) else 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NeonRed,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.home_scan_progress),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = NeonRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Progress Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF161E2E))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((pct / 100f).coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(NeonRed, NeonRedDark)
                                    )
                                )
                        )
                    }
                }
            }

            // F. Action Controls (Start Test & Stop Test)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stop Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isTesting) Color(0xFF140306) else Color(0xFF0F1420))
                        .border(1.5.dp, if (isTesting) NeonRed else SurfaceBorder, RoundedCornerShape(16.dp))
                        .clickable(enabled = isTesting) { onStopTest() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_stop_modern),
                            contentDescription = "Stop",
                            tint = if (isTesting) NeonRed else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_stop_scan),
                            color = if (isTesting) NeonRed else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Start Button (Vibrant Glowing Gradient)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isTesting) {
                                Brush.horizontalGradient(colors = listOf(Color(0xFF1E283C), Color(0xFF141A28)))
                            } else {
                                Brush.horizontalGradient(colors = listOf(NeonRed, NeonRedDark))
                            }
                        )
                        .clickable(enabled = !isTesting) {
                            val c = count.toIntOrNull() ?: 50
                            val l = codeLength.toIntOrNull() ?: 6
                            viewModel.generateAndStart(
                                prefix = prefix,
                                length = l,
                                count = c,
                                charset = charset,
                                delayMs = 1200L
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_play_modern),
                            contentDescription = "Start",
                            tint = if (isTesting) TextMuted else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isTesting) stringResource(R.string.home_scanning_active) else stringResource(R.string.home_start_scan),
                            color = if (isTesting) TextMuted else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // G. Live Operations Terminal Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_antenna_tower),
                            contentDescription = "Terminal",
                            tint = NeonRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_live_log_title),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { 
                            if (!viewModel.isServiceRunning().value) {
                                viewModel.clearLogs()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_clear_log),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Terminal Display Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF05080E))
                        .border(1.dp, Color(0xFF141A28), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    if (logEntries.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.empty_search),
                                contentDescription = "Empty Logs",
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_no_logs_yet),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.home_logs_appear_here),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(logEntries.size) {
                            if (logEntries.isNotEmpty()) {
                                listState.animateScrollToItem(logEntries.size - 1)
                            }
                        }
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(logEntries) { entry ->
                                val color = when (entry.level) {
                                    LogLevel.SUCCESS, LogLevel.SUCCESS_TWO_DEVICES -> NeonGreen
                                    LogLevel.WARNING -> NeonAmber
                                    LogLevel.ERROR -> NeonRed
                                    else -> NeonBlue
                                }
                                Text(
                                    text = "[${DateUtils.formatTime(entry.timestamp)}] ${entry.message}",
                                    color = color,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Router Selector Dialog
    if (showRouterDialog) {
        AlertDialog(
            onDismissRequest = { showRouterDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF26080B))
                        .border(1.5.dp, NeonRed, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_router_3d),
                        contentDescription = "Router",
                        tint = NeonRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.home_select_router_dialog_title),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routers.forEach { r ->
                        val isSel = r.id == selectedRouterId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSel) NeonRed.copy(alpha = 0.15f) else SurfaceItem)
                                .border(1.2.dp, if (isSel) NeonRed else SurfaceBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.selectRouter(r.id)
                                showRouterDialog = false
                            }
                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = r.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "IP: ${r.ip} · ${r.strategyId.uppercase()}",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (isSel) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check_circle_green),
                                    contentDescription = "Selected",
                                    tint = NeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(NeonRed, NeonRedDark)))
                        .clickable {
                            showRouterDialog = false
                            onNavigateToRouters()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_router_config_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.40f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161E2E))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                        .clickable { showRouterDialog = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, SurfaceBorder, RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun InputRowItem(
    label: String,
    iconRes: Int,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Input Box
        Box(
            modifier = Modifier
                .width(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceItem)
                .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                ),
                singleLine = true
            )
        }

        // Label with vector icon badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141A28))
                    .border(1.dp, Color(0xFF222C42), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = NeonRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun StatCardItem(
    title: String,
    count: String,
    subText: String,
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.5.dp, color.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subText,
            color = TextSecondary,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}
