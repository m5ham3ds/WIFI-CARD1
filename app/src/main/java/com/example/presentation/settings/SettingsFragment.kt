package com.example.presentation.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.example.R
import com.example.data.local.preferences.ThemePreferences
import com.example.domain.model.AppPrimaryColor
import com.example.presentation.compose.SettingsScreenCompose
import com.example.presentation.compose.WifiCardTheme
import com.example.util.LocaleHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Modern Jetpack Compose Settings Screen
 * Fully matches 'صفحه الاعدادات.png'
 */
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                WifiCardTheme {
                    SettingsScreenCompose(
                        viewModel = viewModel,
                        onNavigateToRouters = {
                            findNavController().navigate(R.id.action_settings_to_routerManager)
                        },
                        onColorPickerClick = {
                            showColorPickerDialog()
                        },
                        onClearHistoryClick = {
                            if (com.example.service.TestService.isRunning.value) {
                                com.example.util.ToastHelper.showErrorToast(
                                    requireContext(),
                                    getString(R.string.cannot_clear_history_active)
                                )
                                return@SettingsScreenCompose
                            }
                            com.example.util.DialogHelper.showCustomDialog(
                                context = requireContext(),
                                title = getString(R.string.pref_clear_history),
                                message = getString(R.string.msg_confirm_clear),
                                dialogType = com.example.util.DialogHelper.DialogType.WARNING,
                                positiveButtonText = getString(R.string.btn_confirm_delete),
                                positiveAction = {
                                    viewModel.confirmClearHistory()
                                },
                                negativeButtonText = getString(R.string.btn_cancel)
                            )
                        },
                        onResetDelaysClick = {
                            com.example.util.DialogHelper.showCustomDialog(
                                context = requireContext(),
                                title = getString(R.string.dialog_reset_delays_title),
                                message = getString(R.string.dialog_reset_delays_msg),
                                dialogType = com.example.util.DialogHelper.DialogType.WARNING,
                                iconRes = R.drawable.ic_timer,
                                positiveButtonText = getString(R.string.btn_confirm_reset),
                                positiveAction = {
                                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                                    prefs.edit()
                                        .putString("page_load_delay", "2000")
                                        .putString("card_test_delay", "3000")
                                        .putString("screenshot_delay", "2000")
                                        .apply()
                                    viewModel.resetDelaysToDefault()
                                    com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.msg_reset_success))
                                },
                                negativeButtonText = getString(R.string.btn_cancel)
                            )
                        },
                        onThemeClick = {
                            showThemeSelectionDialog()
                        },
                        onLanguageClick = {
                            showLanguageSelectionDialog()
                        },
                        onPageLoadDelayClick = {
                            showPageLoadDelayDialog()
                        },
                        onCardTestDelayClick = {
                            showCardTestDelayDialog()
                        },
                        onScreenshotDelayClick = {
                            showScreenshotDelayDialog()
                        },
                        onThreadCountClick = {
                            showThreadCountDialog()
                        },
                        onExportDbClick = {
                            viewModel.exportAllResults(requireContext())
                        },
                        onBack = {
                            activity?.onBackPressedDispatcher?.onBackPressed()
                        },
                        showTopHeader = false
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is SettingsViewModel.UiEvent.ShowMessage -> {
                            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(event.resId))
                        }
                        is SettingsViewModel.UiEvent.ShowText -> {
                            com.example.util.ToastHelper.showSuccessToast(requireContext(), event.text)
                        }
                        is SettingsViewModel.UiEvent.ShareExportFile -> {
                            com.example.util.ToastHelper.showSuccessToast(requireContext(), event.message)
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    requireContext(),
                                    "${requireContext().packageName}.fileprovider",
                                    event.file
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, event.file.name)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.export_share_title)))
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to launch sharesheet for export file")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showThemeSelectionDialog() {
        val themes = resources.getStringArray(R.array.theme_entries)
        val keys = resources.getStringArray(R.array.theme_values)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val curTheme = prefs.getString("theme", "dark") ?: "dark"
        val checkedItem = keys.indexOf(curTheme).coerceAtLeast(0)

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_theme),
            options = themes,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_palette
        ) { which ->
            val selected = keys[which]
            prefs.edit().putString("theme", selected).apply()
            viewModel.setThemeMode(selected)
            val nightMode = when (selected) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
            activity?.recreate()
        }
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(getString(R.string.lang_ar), getString(R.string.lang_en))
        val codes = arrayOf("ar", "en")
        val curLang = LocaleHelper.getPersistedLocale(requireContext())
        val checkedItem = if (curLang == "en") 1 else 0

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_language),
            options = languages,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_info
        ) { which ->
            val selected = codes[which]
            LocaleHelper.persistLocale(requireContext(), selected)
            viewModel.setAppLanguage(selected)
            LocaleHelper.setLocale(requireContext(), selected)
            try {
                val appLocale = when (selected.lowercase()) {
                    "en" -> androidx.core.os.LocaleListCompat.forLanguageTags("en")
                    else -> androidx.core.os.LocaleListCompat.forLanguageTags("ar")
                }
                AppCompatDelegate.setApplicationLocales(appLocale)
            } catch (_: Exception) {}
            activity?.recreate()
        }
    }

    private fun showPageLoadDelayDialog() {
        val options = resources.getStringArray(R.array.delay_entries)
        val values = resources.getStringArray(R.array.delay_values)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val curVal = prefs.getString("page_load_delay", "2000") ?: "2000"
        val checkedItem = values.indexOf(curVal).let { if (it >= 0) it else 2 }

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_page_load_title),
            options = options,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_timer
        ) { which ->
            val selected = values[which]
            prefs.edit().putString("page_load_delay", selected).apply()
            viewModel.setPageLoadDelay(selected.toLong())
            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.delay_saved_toast, options[which]))
        }
    }

    private fun showCardTestDelayDialog() {
        val options = resources.getStringArray(R.array.delay_entries)
        val values = resources.getStringArray(R.array.delay_values)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val curVal = prefs.getString("card_test_delay", "3000") ?: "3000"
        val checkedItem = values.indexOf(curVal).let { if (it >= 0) it else 4 }

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_card_test_title),
            options = options,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_timer
        ) { which ->
            val selected = values[which]
            prefs.edit().putString("card_test_delay", selected).apply()
            viewModel.setCardTestDelay(selected.toLong())
            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.delay_saved_toast, options[which]))
        }
    }

    private fun showScreenshotDelayDialog() {
        val options = resources.getStringArray(R.array.screenshot_delay_entries)
        val values = resources.getStringArray(R.array.screenshot_delay_values)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val curVal = prefs.getString("screenshot_delay", "2000") ?: "2000"
        val checkedItem = values.indexOf(curVal).let { if (it >= 0) it else 2 }

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_screenshot_title),
            options = options,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_timer
        ) { which ->
            val selected = values[which]
            prefs.edit().putString("screenshot_delay", selected).apply()
            viewModel.setScreenshotDelay(selected.toLong())
            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.delay_saved_toast, options[which]))
        }
    }

    private fun showThreadCountDialog() {
        val isAr = LocaleHelper.getPersistedLocale(requireContext()) == "ar"
        val options = if (isAr) {
            arrayOf("1 صفحة (الافتراضي)", "2 صفحتان", "3 صفحات (الحد الآمن)")
        } else {
            arrayOf("1 Page (Default)", "2 Pages", "3 Pages (Safe Limit)")
        }
        val values = arrayOf("1", "2", "3")
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val curVal = prefs.getString("thread_count", "1") ?: "1"
        val checkedItem = values.indexOf(curVal).coerceAtLeast(0)

        com.example.util.DialogHelper.showSingleChoiceDialog(
            context = requireContext(),
            title = getString(R.string.pref_thread_count_title),
            options = options,
            selectedIndex = checkedItem,
            iconRes = R.drawable.ic_router_3d
        ) { which ->
            val selected = values[which]
            prefs.edit().putString("thread_count", selected).apply()
            viewModel.setThreadCount(selected)
            com.example.util.ToastHelper.showSuccessToast(requireContext(), getString(R.string.thread_count_saved_toast, options[which]))
        }
    }

    private fun showColorPickerDialog() {
        val ctx = context ?: return
        val isAr = LocaleHelper.getPersistedLocale(ctx) == "ar"
        val currentKey = ThemePreferences.getPrimaryColorSync(ctx)
        var tempSelectedColor = AppPrimaryColor.fromKey(currentKey)

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_color_picker, null)
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvPreviewName = dialogView.findViewById<TextView>(R.id.tv_preview_color_name)
        val btnPreview = dialogView.findViewById<MaterialButton>(R.id.btn_preview_sample)

        val ivCheckRed = dialogView.findViewById<ImageView>(R.id.iv_check_red)
        val ivCheckBlue = dialogView.findViewById<ImageView>(R.id.iv_check_blue)
        val ivCheckPurple = dialogView.findViewById<ImageView>(R.id.iv_check_purple)
        val ivCheckYellow = dialogView.findViewById<ImageView>(R.id.iv_check_yellow)
        val ivCheckGreen = dialogView.findViewById<ImageView>(R.id.iv_check_green)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_color_picker_cancel)
        val btnApply = dialogView.findViewById<MaterialButton>(R.id.btn_color_picker_apply)

        val dpToPx = { dp: Int -> (dp * ctx.resources.displayMetrics.density).toInt() }
        val itemRed = dialogView.findViewById<View>(R.id.item_color_red)
        val itemBlue = dialogView.findViewById<View>(R.id.item_color_blue)
        val itemPurple = dialogView.findViewById<View>(R.id.item_color_purple)
        val itemYellow = dialogView.findViewById<View>(R.id.item_color_yellow)
        val itemGreen = dialogView.findViewById<View>(R.id.item_color_green)

        val colorViews = listOf(
            AppPrimaryColor.RED to itemRed,
            AppPrimaryColor.BLUE to itemBlue,
            AppPrimaryColor.PURPLE to itemPurple,
            AppPrimaryColor.YELLOW to itemYellow,
            AppPrimaryColor.GREEN to itemGreen
        )

        fun updateItemBorder(view: View, isSelected: Boolean) {
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                if (isSelected) {
                    setColor(Color.parseColor("#26080B"))
                    setStroke(dpToPx(1.5f.toInt().coerceAtLeast(1)), Color.parseColor("#FFFF1E27"))
                } else {
                    setColor(Color.parseColor("#090E18"))
                    setStroke(dpToPx(1), Color.parseColor("#161E2E"))
                }
            }
            view.background = drawable
        }

        fun updateSelectionUi(color: AppPrimaryColor) {
            tempSelectedColor = color
            tvPreviewName.text = color.getDisplayName(isAr)
            btnPreview.backgroundTintList = ColorStateList.valueOf(color.colorInt)
            btnPreview.setTextColor(if (color == AppPrimaryColor.YELLOW) Color.BLACK else Color.WHITE)

            colorViews.forEach { (c, v) ->
                updateItemBorder(v, c == color)
            }

            ivCheckRed.visibility = if (color == AppPrimaryColor.RED) View.VISIBLE else View.GONE
            ivCheckBlue.visibility = if (color == AppPrimaryColor.BLUE) View.VISIBLE else View.GONE
            ivCheckPurple.visibility = if (color == AppPrimaryColor.PURPLE) View.VISIBLE else View.GONE
            ivCheckYellow.visibility = if (color == AppPrimaryColor.YELLOW) View.VISIBLE else View.GONE
            ivCheckGreen.visibility = if (color == AppPrimaryColor.GREEN) View.VISIBLE else View.GONE
        }

        updateSelectionUi(tempSelectedColor)

        dialogView.findViewById<View>(R.id.item_color_red).setOnClickListener {
            updateSelectionUi(AppPrimaryColor.RED)
        }
        dialogView.findViewById<View>(R.id.item_color_blue).setOnClickListener {
            updateSelectionUi(AppPrimaryColor.BLUE)
        }
        dialogView.findViewById<View>(R.id.item_color_purple).setOnClickListener {
            updateSelectionUi(AppPrimaryColor.PURPLE)
        }
        dialogView.findViewById<View>(R.id.item_color_yellow).setOnClickListener {
            updateSelectionUi(AppPrimaryColor.YELLOW)
        }
        dialogView.findViewById<View>(R.id.item_color_green).setOnClickListener {
            updateSelectionUi(AppPrimaryColor.GREEN)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            dialog.dismiss()
            if (tempSelectedColor.key == currentKey) {
                return@setOnClickListener
            }

            com.example.util.DialogHelper.showCustomDialog(
                context = ctx,
                title = getString(R.string.dialog_color_confirm_title),
                message = getString(R.string.dialog_color_confirm_msg),
                dialogType = com.example.util.DialogHelper.DialogType.INFO,
                iconRes = tempSelectedColor.getCircleDrawableRes(),
                positiveButtonText = getString(R.string.dialog_color_confirm_pos),
                positiveAction = {
                    ThemePreferences.setPrimaryColorSync(ctx, tempSelectedColor.key)
                    viewModel.setPrimaryColor(tempSelectedColor.key)
                    activity?.recreate()
                },
                negativeButtonText = getString(R.string.dialog_color_confirm_neg)
            )
        }

        dialog.show()
    }
}
