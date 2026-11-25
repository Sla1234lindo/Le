package com.screenai.assistant

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import kotlin.concurrent.thread

class AssistActivity : Activity() {
    private lateinit var answer: TextView
    private lateinit var question: EditText
    private lateinit var imageData: String
    private lateinit var apiKey: String

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun resColor(id: Int) = resources.getColor(id, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKey = getSharedPreferences("ScreenAI", 0).getString("api_key", "") ?: ""
        // MainActivity stores in its private preference file; Android's default name is MainActivity.
        apiKey = getSharedPreferences("MainActivity", 0).getString("api_key", apiKey) ?: apiKey
        val path = intent.getStringExtra("imagePath") ?: ""
        val bitmap = BitmapFactory.decodeFile(path)
        val out = ByteArrayOutputStream()
        bitmap?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
        bitmap?.recycle()
        imageData = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

        val scroll = ScrollView(this).apply { setBackgroundColor(resColor(R.color.bg_app)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(22))
        }

        root.addView(TextView(this).apply {
            text = "Pergunte sobre sua tela"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resColor(R.color.text_primary))
        })
        root.addView(TextView(this).apply {
            text = "A imagem é enviada à API somente quando você tocar em Analisar."
            textSize = 13f
            setTextColor(resColor(R.color.text_secondary))
            setPadding(0, dp(6), 0, dp(16))
        })

        // Preview card with rounded corners
        val previewFrame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_card)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }
        val preview = ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeFile(path))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        previewFrame.addView(preview, ViewGroup.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(260)).apply { bottomMargin = dp(16) })

        question = EditText(this).apply {
            hint = "Ex.: O que está acontecendo?"
            setHintTextColor(resColor(R.color.text_hint))
            setTextColor(resColor(R.color.text_primary))
            minLines = 2
            textSize = 15f
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(question, ViewGroup.LayoutParams(-1, -2).also {
            (it as LinearLayout.LayoutParams).bottomMargin = dp(14)
        })

        root.addView(Button(this).apply {
            text = "Analisar tela"
            isAllCaps = false
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resColor(R.color.text_on_primary))
            background = getDrawable(R.drawable.bg_button_primary)
            stateListAnimator = null
            setOnClickListener { ask() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(16) })

        answer = TextView(this).apply {
            textSize = 15f
            setTextColor(resColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_answer)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        root.addView(answer, ViewGroup.LayoutParams(-1, -2))

        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)
    }

    private fun ask() {
        val q = question.text.toString().trim().ifBlank { "Explique o que está acontecendo nesta tela e o que posso fazer." }
        answer.text = "Analisando..."
        thread {
            try {
                val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                val content = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", q))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")))
                val body = JSONObject()
                    .put("model", "gpt-4o-mini")
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                    .put("max_tokens", 700)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().readText()
                val json = JSONObject(response)
                val result = if (conn.responseCode in 200..299)
                    json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                else "Erro da API: " + (json.optJSONObject("error")?.optString("message", response) ?: response)
                runOnUiThread { answer.text = result }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread { answer.text = "Não consegui conectar: ${e.message}" }
            }
        }
    }
}
