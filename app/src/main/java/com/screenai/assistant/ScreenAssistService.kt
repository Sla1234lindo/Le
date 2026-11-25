package com.screenai.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

class ScreenAssistService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bubble: View? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("projectionData")
        if (data != null && projection == null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, handler)
            showBubble()
        }
        return START_NOT_STICKY
    }
    private fun createNotification() {
        val channelId = "screenai_capture"
        if (Build.VERSION.SDK_INT >= 26) { val channel = NotificationChannel(channelId, "ScreenAI captura de tela", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(channel) }
        val n = Notification.Builder(this, channelId).setContentTitle("ScreenAI ativo").setContentText("Toque na bolinha para analisar a tela").setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(77, n)
    }
    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val paddingPx = (14 * resources.displayMetrics.density).toInt()
        val tv = ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle)
            setBackgroundResource(R.drawable.bg_bubble)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            elevation = 14f
            setOnClickListener { capture() }
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(sizePx, sizePx, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 300 }
        tv.setOnTouchListener(object : View.OnTouchListener { var sx=0f; var sy=0f; var ox=0; var oy=0; var moved=false
            override fun onTouch(v: View, e: MotionEvent): Boolean { when(e.action) { MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=lp.x; oy=lp.y; moved=false; return true }; MotionEvent.ACTION_MOVE -> { val dx=(sx-e.rawX).toInt(); val dy=(e.rawY-sy).toInt(); if(kotlin.math.abs(dx)>8||kotlin.math.abs(dy)>8)moved=true; lp.x=ox+dx; lp.y=oy+dy; wm?.updateViewLayout(v,lp); return true }; MotionEvent.ACTION_UP -> { if(!moved) capture(); return true } }; return false }
        })
        bubble = tv
        wm?.addView(tv, lp)
    }
    private fun capture() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels; val h = metrics.heightPixels; val dpi = metrics.densityDpi
        reader?.close(); reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display?.release()
        display = projection?.createVirtualDisplay("ScreenAI", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
        handler.postDelayed({
            try {
                val image = reader?.acquireLatestImage() ?: return@postDelayed
                val plane = image.planes[0]; val buffer = plane.buffer; val pixelStride=plane.pixelStride; val rowStride=plane.rowStride; val rowPadding=rowStride-pixelStride*w
                val bmp=Bitmap.createBitmap(w+rowPadding/pixelStride,h,Bitmap.Config.ARGB_8888); bmp.copyPixelsFromBuffer(buffer); image.close()
                val cropped=Bitmap.createBitmap(bmp,0,0,w,h); bmp.recycle()
                val file=File(cacheDir,"screenai_capture.jpg"); FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG,80,it) }; cropped.recycle()
                startActivity(Intent(this, AssistActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("imagePath",file.absolutePath))
            } catch (_: Exception) { }
        }, 350)
    }
    override fun onDestroy() { try { bubble?.let { wm?.removeView(it) } } catch (_: Exception) {}; display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
}
