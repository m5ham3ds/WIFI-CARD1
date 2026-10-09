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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.RouterProfileEntity
import com.example.presentation.settings.RouterManagerViewModel
import com.example.util.DateUtils

/**
 * Modern Jetpack Compose Router Management Screen
 * Complete Kotlin + Jetpack Compose + Material 3 implementation
 */
@Composable
fun RouterManagerScreenCompose(
    viewModel: RouterManagerViewModel,
    onAddRouter: () -> Unit,
    onEditRouter: (Long) -> Unit,
    onBack: () -> Unit,
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val routers by viewModel.routers.collectAsState()
    var routerToDelete by remember { mutableStateOf<RouterProfileEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (showTopHeader) {
            PagesHeader(
                title = stringResource(R.string.menu_router_manager),
                subtitle = stringResource(R.string.header_router_manager_sub),
                isRoot = false,
                onNavClick = onBack
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (routers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.empty_search),
                            contentDescription = "Empty",
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.router_empty_title),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.router_empty_sub),
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(routers, key = { it.id }) { router ->
                        RouterCardItem(
                            router = router,
                            onSetDefault = { viewModel.setDefaultRouter(router.id) },
                            onEdit = { onEditRouter(router.id) },
                            onDelete = { routerToDelete = router }
                        )
                    }
                }
            }

            // Floating Action Button at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonRed, NeonRedDark)
                            )
                        )
                        .clickable { onAddRouter() }
                        .padding(horizontal = 24.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.router_btn_add),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_router_3d),
                            contentDescription = "Add",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    routerToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { routerToDelete = null },
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
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = NeonRed,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_router_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.router_delete_confirm_msg, target.name),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(NeonRed, NeonRedDark)))
                        .clickable {
                            viewModel.deleteRouter(target)
                            routerToDelete = null
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.router_delete_confirm_pos),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161E2E))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(14.dp))
                        .clickable { routerToDelete = null }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            containerColor = SurfaceDark,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.5.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        )
    }
}

@Composable
fun RouterCardItem(
    router: RouterProfileEntity,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDefault = router.isDefault

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(
                if (isDefault) 1.5.dp else 1.dp,
                if (isDefault) NeonRed else SurfaceBorder,
                RoundedCornerShape(18.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Row: Status badge / Set Default on Left, Name & Icon on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Default indicator or button
            if (isDefault) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.15f))
                        .border(1.dp, NeonGreen.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.router_default_badge),
                            color = NeonGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle_green),
                            contentDescription = "Default",
                            tint = NeonGreen,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF141A28))
                        .clickable { onSetDefault() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.router_btn_set_default),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right: Router Name and Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = router.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = DateUtils.formatDate(router.createdAt),
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDefault) Color(0xFF1C0306) else Color(0xFF111726))
                        .border(1.dp, if (isDefault) NeonRed.copy(alpha = 0.5f) else SurfaceBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_router_3d),
                        contentDescription = "Router",
                        tint = if (isDefault) NeonRed else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Middle Row: Details Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070A10))
                .border(1.dp, Color(0xFF161E2E), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${router.protocol.uppercase()} : ${router.ip}${router.loginPath}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = router.strategyId.uppercase(),
                    color = NeonRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Action Buttons Row: Delete and Edit
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Delete button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF160306))
                    .border(1.dp, NeonRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onDelete() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = NeonRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.router_btn_delete),
                        color = NeonRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Edit button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0C1424))
                    .border(1.dp, NeonBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onEdit() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_settings),
                        contentDescription = "Edit",
                        tint = NeonBlue,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.router_btn_edit),
                        color = NeonBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
