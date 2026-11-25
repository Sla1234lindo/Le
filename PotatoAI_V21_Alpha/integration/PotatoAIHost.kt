package com.potato.ai.integration

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import com.potato.ai.FloatingAssistantService

/** Bridge helper for an Android host app. */
object PotatoAIHost {
    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    fun start(context: Context) {
        require(hasOverlayPermission(context)) { "Overlay permission has not been granted" }
        val intent = Intent(context, FloatingAssistantService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
        else context.startService(intent)
    }
    fun stop(context: Context) { context.stopService(Intent(context, FloatingAssistantService::class.java)) }
}