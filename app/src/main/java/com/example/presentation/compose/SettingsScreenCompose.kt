package com.example.presentation.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.example.R
import com.example.data.local.preferences.ThemePreferences
import com.example.presentation.settings.SettingsViewModel

/**
 * Authentic Settings Screen matching 'صفحه الاعدادات.png'
 */
@Composable
fun SettingsScreenCompose(
    viewModel: SettingsViewModel,
    onNavigateToRouters: () -> Unit,
    onColorPickerClick: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onResetDelaysClick: () -> Unit,
    onThemeClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onPageLoadDelayClick: () -> Unit = {},
    onCardTestDelayClick: () -> Unit = {},
    onScreenshotDelayClick: () -> Unit = {},
    onThreadCountClick: () -> Unit = {},
    onExportDbClick: () -> Unit = {},
    onBack: () -> Unit = {},
    showTopHeader: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val vibrateFlow by viewModel.vibrateOnSuccess.collectAsState(initial = prefs.getBoolean("vibrate_on_success", true))
    val soundFlow by viewModel.soundOnSuccess.collectAsState(initial = prefs.getBoolean("sound_on_success", false))
    val preloadFlow by viewModel.enablePreload.collectAsState(initial = prefs.getBoolean("enable_preload_pages", true))
    val pageLoadDelayFlow by viewModel.pageLoadDelay.collectAsState(initial = 2000L)
    val cardTestDelayFlow by viewModel.cardTestDelay.collectAsState(initial = 3000L)
    val screenshotDelayFlow by viewModel.screenshotDelay.collectAsState(initial = 2000L)

    var vibrateEnabled by remember { mutableStateOf(prefs.getBoolean("vibrate_on_success", prefs.getBoolean("vibrate", true))) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_on_success", prefs.getBoolean("sound", false))) }
    var preloadEnabled by remember { mutableStateOf(prefs.getBoolean("enable_preload_pages", prefs.getBoolean("preload_background", true))) }

    LaunchedEffect(vibrateFlow) { vibrateEnabled = vibrateFlow }
    LaunchedEffect(soundFlow) { soundEnabled = soundFlow }
    LaunchedEffect(preloadFlow) { preloadEnabled = preloadFlow }

    val themeVal = prefs.getString("theme", "dark") ?: "dark"
    val themeKeys = context.resources.getStringArray(R.array.theme_values)
    val themeEntries = context.resources.getStringArray(R.array.theme_entries)
    val themeIndex = themeKeys.indexOf(themeVal).let { if (it >= 0) it else 1 }
    val themeLabel = themeEntries.getOrElse(themeIndex) { "Dark Mode" }

    val langVal = com.example.util.LocaleHelper.getPersistedLocale(context)
    val langLabel = if (langVal == "en") stringResource(R.string.lang_en) else stringResource(R.string.lang_ar)

    val primaryKey = ThemePreferences.getPrimaryColorSync(context)
    val primaryColorObj = com.example.domain.model.AppPrimaryColor.fromKey(primaryKey)
    val colorLabel = primaryColorObj.getDisplayName(langVal != "en")

    val pageLoadDelayVal = (pageLoadDelayFlow / 1000).coerceAtLeast(1)
    val cardTestDelayVal = (cardTestDelayFlow / 1000).coerceAtLeast(1)
    val screenshotDelayVal = (screenshotDelayFlow / 1000).coerceAtLeast(1)
    val threadCountVal = prefs.getString("thread_count", "1") ?: "1"

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Graphic Header with Page Title
        if (showTopHeader) {
            PagesHeader(
                title = stringResource(R.string.menu_settings),
                subtitle = stringResource(R.string.header_settings_sub),
                isRoot = false,
                onNavClick = onBack
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. المظهر والتصميم
            SettingsGroupCard(title = stringResource(R.string.settings_group_appearance), iconRes = R.drawable.ic_palette) {
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_theme),
                    subtitle = themeLabel,
                    iconRes = R.drawable.ic_nav_settings,
                    onClick = onThemeClick
                )
                HorizontalDivider(color = Color(0xFF141A28))
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_language),
                    subtitle = langLabel,
                    iconRes = R.drawable.ic_info,
                    onClick = onLanguageClick
                )
            }

            // 2. لون التطبيق
            SettingsGroupCard(title = stringResource(R.string.pref_primary_color), iconRes = R.drawable.ic_palette) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onColorPickerClick() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Arrow",
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.pref_primary_color),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = colorLabel,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.End
                            )
                        }

                        // Selected Color Circle Icon
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF161E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(NeonRed)
                            )
                        }
                    }
                }
            }

            // 3. الإشعارات والتنبيهات
            SettingsGroupCard(title = stringResource(R.string.pref_category_notifications), iconRes = R.drawable.ic_bell) {
                SettingsRowToggleItem(
                    title = stringResource(R.string.pref_vibrate),
                    iconRes = R.drawable.ic_bell,
                    checked = vibrateEnabled,
                    onCheckedChange = {
                        vibrateEnabled = it
                        prefs.edit().putBoolean("vibrate_on_success", it).putBoolean("vibrate", it).apply()
                        viewModel.setVibrateOnSuccess(it)
                    }
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowToggleItem(
                    title = stringResource(R.string.pref_sound),
                    iconRes = R.drawable.ic_bell,
                    checked = soundEnabled,
                    onCheckedChange = {
                        soundEnabled = it
                        prefs.edit().putBoolean("sound_on_success", it).putBoolean("sound", it).apply()
                        viewModel.setSoundOnSuccess(it)
                    }
                )
            }

            // 4. إعدادات سرعة الفحص والانتظار
            SettingsGroupCard(title = stringResource(R.string.settings_group_delays), iconRes = R.drawable.ic_timer) {
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_page_load_title),
                    subtitle = "$pageLoadDelayVal s",
                    iconRes = R.drawable.ic_timer,
                    onClick = onPageLoadDelayClick
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_card_test_title),
                    subtitle = "$cardTestDelayVal s",
                    iconRes = R.drawable.ic_timer,
                    onClick = onCardTestDelayClick
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_screenshot_title),
                    subtitle = "$screenshotDelayVal s",
                    iconRes = R.drawable.ic_antenna_tower,
                    onClick = onScreenshotDelayClick
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_reset_delays_title),
                    subtitle = stringResource(R.string.settings_reset_delays_sub),
                    iconRes = R.drawable.ic_nav_history,
                    onClick = onResetDelaysClick
                )
            }

            // 5. إعدادات الاختبار والخلفية
            SettingsGroupCard(title = stringResource(R.string.settings_group_background), iconRes = R.drawable.ic_nav_settings) {
                SettingsRowToggleItem(
                    title = stringResource(R.string.settings_preload_title),
                    subtitle = stringResource(R.string.settings_preload_sub),
                    iconRes = R.drawable.ic_antenna_tower,
                    checked = preloadEnabled,
                    onCheckedChange = {
                        preloadEnabled = it
                        prefs.edit().putBoolean("enable_preload_pages", it).putBoolean("preload_background", it).apply()
                        viewModel.setEnablePreload(it)
                    }
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.pref_thread_count_title),
                    subtitle = threadCountVal,
                    iconRes = R.drawable.ic_nav_history,
                    onClick = onThreadCountClick
                )
            }

            // 6. إدارة البيانات
            SettingsGroupCard(title = stringResource(R.string.settings_group_data), iconRes = R.drawable.ic_nav_router) {
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_router_config_title),
                    subtitle = stringResource(R.string.settings_router_config_sub),
                    iconRes = R.drawable.ic_nav_router,
                    onClick = onNavigateToRouters
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_clear_history_title),
                    subtitle = stringResource(R.string.settings_clear_history_sub),
                    iconRes = R.drawable.ic_delete,
                    onClick = onClearHistoryClick
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_export_backup_title),
                    subtitle = stringResource(R.string.settings_export_backup_sub),
                    iconRes = R.drawable.ic_export,
                    onClick = onExportDbClick
                )
            }

            // 7. حول
            SettingsGroupCard(title = stringResource(R.string.settings_group_about), iconRes = R.drawable.ic_info) {
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_version_title),
                    subtitle = "1.0.0-Stable",
                    iconRes = R.drawable.ic_info,
                    onClick = {
                        com.example.util.ToastHelper.showSuccessToast(context, "WiFi Master Pro v1.0.0-Stable")
                    }
                )
                HorizontalDivider(color = SurfaceBorder)
                SettingsRowClickItem(
                    title = stringResource(R.string.settings_github_title),
                    subtitle = "msalah564s/wifi-master-pro",
                    iconRes = R.drawable.ic_github,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/msalah564s/wifi-master-pro"))
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsGroupCard(
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
fun SettingsRowClickItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back),
            contentDescription = "Arrow",
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.End
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceItem)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = NeonRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsRowToggleItem(
    title: String,
    subtitle: String? = null,
    iconRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeonRed,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceItem
            )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceItem)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = NeonRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
