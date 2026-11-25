package com.screenai.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

class ScreenAssistService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bubble: View? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureRequested = false
    private val captureTimeout = Runnable {
        captureRequested = false
        Toast.makeText(this, "Tempo esgotado ao capturar. Tente novamente.", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("projectionData")
        if (data != null && projection == null) {
            try {
                val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                projection = manager.getMediaProjection(resultCode, data)
                projection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { stopSelf() }
                }, handler)
                initializeCapture()
                showBubble()
            } catch (_: Exception) {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channelId = "clc_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "CLC · Captura de tela", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("CLC está ativo")
            .setContentText("Toque na bolha para capturar a tela. Toque em Parar para encerrar.")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(77, notification)
    }

    private fun initializeCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        reader?.setOnImageAvailableListener({ source ->
            val image = try { source.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            if (!captureRequested) { image.close(); return@setOnImageAvailableListener }
            captureRequested = false
            handler.removeCallbacks(captureTimeout)
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val full = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buffer)
                image.close()
                val cropped = Bitmap.createBitmap(full, 0, 0, width, height)
                full.recycle()
                val file = File(cacheDir, "clc_capture.jpg")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                cropped.recycle()
                startActivity(Intent(this, AssistActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("imagePath", file.absolutePath))
            } catch (_: Exception) {
                try { image.close() } catch (_: Exception) {}
                Toast.makeText(this, "Não foi possível processar a captura.", Toast.LENGTH_SHORT).show()
            }
        }, handler)

        // Android 14+: a MediaProjection só pode criar um VirtualDisplay por sessão.
        display = projection?.createVirtualDisplay(
            "CLC", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler
        )
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = (56 * resources.displayMetrics.density).toInt()
        val padding = (14 * resources.displayMetrics.density).toInt()
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle)
            setBackgroundResource(R.drawable.bg_bubble)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(padding, padding, padding, padding)
            elevation = 14f
            contentDescription = "Capturar tela com CLC"
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 300 }

        icon.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0f; var startY = 0f; var originX = 0; var originY = 0; var moved = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; originX = params.x; originY = params.y; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (startX - event.rawX).toInt()
                        val dy = (event.rawY - startY).toInt()
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                        params.x = originX + dx; params.y = originY + dy
                        try { wm?.updateViewLayout(view, params) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) capture()
                        return true
                    }
                }
                return false
            }
        })
        bubble = icon
        wm?.addView(icon, params)
    }

    private fun capture() {
        if (captureRequested) return
        if (reader == null || display == null) {
            Toast.makeText(this, "Captura indisponível. Reinicie o CLC.", Toast.LENGTH_SHORT).show()
            return
        }
        captureRequested = true
        handler.removeCallbacks(captureTimeout)
        handler.postDelayed(captureTimeout, 2500)
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureTimeout)
        captureRequested = false
        try { bubble?.let { wm?.removeView(it) } } catch (_: Exception) {}
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
