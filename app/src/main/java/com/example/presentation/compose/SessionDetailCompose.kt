package com.example.presentation.compose

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.TestResultEntity
import com.example.data.local.entity.TestSessionEntity
import com.example.presentation.history.HistoryViewModel
import com.example.util.DateUtils

/**
 * Authentic Session Details Screen matching 'صفحه داخل احد السجلات.jpg'
 */
@Composable
fun SessionDetailCompose(
    session: TestSessionEntity,
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val results by viewModel.filteredResults.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val displayedResults = remember(results, searchQuery) {
        if (searchQuery.isBlank()) results
        else results.filter { it.cardCode.contains(searchQuery, ignoreCase = true) || it.message.contains(searchQuery, ignoreCase = true) }
    }

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
            // 1. Top Session Summary Card with Red Neon Glowing Border
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.5.dp, NeonRed, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Right: Title, dot, and details (RTL layout)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C0306))
                                .border(1.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_router),
                                contentDescription = "Router",
                                tint = NeonRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Session #${session.id}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (session.isRunning) NeonRed else Color(0xFF64748B))
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    // Left: Status Badge and Menu Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (session.isRunning) NeonGreen.copy(alpha = 0.15f) else NeonGreen.copy(alpha = 0.12f))
                                .border(1.dp, NeonGreen.copy(alpha = 0.4f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (session.isRunning) stringResource(R.string.history_status_running) else stringResource(R.string.history_status_completed),
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    painter = painterResource(id = if (session.isRunning) R.drawable.ic_timer else R.drawable.ic_check_circle_green),
                                    contentDescription = "Status",
                                    tint = NeonGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Text(
                            text = "⋮",
                            color = TextMuted,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Stat counters triad inside card
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                }
            }

            // 2. Action Bar: Export Button (Left) & Results Header (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Export Button in solid vibrant Red pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeonRed)
                        .clickable {
                            val fileName = "session_${session.id}_results.json"
                            viewModel.exportToFile(context, fileName)
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_export),
                            contentDescription = "Export",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.session_export_results),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Filter Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.session_view_results),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_settings),
                        contentDescription = "Filter",
                        tint = NeonRed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 3. Filter Chips Row matching screenshot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailFilterChip(
                    text = stringResource(R.string.history_filter_all),
                    iconRes = R.drawable.ic_check_circle_green,
                    isSelected = currentFilter == "all",
                    onClick = { viewModel.setFilter("all") },
                    modifier = Modifier.weight(1f)
                )
                DetailFilterChip(
                    text = stringResource(R.string.session_filter_success),
                    iconRes = R.drawable.ic_check_circle_green,
                    isSelected = currentFilter == "success",
                    onClick = { viewModel.setFilter("success") },
                    modifier = Modifier.weight(1.3f)
                )
                DetailFilterChip(
                    text = stringResource(R.string.session_filter_failure),
                    iconRes = R.drawable.ic_cross_circle_red,
                    isSelected = currentFilter == "failure",
                    onClick = { viewModel.setFilter("failure") },
                    modifier = Modifier.weight(1.3f)
                )
                DetailFilterChip(
                    text = stringResource(R.string.filter_two_devices),
                    iconRes = R.drawable.ic_antenna_tower,
                    isSelected = currentFilter == "two_devices",
                    onClick = { viewModel.setFilter("two_devices") },
                    modifier = Modifier.weight(1.5f)
                )
            }

            // 4. Search and Toolbar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Action shortcut icon buttons on the left
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_history),
                        contentDescription = "Calendar",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_timer),
                        contentDescription = "Sort",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Search Bar Input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
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
                                        text = stringResource(R.string.session_search_hint),
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 5. Results Content Area (Empty State or List)
            if (displayedResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceDark)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.empty_search),
                            contentDescription = "Empty",
                            modifier = Modifier.size(90.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.session_no_results),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.session_results_appear_here),
                            color = TextMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedResults) { item ->
                        val isSuccess = item.state == "Success"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceDark)
                                .border(
                                    1.dp,
                                    if (isSuccess) NeonGreen.copy(alpha = 0.35f) else NeonRed.copy(alpha = 0.35f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSuccess) NeonGreen.copy(alpha = 0.12f) else NeonRed.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = if (isSuccess) R.drawable.ic_check_circle_green else R.drawable.ic_cross_circle_red),
                                            contentDescription = item.state,
                                            tint = if (isSuccess) NeonGreen else NeonRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = item.cardCode,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = item.message,
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "${item.durationMs}ms",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 6. Bottom Info Tip Card matching 'صفحه داخل احد السجلات.jpg'
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, NeonRed.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.session_info_title),
                            color = NeonRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.session_bottom_info_tip),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.End
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = "Info",
                        tint = NeonRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailFilterChip(
    text: String,
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF220307) else Color(0xFF090E17))
            .border(
                1.dp,
                if (isSelected) NeonRed else SurfaceBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
