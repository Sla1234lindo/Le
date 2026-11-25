package com.screenai.assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
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
import kotlin.concurrent.thread

class AssistActivity : Activity() {
    private lateinit var chat: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var question: EditText
    private lateinit var sendButton: Button
    private lateinit var imageData: String
    private lateinit var apiKey: String
    private lateinit var baseUrl: String
    private lateinit var endpointPath: String
    private lateinit var model: String
    private lateinit var systemPrompt: String
    private lateinit var authHeaderName: String
    private lateinit var authPrefix: String
    private lateinit var extraHeaders: String
    private var jsonResponse = false
    private var temperature = 0.5f
    private var maxTokens = 900
    private var contextTurns = 6
    private var imageQuality = 78
    private var imageMaxDimension = 1280
    private var timeoutSeconds = 60
    private val conversation = JSONArray()
    private var lastAnswer = ""
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = resources.getColor(id, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("clc_settings", MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""
        baseUrl = (prefs.getString("api_base_url", "https://api.openai.com/v1") ?: "").trim().trimEnd('/')
        endpointPath = prefs.getString("endpoint_path", "/chat/completions") ?: "/chat/completions"
        model = prefs.getString("api_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        systemPrompt = prefs.getString("system_prompt", "Você é o CLC, um assistente visual útil. Responda em português, com clareza e precisão.") ?: ""
        authHeaderName = prefs.getString("auth_header_name", "Authorization") ?: "Authorization"
        authPrefix = prefs.getString("auth_prefix", "Bearer") ?: "Bearer"
        extraHeaders = prefs.getString("extra_headers", "{}") ?: "{}"
        jsonResponse = prefs.getBoolean("json_response", false)
        temperature = prefs.getFloat("temperature", 0.5f)
        maxTokens = prefs.getInt("max_tokens", 900)
        contextTurns = prefs.getInt("context_turns", 6)
        imageQuality = prefs.getInt("image_quality", 78)
        imageMaxDimension = prefs.getInt("image_max_dimension", 1280)
        timeoutSeconds = prefs.getInt("timeout_seconds", 60)

        val path = intent.getStringExtra("imagePath") ?: ""
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Toast.makeText(this, "Não foi possível carregar a captura.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        imageData = encodeImage(bitmap)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(color(R.color.bg_app)); setPadding(dp(16), dp(18), dp(16), dp(12)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        heading.addView(TextView(this).apply { text = "CLC · Assistente"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color(R.color.text_primary)) })
        heading.addView(TextView(this).apply { text = "Modelo: $model"; textSize = 11f; setTextColor(color(R.color.text_secondary)) })
        header.addView(heading)
        header.addView(smallAction("Copiar") { copyLastAnswer() })
        header.addView(smallAction("Limpar") { clearConversation() })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val preview = FrameLayout(this).apply { setBackgroundResource(R.drawable.bg_card); clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) { outline.setRoundRect(0, 0, view.width, view.height, dp(18).toFloat()) }
            }
        }
        preview.addView(ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(10) })

        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Resuma", "Explique", "Traduza", "Passo a passo").forEach { prompt ->
            shortcuts.addView(smallAction(prompt) { question.setText(prompt) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) })
        }
        root.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        chatScroll = ScrollView(this).apply { isFillViewport = true }
        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(8)) }
        chatScroll.addView(chat, ViewGroup.LayoutParams(-1, -2))
        root.addView(chatScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addMessage("CLC", "Captura pronta. Pergunte qualquer coisa sobre o que aparece na tela.", false)

        question = EditText(this).apply {
            hint = "Pergunte ao CLC..."; setHintTextColor(color(R.color.text_hint))
            setTextColor(color(R.color.text_primary)); minLines = 1; maxLines = 4; textSize = 14f
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(13), dp(11), dp(13), dp(11))
        }
        root.addView(question, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8); topMargin = dp(8) })
        sendButton = Button(this).apply {
            text = "Enviar mensagem"; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.text_on_primary)); background = getDrawable(R.drawable.bg_button_primary)
            stateListAnimator = null; setOnClickListener { ask() }
        }
        root.addView(sendButton, LinearLayout.LayoutParams(-1, dp(48)))
        setContentView(root)
    }

    private fun smallAction(textValue: String, action: () -> Unit) = Button(this).apply {
        text = textValue; isAllCaps = false; textSize = 10f; minWidth = 0; minimumWidth = 0
        setPadding(dp(4), 0, dp(4), 0); setTextColor(color(R.color.primary))
        background = getDrawable(R.drawable.bg_button_outline); stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun addMessage(sender: String, textValue: String, user: Boolean) {
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (user) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(4))
        }
        wrapper.addView(TextView(this).apply {
            text = sender; textSize = 10f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.text_secondary)); setPadding(dp(5), 0, dp(5), dp(3))
        })
        wrapper.addView(TextView(this).apply {
            text = textValue; textSize = 14f; setTextColor(color(R.color.text_primary))
            setBackgroundResource(if (user) R.drawable.bg_user_message else R.drawable.bg_answer)
            setPadding(dp(13), dp(10), dp(13), dp(10)); setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2))
        chat.addView(wrapper, ViewGroup.LayoutParams(-1, -2))
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val ratio = minOf(1f, imageMaxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true) else bitmap
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, imageQuality, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildMessages(): JSONArray {
        val result = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        if (conversation.length() == 0) return result
        val keep = contextTurns * 2
        val start = if (conversation.length() > keep + 1) conversation.length() - keep else 0
        result.put(conversation.getJSONObject(0))
        for (i in maxOf(1, start) until conversation.length()) result.put(conversation.getJSONObject(i))
        return result
    }

    private fun ask() {
        val prompt = question.text.toString().trim()
        if (prompt.isBlank()) { question.error = "Digite uma pergunta"; return }
        question.setText("")
        addMessage("Você", prompt, true)
        sendButton.isEnabled = false; sendButton.text = "Pensando..."
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
        if (conversation.length() == 0) content.put(JSONObject().put("type", "image_url").put(
            "image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")
        ))
        conversation.put(JSONObject().put("role", "user").put("content", content))
        addMessage("CLC", "Gerando resposta...", false)
        val pendingIndex = chat.childCount - 1

        thread {
            var connection: HttpURLConnection? = null
            try {
                val endpoint = baseUrl + if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                val authValue = listOf(authPrefix, apiKey).filter { it.isNotBlank() }.joinToString(" ")
                if (authHeaderName.isNotBlank() && authValue.isNotBlank()) connection.setRequestProperty(authHeaderName, authValue)
                val headers = JSONObject(extraHeaders)
                val headerIterator = headers.keys()
                while (headerIterator.hasNext()) {
                    val header = headerIterator.next()
                    connection.setRequestProperty(header, headers.get(header).toString())
                }
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = 15000
                connection.readTimeout = timeoutSeconds * 1000
                connection.doOutput = true
                val body = JSONObject().put("model", model).put("messages", buildMessages())
                    .put("max_tokens", maxTokens).put("temperature", temperature).put("stream", false)
                if (jsonResponse) body.put("response_format", JSONObject().put("type", "json_object"))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(raw)
                if (status !in 200..299) {
                    conversation.remove(conversation.length() - 1)
                    val error = json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "HTTP $status: $raw"
                    throw IllegalStateException(error)
                }
                val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val response = when (val value = message.opt("content")) {
                    is String -> value
                    is JSONArray -> buildString { for (i in 0 until value.length()) append(value.optJSONObject(i)?.optString("text").orEmpty()) }
                    else -> "A API retornou uma resposta vazia."
                }.ifBlank { "A API retornou uma resposta vazia." }
                conversation.put(JSONObject().put("role", "assistant").put("content", response))
                lastAnswer = response
                runOnUiThread { updatePending(pendingIndex, response) }
            } catch (e: Exception) {
                conversation.remove(conversation.length() - 1)
                runOnUiThread { updatePending(pendingIndex, "Erro: ${e.message ?: "não foi possível consultar a IA"}") }
            } finally {
                connection?.disconnect()
                runOnUiThread { sendButton.isEnabled = true; sendButton.text = "Enviar mensagem" }
            }
        }
    }

    private fun updatePending(index: Int, value: String) {
        if (index >= 0 && index < chat.childCount) {
            val wrapper = chat.getChildAt(index) as? LinearLayout
            val message = wrapper?.getChildAt(1) as? TextView
            message?.text = value
        }
    }

    private fun copyLastAnswer() {
        if (lastAnswer.isBlank()) { Toast.makeText(this, "Ainda não há resposta para copiar.", Toast.LENGTH_SHORT).show(); return }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Resposta do CLC", lastAnswer))
        Toast.makeText(this, "Resposta copiada.", Toast.LENGTH_SHORT).show()
    }

    private fun clearConversation() {
        conversation.clear()
        lastAnswer = ""
        chat.removeAllViews()
        addMessage("CLC", "Conversa limpa. A próxima pergunta usará novamente a captura original.", false)
    }
}
