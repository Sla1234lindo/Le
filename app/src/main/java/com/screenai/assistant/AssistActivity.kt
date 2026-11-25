package com.screenai.assistant
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.Typeface
import android.os.Bundle
import android.util.Base64
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
    private lateinit var answer: TextView
    private lateinit var question: EditText
    private lateinit var sendButton: Button
    private lateinit var imageData: String
    private lateinit var apiKey: String
    private lateinit var baseUrl: String
    private lateinit var model: String
    private val conversation = JSONArray()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = resources.getColor(id, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("MainActivity", 0)
        apiKey = prefs.getString("api_key", "") ?: ""
        baseUrl = (prefs.getString("api_base_url", "https://api.openai.com/v1") ?: "").trim().trimEnd('/')
        model = prefs.getString("api_model", "gpt-4o-mini") ?: "gpt-4o-mini"

        val path = intent.getStringExtra("imagePath") ?: ""
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap == null) {
            Toast.makeText(this, "Não foi possível carregar a captura.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        imageData = encodeImage(bitmap)

        val scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.bg_app)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        root.addView(TextView(this).apply {
            text = "Converse com a tela"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.text_primary))
        })
        root.addView(TextView(this).apply {
            text = "Faça perguntas, peça explicações ou continue a conversa."
            textSize = 13f; setTextColor(color(R.color.text_secondary)); setPadding(0, dp(5), 0, dp(14))
        })

        val previewFrame = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_card); clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(22).toFloat())
                }
            }
        }
        previewFrame.addView(ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER },
            FrameLayout.LayoutParams(-1, -1))
        root.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(230)).apply { bottomMargin = dp(14) })

        val shortcuts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Resuma", "Explique", "O que fazer?").forEach { prompt ->
            shortcuts.addView(Button(this).apply {
                text = prompt; isAllCaps = false; textSize = 11f
                setTextColor(color(R.color.primary)); background = getDrawable(R.drawable.bg_button_outline)
                stateListAnimator = null; setOnClickListener { question.setText(prompt) }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        }
        root.addView(shortcuts, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        question = EditText(this).apply {
            hint = "Digite sua pergunta..."; setHintTextColor(color(R.color.text_hint))
            setTextColor(color(R.color.text_primary)); minLines = 2; maxLines = 4; textSize = 15f
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(question, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        sendButton = Button(this).apply {
            text = "Enviar pergunta"; isAllCaps = false; textSize = 15f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(color(R.color.text_on_primary))
            background = getDrawable(R.drawable.bg_button_primary); stateListAnimator = null
            setOnClickListener { ask() }
        }
        root.addView(sendButton, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })
        answer = TextView(this).apply {
            text = "As respostas aparecerão aqui. Você pode fazer várias perguntas sobre a mesma captura."
            textSize = 14f; setTextColor(color(R.color.text_primary)); setBackgroundResource(R.drawable.bg_answer)
            setPadding(dp(16), dp(14), dp(16), dp(14)); setLineSpacing(dp(3).toFloat(), 1f)
        }
        root.addView(answer, ViewGroup.LayoutParams(-1, -2))
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2)); setContentView(scroll)
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val scaled = if (bitmap.width > 1600 || bitmap.height > 1600) {
            val ratio = minOf(1600f / bitmap.width, 1600f / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else bitmap
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun ask() {
        val prompt = question.text.toString().trim().ifBlank { "Explique o que está acontecendo nesta tela e o que posso fazer." }
        question.setText("")
        sendButton.isEnabled = false; sendButton.text = "Pensando..."
        answer.text = "A IA está analisando sua pergunta..."
        thread {
            var connection: HttpURLConnection? = null
            try {
                val content = JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
                if (conversation.length() == 0) {
                    content.put(JSONObject().put("type", "image_url").put(
                        "image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")
                    ))
                }
                conversation.put(JSONObject().put("role", "user").put("content", content))
                val messages = JSONArray().put(JSONObject().put("role", "system").put(
                    "content", "Você é o CLC, um assistente visual útil. Responda no idioma do usuário, seja claro e preciso. Não invente detalhes que não estejam visíveis."
                ))
                for (i in 0 until conversation.length()) messages.put(conversation.getJSONObject(i))

                val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = 20000; connection.readTimeout = 60000; connection.doOutput = true
                val body = JSONObject().put("model", model).put("messages", messages)
                    .put("max_tokens", 900).put("temperature", 0.5)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = JSONObject(raw)
                if (status !in 200..299) {
                    conversation.remove(conversation.length() - 1)
                    val message = json.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() } ?: "HTTP $status: $raw"
                    throw IllegalStateException(message)
                }
                val message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                val response = when (val value = message.opt("content")) {
                    is String -> value
                    is JSONArray -> buildString {
                        for (i in 0 until value.length()) append(value.optJSONObject(i)?.optString("text").orEmpty())
                    }
                    else -> "A API retornou uma resposta vazia."
                }.ifBlank { "A API retornou uma resposta vazia." }
                conversation.put(JSONObject().put("role", "assistant").put("content", response))
                runOnUiThread { answer.text = response }
            } catch (error: Exception) {
                runOnUiThread { answer.text = "Não foi possível consultar a IA.\n\n" + (error.message ?: "Erro desconhecido") }
            } finally {
                connection?.disconnect()
                runOnUiThread { sendButton.isEnabled = true; sendButton.text = "Enviar pergunta" }
            }
        }
    }
}
