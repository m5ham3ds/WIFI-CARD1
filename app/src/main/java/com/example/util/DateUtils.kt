package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    
    fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        return if (minutes > 0) "${minutes}m ${seconds % 60}s" else "${seconds}s"
    }
}
