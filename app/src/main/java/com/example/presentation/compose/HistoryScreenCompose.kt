package com.example.presentation.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.presentation.history.HistoryViewModel
import com.example.util.DateUtils

/**
 * Authentic History Screen matching 'صفحه السجلات.png'
 */
@Composable
fun HistoryScreenCompose(
    viewModel: HistoryViewModel,
    onBack: () -> Unit = {},
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionId.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // If a session is selected, render the SessionDetailCompose screen
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }
    if (selectedSession != null) {
        SessionDetailCompose(
            session = selectedSession,
            viewModel = viewModel,
            onBack = { viewModel.clearSessionSelection() },
            showTopHeader = showTopHeader
        )
        return
    }

    // Filter sessions by search query
    val displayedSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter {
            it.routerName.contains(searchQuery, ignoreCase = true) ||
            "Session #${it.id}".contains(searchQuery, ignoreCase = true)
        }
    }

    // Summary calculations
    val totalSuccess = sessions.sumOf { it.successCount }
    val totalFailure = sessions.sumOf { it.failureCount }
    val lastDate = if (sessions.isNotEmpty()) stringResource(R.string.history_today) else "-"

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
            // 1. Search Bar & Dropdown Filters Row (matching 'صفحه السجلات.png')
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calendar Filter Dropdown
                FilterDropdownButton(
                    label = stringResource(R.string.history_filter_all),
                    iconRes = R.drawable.ic_nav_history,
                    onClick = {}
                )

                // Router Filter Dropdown
                FilterDropdownButton(
                    label = stringResource(R.string.history_filter_all),
                    iconRes = R.drawable.ic_router,
                    onClick = {}
                )

                // Search Bar Input on the right
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.history_search_hint),
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                innerTextField()
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_history),
                            contentDescription = "Search",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 2. 4 Stat Cards in a row (matching 'صفحه السجلات.png')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Failed (Left)
                HistoryStatCard(
                    title = stringResource(R.string.history_stat_failed),
                    value = totalFailure.toString(),
                    iconRes = R.drawable.ic_cross_circle_red,
                    color = NeonRed,
                    modifier = Modifier.weight(1f)
                )

                // Success
                HistoryStatCard(
                    title = stringResource(R.string.history_stat_success),
                    value = totalSuccess.toString(),
                    iconRes = R.drawable.ic_check_circle_green,
                    color = NeonGreen,
                    modifier = Modifier.weight(1f)
                )

                // Total Sessions
                HistoryStatCard(
                    title = stringResource(R.string.history_stat_total),
                    value = sessions.size.toString(),
                    iconRes = R.drawable.ic_timer,
                    color = Color.White,
                    modifier = Modifier.weight(1.1f)
                )

                // Last Operation (Right)
                HistoryStatCard(
                    title = stringResource(R.string.history_stat_last),
                    value = lastDate,
                    iconRes = R.drawable.ic_timer,
                    color = Color.White,
                    modifier = Modifier.weight(1.1f)
                )
            }

            // 3. Sessions List
            if (displayedSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.empty_search),
                            contentDescription = "Empty History",
                            modifier = Modifier.size(90.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.history_no_sessions),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.history_sessions_appear_here),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedSessions) { session ->
                        val isRunning = session.isRunning
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceDark)
                                .border(
                                    if (isRunning) 1.5.dp else 1.dp,
                                    if (isRunning) NeonRed else SurfaceBorder,
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable { viewModel.selectSession(session.id) }
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Session Top Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Three dots menu & Status Pill on Left
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "⋮",
                                            color = TextMuted,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isRunning) Color(0xFF0F2B1E) else Color(0xFF0F2B1E))
                                                .border(1.dp, NeonGreen.copy(alpha = 0.5f), CircleShape)
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = if (isRunning) stringResource(R.string.history_status_running) else stringResource(R.string.history_status_completed),
                                                    color = NeonGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    painter = painterResource(id = if (isRunning) R.drawable.ic_timer else R.drawable.ic_check_circle_green),
                                                    contentDescription = "Status",
                                                    tint = NeonGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Session Title, Indicator Dot, Date, and Router on Right
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Session #${session.id}",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isRunning) NeonRed else Color(0xFF94A3B8))
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "📅 ${DateUtils.formatDateTime(session.startedAt)}",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "🌐 ${session.routerName}",
                                                color = NeonRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Session Counters Row (Failed, Success, Total, Chevron)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Failed
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF070A12))
                                            .border(1.dp, NeonRed.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_cross_circle_red),
                                                    contentDescription = "Failed",
                                                    tint = NeonRed,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = session.failureCount.toString(),
                                                    color = NeonRed,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(text = stringResource(R.string.history_stat_failed), color = NeonRed.copy(alpha = 0.8f), fontSize = 10.sp)
                                        }
                                    }

                                    // Success
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF070A12))
                                            .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_check_circle_green),
                                                    contentDescription = "Success",
                                                    tint = NeonGreen,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = session.successCount.toString(),
                                                    color = NeonGreen,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(text = stringResource(R.string.history_stat_success), color = NeonGreen.copy(alpha = 0.8f), fontSize = 10.sp)
                                        }
                                    }

                                    // Total
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF070A12))
                                            .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_antenna_tower),
                                                    contentDescription = "Total",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = session.totalCards.toString(),
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(text = stringResource(R.string.history_stat_total), color = TextMuted, fontSize = 10.sp)
                                        }
                                    }

                                    // Chevron Circular Button
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF141926)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_back),
                                            contentDescription = "Details",
                                            tint = NeonRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDropdownButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = NeonRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "▾",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun HistoryStatCard(
    title: String,
    value: String,
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = title,
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}
