package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.R
import com.example.data.local.preferences.ThemePreferences
import com.example.domain.model.AppPrimaryColor
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object DialogHelper {

    enum class DialogType {
        SUCCESS, WARNING, ERROR, INFO
    }

    fun showCustomDialog(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        dialogType: DialogType = DialogType.INFO,
        iconRes: Int? = null,
        positiveButtonText: CharSequence? = null,
        positiveAction: (() -> Unit)? = null,
        negativeButtonText: CharSequence? = null,
        negativeAction: (() -> Unit)? = null,
        neutralButtonText: CharSequence? = null,
        neutralAction: (() -> Unit)? = null,
        isCancelable: Boolean = true
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_alert, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setCancelable(isCancelable)
            .create()

        // Set transparent window background so our rounded card works
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.dialog_message)
        val imgIcon = view.findViewById<ImageView>(R.id.dialog_icon)
        val iconContainer = view.findViewById<FrameLayout>(R.id.dialog_icon_container)

        val btnPositive = view.findViewById<MaterialButton>(R.id.btn_dialog_positive)
        val btnNegative = view.findViewById<MaterialButton>(R.id.btn_dialog_negative)
        val btnNeutral = view.findViewById<MaterialButton>(R.id.btn_dialog_neutral)

        // Set Title & Message
        tvTitle.text = title
        tvMessage.text = message

        val primaryColorKey = ThemePreferences.getPrimaryColorSync(context)
        val appColor = AppPrimaryColor.fromKey(primaryColorKey)

        // Set custom or default icon depending on dialogType
        if (iconRes != null) {
            imgIcon.setImageResource(iconRes)
            when (iconRes) {
                R.drawable.ic_check_circle_green, R.drawable.ic_cross_circle_red -> {
                    imgIcon.imageTintList = null
                }
                R.drawable.ic_nav_exit, R.drawable.ic_delete, R.drawable.ic_stop_modern -> {
                    imgIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF1E27"))
                }
                R.drawable.ic_info -> {
                    val tint = when (dialogType) {
                        DialogType.WARNING -> Color.parseColor("#FFA000")
                        DialogType.ERROR -> Color.parseColor("#FF1E27")
                        DialogType.SUCCESS -> Color.parseColor("#00E676")
                        DialogType.INFO -> appColor.colorInt
                    }
                    imgIcon.imageTintList = android.content.res.ColorStateList.valueOf(tint)
                }
                else -> {
                    // Retain native colors for circle color drawables and custom icons
                    imgIcon.imageTintList = null
                }
            }
        } else {
            when (dialogType) {
                DialogType.SUCCESS -> {
                    imgIcon.setImageResource(R.drawable.ic_check_circle_green)
                    imgIcon.imageTintList = null
                }
                DialogType.WARNING -> {
                    imgIcon.setImageResource(R.drawable.ic_info)
                    imgIcon.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFA000"))
                }
                DialogType.ERROR -> {
                    imgIcon.setImageResource(R.drawable.ic_cross_circle_red)
                    imgIcon.imageTintList = null
                }
                DialogType.INFO -> {
                    imgIcon.setImageResource(R.drawable.ic_info)
                    imgIcon.imageTintList = android.content.res.ColorStateList.valueOf(appColor.colorInt)
                }
            }
        }

        // Set Cyber Badge Background for Icon
        val bgDrawable = when (dialogType) {
            DialogType.SUCCESS -> R.drawable.bg_dialog_badge_green
            DialogType.WARNING -> R.drawable.bg_dialog_badge_orange
            DialogType.ERROR -> R.drawable.bg_dialog_badge_red
            DialogType.INFO -> when (appColor) {
                AppPrimaryColor.RED -> R.drawable.bg_dialog_badge_red
                AppPrimaryColor.BLUE -> R.drawable.bg_dialog_badge_blue
                AppPrimaryColor.GREEN -> R.drawable.bg_dialog_badge_green
                AppPrimaryColor.YELLOW -> R.drawable.bg_dialog_badge_orange
                AppPrimaryColor.PURPLE -> R.drawable.bg_dialog_badge_purple
            }
        }
        iconContainer.setBackgroundResource(bgDrawable)

        // Set Positive Button Background based on Type
        if (dialogType == DialogType.SUCCESS) {
            btnPositive.setBackgroundResource(R.drawable.bg_dialog_btn_success)
        } else {
            btnPositive.setBackgroundResource(R.drawable.bg_dialog_btn_primary)
        }

        // Set Positive Button
        if (positiveButtonText != null) {
            btnPositive.text = positiveButtonText
        } else {
            btnPositive.text = context.getString(R.string.dialog_ok)
        }
        btnPositive.setOnClickListener {
            dialog.dismiss()
            positiveAction?.invoke()
        }

        // Set Negative Button
        if (negativeButtonText != null) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = negativeButtonText
            btnNegative.setOnClickListener {
                dialog.dismiss()
                negativeAction?.invoke()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        // Set Neutral Button
        if (neutralButtonText != null) {
            btnNeutral.visibility = View.VISIBLE
            btnNeutral.text = neutralButtonText
            btnNeutral.setOnClickListener {
                dialog.dismiss()
                neutralAction?.invoke()
            }
        } else {
            btnNeutral.visibility = View.GONE
        }

        dialog.show()
        return dialog
    }

    fun showSingleChoiceDialog(
        context: Context,
        title: CharSequence,
        options: Array<String>,
        selectedIndex: Int,
        iconRes: Int = R.drawable.ic_palette,
        onSelected: (Int) -> Unit
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_custom_selection, null)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = view.findViewById<TextView>(R.id.dialog_selection_title)
        val imgIcon = view.findViewById<ImageView>(R.id.dialog_selection_icon)
        val optionsContainer = view.findViewById<android.widget.LinearLayout>(R.id.dialog_selection_options_container)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_dialog_selection_cancel)

        tvTitle.text = title
        imgIcon.setImageResource(iconRes)

        val primaryColorKey = ThemePreferences.getPrimaryColorSync(context)
        val appColor = AppPrimaryColor.fromKey(primaryColorKey)

        val iconContainer = view.findViewById<FrameLayout>(R.id.dialog_selection_icon_container)
        val (bgBadge, tintColor) = when (iconRes) {
            R.drawable.ic_timer -> Pair(R.drawable.bg_dialog_badge_orange, Color.parseColor("#FFA000"))
            R.drawable.ic_check_circle_green -> Pair(R.drawable.bg_dialog_badge_green, Color.parseColor("#00E676"))
            R.drawable.ic_palette -> {
                val badge = when (appColor) {
                    AppPrimaryColor.RED -> R.drawable.bg_dialog_badge_red
                    AppPrimaryColor.BLUE -> R.drawable.bg_dialog_badge_blue
                    AppPrimaryColor.GREEN -> R.drawable.bg_dialog_badge_green
                    AppPrimaryColor.YELLOW -> R.drawable.bg_dialog_badge_orange
                    AppPrimaryColor.PURPLE -> R.drawable.bg_dialog_badge_purple
                }
                Pair(badge, appColor.colorInt)
            }
            R.drawable.ic_info -> Pair(R.drawable.bg_dialog_badge_blue, Color.parseColor("#2979FF"))
            else -> Pair(R.drawable.bg_dialog_badge_red, Color.parseColor("#FF1E27"))
        }
        iconContainer.setBackgroundResource(bgBadge)
        if (iconRes == R.drawable.ic_check_circle_green) {
            imgIcon.imageTintList = null
        } else {
            imgIcon.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        val dpToPx = { dp: Int -> (dp * context.resources.displayMetrics.density).toInt() }

        val selBorderColor = when {
            iconRes == R.drawable.ic_timer -> Color.parseColor("#FFA000")
            else -> appColor.colorInt
        }
        val selBgColor = when {
            iconRes == R.drawable.ic_timer -> Color.parseColor("#241605")
            appColor == AppPrimaryColor.BLUE -> Color.parseColor("#081426")
            appColor == AppPrimaryColor.GREEN -> Color.parseColor("#072212")
            appColor == AppPrimaryColor.PURPLE -> Color.parseColor("#1F0D2E")
            appColor == AppPrimaryColor.YELLOW -> Color.parseColor("#262208")
            else -> Color.parseColor("#26080B")
        }

        options.forEachIndexed { index, optionText ->
            val isSelected = index == selectedIndex
            val itemLayout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(8))
                }
                setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))

                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(14).toFloat()
                    if (isSelected) {
                        setColor(selBgColor)
                        setStroke(dpToPx(1.5f.toInt().coerceAtLeast(1)), selBorderColor)
                    } else {
                        setColor(Color.parseColor("#090E18"))
                        setStroke(dpToPx(1), Color.parseColor("#1E283C"))
                    }
                }
                background = drawable
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    dialog.dismiss()
                    onSelected(index)
                }
            }

            val indicator = ImageView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                    setMargins(0, 0, dpToPx(12), 0)
                }
                if (isSelected) {
                    setImageResource(R.drawable.ic_check_circle_green)
                    imageTintList = null
                } else {
                    setImageResource(R.drawable.bg_status_dot)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#334155"))
                }
            }

            val textView = TextView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = optionText
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#CBD5E1"))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            }

            itemLayout.addView(textView)
            itemLayout.addView(indicator)
            optionsContainer.addView(itemLayout)
        }

        dialog.show()
        return dialog
    }
}
