package com.example.presentation.compose

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.test.TestViewModel
import com.example.service.TestService

/**
 * Authentic Test Screen matching reference repository & design.
 * Features live WebView screen streaming, status & progress tracking,
 * and exclusively Pause/Resume and Cancel action controls (NO text input).
 */
@Composable
fun TestScreenCompose(
    viewModel: TestViewModel,
    onPauseToggle: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onNavigateHome: () -> Unit = {},
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val serviceState by viewModel.serviceState.collectAsState()
    val isRunning by TestService.isRunning.collectAsState()

    val isTestActive = isRunning && (serviceState.status == "RUNNING" || serviceState.isPaused)
    val isTestPaused = isRunning && serviceState.isPaused
    val isTestDone = serviceState.status == "DONE"

    val total = if (serviceState.total > 0) serviceState.total else 1
    val progress = serviceState.progress.coerceIn(0, total)
    val pct = if (serviceState.total > 0 && (isTestActive || isTestDone)) (progress * 100 / total) else 0

    // Decode live screenshot bytes when updated from TestService
    val screenshotBitmap = remember(serviceState.screenshotBytes) {
        if (!isTestActive) null
        else {
            serviceState.screenshotBytes?.let { bytes ->
                try {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // Infinite pulsing animation for LIVE indicator
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LiveAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Top Status & Progress Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.5.dp, if (isTestActive) NeonRed else SurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Top row: Left icon card, Center text status, Right circular gauge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Red container with document icon
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isTestActive) Color(0xFF1F0307) else SurfaceItem)
                                .border(1.2.dp, if (isTestActive) NeonRed.copy(alpha = 0.6f) else SurfaceBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_history),
                                contentDescription = "Inspection",
                                tint = if (isTestActive) NeonRed else TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Center: Status Title & Subtitle
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val statusLabel = when {
                                    isTestPaused -> stringResource(R.string.notification_state_paused)
                                    isTestActive -> stringResource(R.string.status_testing)
                                    isTestDone -> stringResource(R.string.status_completed)
                                    else -> stringResource(R.string.status_idle)
                                }
                                val statusColor = when {
                                    isTestPaused -> Color(0xFFFFB300)
                                    isTestActive -> NeonGreen
                                    isTestDone -> NeonBlue
                                    else -> TextMuted
                                }

                                Text(
                                    text = statusLabel,
                                    color = statusColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = stringResource(R.string.test_status_label),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    painter = painterResource(id = if (isTestActive || isTestDone) R.drawable.ic_check_circle_green else R.drawable.ic_timer),
                                    contentDescription = "Status",
                                    tint = statusColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isTestActive -> stringResource(R.string.test_status_active_stream_desc)
                                    isTestDone -> stringResource(R.string.test_status_done_desc)
                                    else -> stringResource(R.string.test_status_idle_desc)
                                },
                                color = TextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.End
                            )
                        }

                        // Right: Circular Speedometer Gauge / Wi-Fi meter
                        val gaugeColor = NeonRed
                        Box(
                            modifier = Modifier.size(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0xFF1E283C),
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                )
                                val sweep = if (isTestActive && serviceState.total > 0) (pct * 270f / 100f) else 0f
                                if (sweep > 0f) {
                                    drawArc(
                                        brush = Brush.sweepGradient(listOf(gaugeColor, Color(0xFFFF5252))),
                                        startAngle = 135f,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Icon(
                                painter = painterResource(id = R.drawable.ic_wifi_connected),
                                contentDescription = "Speedometer",
                                tint = if (isTestActive) gaugeColor else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Bottom: Progress slider line & Active card info
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Track with progress
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF141A28))
                            ) {
                                if (isTestActive || isTestDone) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((pct / 100f).coerceIn(0.05f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isTestDone) NeonBlue else NeonRed)
                                    )
                                }
                            }

                            // Active card badge on the right
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when {
                                        isTestActive && serviceState.currentCard.isNotEmpty() ->
                                            stringResource(R.string.test_active_card_format, serviceState.currentCard, serviceState.progress, serviceState.total)
                                        isTestDone ->
                                            stringResource(R.string.test_completed_cards_format, serviceState.total)
                                        else ->
                                            stringResource(R.string.test_no_active_cards)
                                    },
                                    color = if (isTestActive) NeonRed else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_info),
                                    contentDescription = "Info",
                                    tint = TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. WebView Capture View Frame Card ("بث الشاشة المباشر" - Live WebView Stream)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF04060B))
                    .border(1.5.dp, if (isTestActive) NeonRed else SurfaceBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Frame Header bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0C101A))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: LIVE / STANDBY badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isTestActive) Color(0xFF260408) else Color(0xFF141A28))
                                .border(1.dp, if (isTestActive) NeonRed.copy(alpha = 0.6f) else SurfaceBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isTestActive) NeonRed.copy(alpha = liveAlpha) else TextMuted)
                            )
                            Text(
                                text = when {
                                    isTestActive -> "LIVE"
                                    isTestDone -> "DONE"
                                    else -> "STANDBY"
                                },
                                color = if (isTestActive) NeonRed else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Right: Title & icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.test_screen_title),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_antenna_tower),
                                contentDescription = "Stream",
                                tint = if (isTestActive) NeonRed else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Frame Body: Real screenshot when active, or clean standby/idle presentation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF030509)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTestActive && screenshotBitmap != null) {
                            Image(
                                bitmap = screenshotBitmap,
                                contentDescription = "Live WebView Screen",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (isTestActive) {
                            // Active Captive Portal live simulation
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Simulated Browser Address Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B101C))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shield_check),
                                        contentDescription = "SSL Secure",
                                        tint = NeonGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "http://192.168.1.1/login",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_wifi_connected),
                                        contentDescription = "WiFi",
                                        tint = NeonRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }

                                // Captive Portal Page Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF070B14))
                                        .border(1.dp, NeonRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.test_portal_heading),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Hotspot Captive Portal (MikroTik / TP-Link)",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )

                                    // Active Card being tested in the portal
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF030509))
                                            .border(1.2.dp, NeonRed, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (serviceState.currentCard.isNotEmpty()) serviceState.currentCard else "------",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Brush.horizontalGradient(listOf(NeonRed, NeonRedDark)))
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.test_logging_in),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Bottom stream indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = NeonRed,
                                        strokeWidth = 1.8.dp
                                    )
                                    Text(
                                        text = stringResource(R.string.test_submitting_req),
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            // Professional Idle/Standby State (No spinning indicator, no fake active stream)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(SurfaceItem)
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(20.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_router_3d),
                                        contentDescription = "Standby",
                                        tint = if (isTestDone) NeonBlue else TextMuted,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isTestDone) stringResource(R.string.test_completed_title) else stringResource(R.string.test_standby_title),
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isTestDone)
                                        stringResource(R.string.test_completed_desc)
                                    else
                                        stringResource(R.string.test_standby_desc),
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Action Controls
            if (isTestActive) {
                // When Test is Running or Paused: Show Cancel and Pause/Resume controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel & Stop button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF140306))
                            .border(1.5.dp, NeonRed.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                            .clickable { onCancel() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.test_btn_cancel_disconnect),
                                color = NeonRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_cross_circle_red),
                                contentDescription = "Cancel",
                                tint = NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Pause / Resume button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(colors = listOf(NeonRed, NeonRedDark))
                            )
                            .clickable { onPauseToggle(serviceState.isPaused) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (serviceState.isPaused) stringResource(R.string.test_btn_resume) else stringResource(R.string.test_btn_pause),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                            Icon(
                                painter = painterResource(id = if (serviceState.isPaused) R.drawable.ic_play_modern else R.drawable.ic_stop_modern),
                                contentDescription = "Pause",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            } else {
                // When Idle or Done: Show clean navigation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Back to Home button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(SurfaceDark)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(22.dp))
                            .clickable { onNavigateHome() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_home),
                                contentDescription = "Home",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.test_btn_return_home),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Start Test from Home
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.horizontalGradient(colors = listOf(NeonRed, NeonRedDark))
                            )
                            .clickable { onNavigateHome() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play_modern),
                                contentDescription = "Start",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isTestDone) stringResource(R.string.test_btn_new_test) else stringResource(R.string.test_btn_start_test),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
