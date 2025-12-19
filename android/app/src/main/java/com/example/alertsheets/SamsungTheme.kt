package com.example.alertsheets

import android.graphics.Color

/**
 * Samsung One UI Inspired Color Palette
 * Based on modern Samsung Galaxy theme with OLED-optimized colors
 */
object SamsungTheme {
    
    // Primary Brand Colors
    val Blue = Color.parseColor("#4A9EFF")
    val BlueLight = Color.parseColor("#6DB4FF")
    val BlueDark = Color.parseColor("#2D7FDB")
    
    val Green = Color.parseColor("#00D980")
    val GreenLight = Color.parseColor("#2BE594")
    val GreenDark = Color.parseColor("#00B86B")
    
    val Orange = Color.parseColor("#FF8A3D")
    val OrangeLight = Color.parseColor("#FFB800")
    val OrangeDark = Color.parseColor("#FF6B00")
    
    val Red = Color.parseColor("#FF4757")
    val RedLight = Color.parseColor("#FF6B7A")
    val RedDark = Color.parseColor("#E63946")
    
    val Purple = Color.parseColor("#A855F7")
    val PurpleLight = Color.parseColor("#C084FC")
    val PurpleDark = Color.parseColor("#9333EA")
    
    // Backgrounds (OLED Optimized)
    val BackgroundPure = Color.parseColor("#000000")      // Pure black for OLED
    val BackgroundCard = Color.parseColor("#1C1C1E")      // Card background
    val BackgroundElevated = Color.parseColor("#2C2C2E")  // Elevated elements
    val BackgroundDialog = Color.parseColor("#1C1C1E")    // Dialogs
    
    // Text Colors
    val TextPrimary = Color.parseColor("#FFFFFF")         // White
    val TextSecondary = Color.parseColor("#B3B3B3")       // Gray
    val TextDisabled = Color.parseColor("#666666")        // Dark gray
    val TextHint = Color.parseColor("#808080")            // Medium gray
    
    // Feature-Specific Colors (for home cards)
    val ColorPermissions = Blue        // Permissions card
    val ColorPayloads = Green          // Payloads/Config card
    val ColorLogs = Orange             // Logs card
    val ColorApps = Purple             // Apps filter card
    val ColorEndpoints = Red           // Endpoints card
    val ColorSMS = BlueLight           // SMS config card
    
    // Status Colors
    val StatusSuccess = Green
    val StatusWarning = Orange
    val StatusError = Red
    val StatusInfo = Blue
    
    // Dividers & Borders
    val Divider = Color.parseColor("#333333")
    val BorderLight = Color.parseColor("#404040")
    
    // Toggle/Switch Colors
    val ToggleOn = Blue
    val ToggleOff = Color.parseColor("#4D4D4D")
    
    // Helper function for alpha variants
    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}

