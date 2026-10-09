package com.example.presentation.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.data.local.entity.RouterProfileEntity

/**
 * Modern Jetpack Compose Router Add/Edit Form Screen
 */
@Composable
fun RouterFormScreenCompose(
    existingProfile: RouterProfileEntity?,
    onSave: (RouterProfileEntity) -> Unit,
    onCancel: () -> Unit,
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isEdit = existingProfile != null

    var name by remember(existingProfile) { mutableStateOf(existingProfile?.name ?: "") }
    var ip by remember(existingProfile) { mutableStateOf(existingProfile?.ip ?: "192.168.1.1") }
    var protocol by remember(existingProfile) { mutableStateOf(existingProfile?.protocol ?: "http") }
    var loginPath by remember(existingProfile) { mutableStateOf(existingProfile?.loginPath ?: "/login") }
    var strategyId by remember(existingProfile) { mutableStateOf(existingProfile?.strategyId ?: "generic") }
    var usernameSelector by remember(existingProfile) { mutableStateOf(existingProfile?.usernameSelector ?: "input[name=username]") }
    var passwordSelector by remember(existingProfile) { mutableStateOf(existingProfile?.passwordSelector ?: "input[name=password]") }
    var submitSelector by remember(existingProfile) { mutableStateOf(existingProfile?.submitSelector ?: "input[type=submit]") }
    var username by remember(existingProfile) { mutableStateOf(existingProfile?.username ?: "") }
    var successIndicator by remember(existingProfile) { mutableStateOf(existingProfile?.successIndicator ?: "logout") }
    var failureIndicator by remember(existingProfile) { mutableStateOf(existingProfile?.failureIndicator ?: "error") }
    var logoutSelector by remember(existingProfile) { mutableStateOf(existingProfile?.logoutSelector ?: "a[href*=logout]") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val isAr = com.example.util.LocaleHelper.getPersistedLocale(context) == "ar"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        if (showTopHeader) {
            PagesHeader(
                title = if (isEdit) stringResource(R.string.router_form_title_edit) else stringResource(R.string.router_form_title_add),
                subtitle = stringResource(R.string.header_router_form_sub),
                isRoot = false,
                onNavClick = onCancel
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Error banner if any
            errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF26050A))
                        .border(1.dp, NeonRed, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = err,
                        color = NeonRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 1. Basic Connection Info Group
            FormSectionCard(title = stringResource(R.string.router_form_conn_section), iconRes = R.drawable.ic_router_3d) {
                FormFieldInput(
                    label = stringResource(R.string.router_form_name_label),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.router_form_name_placeholder)
                )

                FormFieldInput(
                    label = stringResource(R.string.router_form_ip_label),
                    value = ip,
                    onValueChange = { ip = it },
                    placeholder = "192.168.1.1"
                )

                // Protocol Selector (HTTP / HTTPS)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.router_form_proto_label),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (protocol == "http") NeonRed else SurfaceItem)
                                .border(1.dp, if (protocol == "http") NeonRed else SurfaceBorder, RoundedCornerShape(10.dp))
                                .clickable { protocol = "http" }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HTTP",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (protocol == "https") NeonRed else SurfaceItem)
                                .border(1.dp, if (protocol == "https") NeonRed else SurfaceBorder, RoundedCornerShape(10.dp))
                                .clickable { protocol = "https" }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "HTTPS",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                FormFieldInput(
                    label = stringResource(R.string.router_form_path_label),
                    value = loginPath,
                    onValueChange = { loginPath = it },
                    placeholder = "/login"
                )

                // Strategy Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.strategy_selector_title),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val strategies = listOf(
                        "generic" to if (isAr) "عام" else "Generic",
                        "motasem" to if (isAr) "معتصم" else "Motasem",
                        "bello" to if (isAr) "بيلو" else "Bello",
                        "abasha" to if (isAr) "الباشا" else "AlBasha"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        strategies.forEach { (id, label) ->
                            val isSelected = strategyId.equals(id, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NeonRed else SurfaceItem)
                                    .border(1.dp, if (isSelected) NeonRed else SurfaceBorder, RoundedCornerShape(10.dp))
                                    .clickable { strategyId = id }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. CSS Selectors Group
            FormSectionCard(title = stringResource(R.string.router_form_selectors_section), iconRes = R.drawable.ic_nav_settings) {
                FormFieldInput(
                    label = stringResource(R.string.router_form_user_sel_label),
                    value = usernameSelector,
                    onValueChange = { usernameSelector = it },
                    placeholder = "input[name=username]"
                )

                FormFieldInput(
                    label = stringResource(R.string.router_form_pass_sel_label),
                    value = passwordSelector,
                    onValueChange = { passwordSelector = it },
                    placeholder = "input[name=password]"
                )

                FormFieldInput(
                    label = stringResource(R.string.router_form_login_btn_label),
                    value = submitSelector,
                    onValueChange = { submitSelector = it },
                    placeholder = "input[type=submit], button[type=submit]"
                )

                FormFieldInput(
                    label = stringResource(R.string.router_form_logout_btn_label),
                    value = logoutSelector,
                    onValueChange = { logoutSelector = it },
                    placeholder = "a[href*=logout]"
                )
            }

            // 3. Status Indicators Group
            FormSectionCard(title = stringResource(R.string.router_form_indicators_section), iconRes = R.drawable.ic_check_circle_green) {
                FormFieldInput(
                    label = stringResource(R.string.router_form_succ_ind_label),
                    value = successIndicator,
                    onValueChange = { successIndicator = it },
                    placeholder = "logout, welcome"
                )

                FormFieldInput(
                    label = stringResource(R.string.router_form_fail_ind_label),
                    value = failureIndicator,
                    onValueChange = { failureIndicator = it },
                    placeholder = "invalid, error"
                )
            }

            // 4. Fixed Credentials (Optional)
            FormSectionCard(title = stringResource(R.string.router_form_static_section), iconRes = R.drawable.ic_info) {
                FormFieldInput(
                    label = stringResource(R.string.router_form_static_user_label),
                    value = username,
                    onValueChange = { username = it },
                    placeholder = stringResource(R.string.router_form_static_user_placeholder)
                )
            }

            // Bottom Actions: Cancel and Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cancel Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF140306))
                        .border(1.5.dp, NeonRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { onCancel() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        color = NeonRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save Button (Glowing Red Gradient)
                val validationErrStr = stringResource(R.string.router_form_validation_err)
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonRed, NeonRedDark)
                            )
                        )
                        .clickable {
                            if (name.isBlank() || ip.isBlank() || usernameSelector.isBlank() || passwordSelector.isBlank() || submitSelector.isBlank()) {
                                errorMessage = validationErrStr
                                return@clickable
                            }

                            val result = RouterProfileEntity(
                                id = existingProfile?.id ?: 0L,
                                name = name.trim(),
                                ip = ip.trim(),
                                protocol = protocol.trim(),
                                loginPath = loginPath.trim(),
                                usernameSelector = usernameSelector.trim(),
                                passwordSelector = passwordSelector.trim(),
                                submitSelector = submitSelector.trim(),
                                username = username.trim(),
                                password = existingProfile?.password ?: "",
                                passwordEnabled = existingProfile?.passwordEnabled ?: false,
                                strategyId = strategyId.trim(),
                                successIndicator = successIndicator.trim(),
                                failureIndicator = failureIndicator.trim(),
                                logoutSelector = logoutSelector.trim(),
                                isDefault = existingProfile?.isDefault ?: false,
                                createdAt = existingProfile?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(result)
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEdit) stringResource(R.string.router_form_btn_save) else stringResource(R.string.router_form_btn_add_action),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    iconRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = title,
                color = NeonRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = NeonRed,
                modifier = Modifier.size(18.dp)
            )
        }
        content()
    }
}

@Composable
private fun FormFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF070A10))
                .border(1.dp, Color(0xFF161E2E), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}
