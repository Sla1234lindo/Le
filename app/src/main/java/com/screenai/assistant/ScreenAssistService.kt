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
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ScreenAssistService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var bubble: View? = null
    private var wm: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureRequested = false
    private var coachMode = false
    private var coachBusy = false
    private var lastCoachAt = 0L
    private val coachIntervalMs = 12000L
    private val coachLoop = object : Runnable { override fun run() { if (coachMode) { captureForCoach(); handler.postDelayed(this, coachIntervalMs) } } }
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
            .setContentText("Toque: analisar tela · Segure: ativar/desativar Coach de Volleyball Legends.")
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
                if (coachMode) {
                    analyzeGameFrame(cropped)
                    cropped.recycle()
                } else {
                val file = File(cacheDir, "clc_capture.jpg")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                cropped.recycle()
                startActivity(Intent(this, AssistActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("imagePath", file.absolutePath))
                }
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
            var startX = 0f; var startY = 0f; var originX = 0; var originY = 0; var moved = false; var downAt = 0L
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX; startY = event.rawY; originX = params.x; originY = params.y; moved = false; downAt = System.currentTimeMillis()
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
                        if (!moved) {\n                            if (System.currentTimeMillis() - downAt >= 650L) toggleCoach() else capture()\n                        }
                        return true
                    }
                }
                return false
            }
        })
        bubble = icon
        wm?.addView(icon, params)
    }

    private fun toggleCoach() {
        coachMode = !coachMode
        if (coachMode) {
            Toast.makeText(this, "Coach de Volleyball Legends ATIVADO · análise a cada 12 s", Toast.LENGTH_LONG).show()
            handler.removeCallbacks(coachLoop)
            handler.post(coachLoop)
        } else {
            handler.removeCallbacks(coachLoop)
            Toast.makeText(this, "Coach desativado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureForCoach() {
        if (!coachMode || captureRequested || coachBusy) return
        if (reader == null || display == null) return
        captureRequested = true
        handler.removeCallbacks(captureTimeout)
        handler.postDelayed(captureTimeout, 2500)
    }

    private fun analyzeGameFrame(bitmap: Bitmap) {
        if (!coachMode || coachBusy) return
        val prefs = getSharedPreferences("clc_settings", MODE_PRIVATE)
        val key = prefs.getString("api_key", "") ?: ""
        val base = (prefs.getString("api_base_url", "https://api.openai.com/v1") ?: "").trim().trimEnd('/')
        val path = prefs.getString("endpoint_path", "/chat/completions") ?: "/chat/completions"
        val model = prefs.getString("api_model", "gpt-4.1-mini") ?: "gpt-4.1-mini"
        if (key.isBlank()) {
            bitmap.recycle()
            coachMode = false
            handler.removeCallbacks(coachLoop)
            Toast.makeText(this, "Configure a chave da API nas configurações do CLC.", Toast.LENGTH_LONG).show()
            return
        }
        val maxDimension = prefs.getInt("image_max_dimension", 1024).coerceIn(512, 1600)
        val ratio = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
        val bytes = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 65, bytes)
        if (scaled !== bitmap) scaled.recycle()
        val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        coachBusy = true
        thread {
            var connection: HttpURLConnection? = null
            val advice = try {
                val endpoint = base + if (path.startsWith("/")) path else "/$path"
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                val prefix = prefs.getString("auth_prefix", "Bearer") ?: "Bearer"
                val header = prefs.getString("auth_header_name", "Authorization") ?: "Authorization"
                val authValue = listOf(prefix, key).filter { it.isNotBlank() }.joinToString(" ")
                if (header.isNotBlank()) connection.setRequestProperty(header, authValue)
                val extras = JSONObject(prefs.getString("extra_headers", "{}") ?: "{}")
                val iterator = extras.keys()
                while (iterator.hasNext()) { val name = iterator.next(); connection.setRequestProperty(name, extras.get(name).toString()) }
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                connection.doOutput = true
                val instruction = "Você é o CLC Coach, especialista em Roblox Volleyball Legends. Analise a captura. Responda em português do Brasil, em no máximo 4 linhas curtas. Identifique apenas o que estiver visível. Se a quadra estiver visível, indique a melhor ação imediata, a zona aparentemente livre (esquerda/centro/direita; fundo/curta) e o risco de bloqueio. Se aparecer seleção/personagem, recomende uma função compatível apenas se identificar o estilo com confiança. Se a imagem não permitir análise, diga o que falta. Não invente informações e nunca controle o jogo.\n\n" + VolleyballLegendsKnowledge.REFERENCE
                val content = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", instruction))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$encoded")))
                val messages = JSONArray().put(JSONObject().put("role", "user").put("content", content))
                val body = JSONObject().put("model", model).put("messages", messages)
                    .put("max_tokens", 180).put("temperature", 0.2).put("stream", false)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IllegalStateException("HTTP $status: ${raw.take(180)}")
                JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "Sem dica disponível.")
            } catch (e: Exception) {
                "Coach: ${e.message ?: "falha na análise"}"
            } finally {
                connection?.disconnect()
            }
            handler.post {
                coachBusy = false
                if (coachMode && advice.isNotBlank()) Toast.makeText(this, advice.take(450), Toast.LENGTH_LONG).show()
            }
        }
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
        handler.removeCallbacks(coachLoop)
        coachMode = false
        captureRequested = false
        try { bubble?.let { wm?.removeView(it) } } catch (_: Exception) {}
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }
}
