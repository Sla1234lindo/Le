package com.screenai.assistant

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val captureRequest = 701
    private lateinit var apiKey: EditText
    private lateinit var baseUrl: EditText
    private lateinit var endpointPath: EditText
    private lateinit var model: EditText
    private lateinit var systemPrompt: EditText
    private lateinit var temperature: SeekBar
    private lateinit var temperatureValue: TextView
    private lateinit var maxTokens: EditText
    private lateinit var contextTurns: EditText
    private lateinit var imageSize: Spinner
    private lateinit var imageQuality: SeekBar
    private lateinit var imageQualityValue: TextView
    private lateinit var timeout: EditText
    private lateinit var authHeaderName: EditText
    private lateinit var authPrefix: EditText
    private lateinit var extraHeaders: EditText
    private lateinit var responseFormat: Spinner
    private lateinit var testButton: Button
    private val prefs by lazy { getSharedPreferences("clc_settings", MODE_PRIVATE) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = resources.getColor(id, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.bg_app)); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(24), dp(20), dp(28)) }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        heading.addView(TextView(this).apply { text = "CLC"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color(R.color.text_primary)) })
        heading.addView(TextView(this).apply { text = "Seu espaço de inteligência"; textSize = 13f; setTextColor(color(R.color.text_secondary)) })
        top.addView(heading)
        top.addView(TextView(this).apply { text = "Tema do sistema"; textSize = 11f; setTextColor(color(R.color.text_secondary)) })
        root.addView(top, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val hero = card().apply { gravity = Gravity.CENTER; setPadding(dp(18), dp(20), dp(18), dp(20)) }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle); setBackgroundResource(R.drawable.bg_bubble)
            scaleType = ImageView.ScaleType.CENTER; elevation = dp(5).toFloat()
        }, LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(10) })
        hero.addView(TextView(this).apply { text = "Seu assistente, suas regras"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color(R.color.text_primary)) })
        hero.addView(TextView(this).apply {
            text = "Configure a IA, personalize as respostas e controle como suas capturas são enviadas."
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(color(R.color.text_secondary))
            setPadding(dp(6), dp(5), dp(6), 0)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val provider = card()
        provider.addView(title("01 · Provedor e modelo"))
        provider.addView(label("Configuração rápida"))
        val presets = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Personalizado / OpenAI-compatible", "OpenAI", "OpenRouter", "Groq", "Together AI"))
            setSelection(0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val preset = when (position) {
                        1 -> "https://api.openai.com/v1"
                        2 -> "https://openrouter.ai/api/v1"
                        3 -> "https://api.groq.com/openai/v1"
                        4 -> "https://api.together.xyz/v1"
                        else -> null
                    }
                    if (preset != null && baseUrl.text.isNullOrBlank()) baseUrl.setText(preset)
                    if (preset != null) baseUrl.setText(preset)
                }
            }
        }
        provider.addView(presets, fieldParams())
        provider.addView(label("URL base"))
        baseUrl = field("https://api.openai.com/v1", prefs.getString("api_base_url", "") ?: "")
        provider.addView(baseUrl, fieldParams())
        provider.addView(label("Caminho do endpoint"))
        endpointPath = field("/chat/completions", prefs.getString("endpoint_path", "/chat/completions") ?: "/chat/completions")
        provider.addView(endpointPath, fieldParams())
        provider.addView(label("Nome/ID exato do modelo"))
        model = field("Ex.: gpt-4o-mini ou modelo do provedor", prefs.getString("api_model", "") ?: "")
        provider.addView(model, fieldParams())
        provider.addView(label("Chave de API"))
        apiKey = field("Cole sua chave privada", prefs.getString("api_key", "") ?: "", true)
        provider.addView(apiKey, fieldParams())
        provider.addView(label("Nome do cabeçalho de autenticação"))
        authHeaderName = field("Authorization", prefs.getString("auth_header_name", "Authorization") ?: "Authorization")
        provider.addView(authHeaderName, fieldParams())
        provider.addView(label("Prefixo da chave (ex.: Bearer)"))
        authPrefix = field("Bearer", prefs.getString("auth_prefix", "Bearer") ?: "Bearer")
        provider.addView(authPrefix, fieldParams())
        provider.addView(label("Cabeçalhos extras em JSON (opcional)"))
        extraHeaders = field("{\"HTTP-Referer\":\"https://seusite.com\"}", prefs.getString("extra_headers", "") ?: "", multiline = true)
        extraHeaders.minLines = 2
        provider.addView(extraHeaders, LinearLayout.LayoutParams(-1, dp(76)))
        provider.addView(note("Use a URL base e o ID de modelo indicados pelo seu provedor. A API precisa aceitar Chat Completions e imagens para analisar capturas."))
        root.addView(provider, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val intelligence = card()
        intelligence.addView(title("02 · Inteligência e respostas"))
        intelligence.addView(label("Criatividade · temperatura"))
        temperatureValue = valueText()
        intelligence.addView(temperatureValue)
        temperature = SeekBar(this).apply {
            max = 20
            progress = (prefs.getFloat("temperature", 0.5f) * 10).toInt().coerceIn(0, 20)
            setOnSeekBarChangeListener(seekListener { temperatureValue.text = String.format("%.1f", it / 10f) })
        }
        intelligence.addView(temperature, fieldParams())
        intelligence.addView(label("Limite máximo de tokens"))
        maxTokens = numberField("900", prefs.getInt("max_tokens", 900).toString())
        intelligence.addView(maxTokens, fieldParams())
        intelligence.addView(label("Histórico enviado por pergunta (turnos)"))
        contextTurns = numberField("6", prefs.getInt("context_turns", 6).toString())
        intelligence.addView(contextTurns, fieldParams())
        intelligence.addView(label("Instrução personalizada para a IA"))
        systemPrompt = field("Ex.: Responda em português, direto e com exemplos...", prefs.getString("system_prompt", DEFAULT_PROMPT) ?: DEFAULT_PROMPT, multiline = true)
        systemPrompt.minLines = 3
        systemPrompt.maxLines = 7
        intelligence.addView(systemPrompt, LinearLayout.LayoutParams(-1, dp(112)))
        intelligence.addView(note("Essa instrução define o comportamento padrão do CLC em todas as conversas."))
        intelligence.addView(label("Formato da resposta"))
        responseFormat = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Texto normal", "JSON estruturado"))
            setSelection(if (prefs.getBoolean("json_response", false)) 1 else 0)
        }
        intelligence.addView(responseFormat, fieldParams())
        root.addView(intelligence, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val capture = card()
        capture.addView(title("03 · Captura e qualidade"))
        capture.addView(label("Resolução máxima enviada"))
        imageSize = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("768 px · mais econômico", "1024 px · equilibrado", "1280 px · alta", "1600 px · máxima"))
            val values = listOf(768, 1024, 1280, 1600)
            setSelection(values.indexOf(prefs.getInt("image_max_dimension", 1280)).coerceAtLeast(0))
        }
        capture.addView(imageSize, fieldParams())
        capture.addView(label("Qualidade JPEG"))
        imageQualityValue = valueText()
        capture.addView(imageQualityValue)
        imageQuality = SeekBar(this).apply {
            max = 55
            progress = (prefs.getInt("image_quality", 78) - 40).coerceIn(0, 55)
            setOnSeekBarChangeListener(seekListener { imageQualityValue.text = "${it + 40}%" })
        }
        capture.addView(imageQuality, fieldParams())
        capture.addView(label("Tempo limite da requisição (segundos)"))
        timeout = numberField("60", prefs.getInt("timeout_seconds", 60).toString())
        capture.addView(timeout, fieldParams())
        root.addView(capture, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val actions = card()
        actions.addView(title("04 · Ferramentas"))
        testButton = button("Testar conexão com a IA", false) { testConnection() }
        actions.addView(testButton, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        actions.addView(button("Apagar chave salva", false) {
            prefs.edit().remove("api_key").apply()
            apiKey.setText("")
            Toast.makeText(this, "Chave removida do armazenamento local.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        actions.addView(button("Salvar configurações", true) {
            if (saveSettings()) Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(button("Salvar e ativar bolha flutuante", true) { activate() }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        root.addView(button("Parar assistente", false) {
            stopService(Intent(this, ScreenAssistService::class.java))
            Toast.makeText(this, "Assistente parado.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(50)))
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)
        temperatureValue.text = String.format("%.1f", temperature.progress / 10f)
        imageQualityValue.text = "${imageQuality.progress + 40}%"
    }

    private fun seekListener(update: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = update(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(17), dp(17), dp(17))
        setBackgroundResource(R.drawable.bg_card); elevation = dp(2).toFloat()
    }
    private fun title(v: String) = TextView(this).apply {
        text = v; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.text_primary)); setPadding(0, 0, 0, dp(8))
    }
    private fun label(v: String) = TextView(this).apply {
        text = v; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.text_primary)); setPadding(0, dp(9), 0, dp(5))
    }
    private fun note(v: String) = TextView(this).apply {
        text = v; textSize = 12f; setTextColor(color(R.color.text_secondary))
        setPadding(0, dp(6), 0, dp(4)); setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun valueText() = TextView(this).apply { textSize = 12f; setTextColor(color(R.color.primary)) }
    private fun field(h: String, v: String, secret: Boolean = false, multiline: Boolean = false) = EditText(this).apply {
        hint = h; setHintTextColor(color(R.color.text_hint)); setTextColor(color(R.color.text_primary))
        textSize = 14f; singleLine = !multiline
        inputType = if (secret) 129 else if (multiline) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        setBackgroundResource(R.drawable.bg_input); setPadding(dp(13), dp(11), dp(13), dp(11)); setText(v)
    }
    private fun numberField(h: String, v: String) = field(h, v).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(3) }
    private fun button(v: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = v; isAllCaps = false; textSize = 14f; stateListAnimator = null
        typeface = if (primary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(color(if (primary) R.color.text_on_primary else R.color.primary))
        background = getDrawable(if (primary) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        setOnClickListener { action() }
    }

    private fun saveSettings(): Boolean {
        val key = apiKey.text.toString().trim()
        val url = baseUrl.text.toString().trim().trimEnd('/')
        val selectedModel = model.text.toString().trim()
        val path = endpointPath.text.toString().trim().let { if (it.startsWith("/")) it else "/$it" }
        val parsed = try { URI(url) } catch (_: Exception) { null }
        if (parsed == null || parsed.scheme !in listOf("https", "http") || parsed.host.isNullOrBlank()) { baseUrl.error = "Informe uma URL válida"; return false }
        if (selectedModel.isBlank()) { model.error = "Informe o ID do modelo"; return false }
        if (key.isBlank()) { apiKey.error = "Informe a chave de API"; return false }
        val headerName = authHeaderName.text.toString().trim()
        val prefix = authPrefix.text.toString().trim()
        if (!headerName.matches(Regex("[A-Za-z0-9-]+")) || prefix.contains("\\n") || prefix.contains("\\r")) { authHeaderName.error = "Cabeçalho ou prefixo inválido"; return false }
        val extra = try { if (extraHeaders.text.toString().isBlank()) JSONObject() else JSONObject(extraHeaders.text.toString()) } catch (_: Exception) { extraHeaders.error = "Informe um objeto JSON válido"; return false }
        if (extra.keys().asSequence().any { !it.matches(Regex("[A-Za-z0-9-]+")) }) { extraHeaders.error = "Nome de cabeçalho inválido"; return false }
        if (!path.startsWith("/") || path.contains("..")) { endpointPath.error = "Caminho inválido"; return false }
        val tokens = maxTokens.text.toString().toIntOrNull()?.coerceIn(64, 16000) ?: run { maxTokens.error = "Use um número entre 64 e 16000"; return false }
        val turns = contextTurns.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: run { contextTurns.error = "Use de 1 a 20 turnos"; return false }
        val seconds = timeout.text.toString().toIntOrNull()?.coerceIn(10, 180) ?: run { timeout.error = "Use de 10 a 180 segundos"; return false }
        val dimensions = listOf(768, 1024, 1280, 1600)
        prefs.edit()
            .putString("api_key", key).putString("api_base_url", url).putString("api_model", selectedModel)
            .putString("endpoint_path", path).putString("system_prompt", systemPrompt.text.toString().trim())
            .putString("auth_header_name", headerName).putString("auth_prefix", prefix).putString("extra_headers", extra.toString())
            .putBoolean("json_response", responseFormat.selectedItemPosition == 1)
            .putFloat("temperature", temperature.progress / 10f).putInt("max_tokens", tokens)
            .putInt("context_turns", turns).putInt("image_max_dimension", dimensions[imageSize.selectedItemPosition])
            .putInt("image_quality", imageQuality.progress + 40).putInt("timeout_seconds", seconds).apply()
        return true
    }

    private fun testConnection() {
        if (!saveSettings()) return
        testButton.isEnabled = false
        testButton.text = "Testando..."
        val url = prefs.getString("api_base_url", "") + prefs.getString("endpoint_path", "/chat/completions")
        val key = prefs.getString("api_key", "") ?: ""
        val selectedModel = prefs.getString("api_model", "") ?: ""
        thread {
            var connection: HttpURLConnection? = null
            val result = try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                val authName = prefs.getString("auth_header_name", "Authorization") ?: "Authorization"
                val authPrefixValue = prefs.getString("auth_prefix", "Bearer") ?: "Bearer"
                connection.setRequestProperty(authName, listOf(authPrefixValue, key).filter { it.isNotBlank() }.joinToString(" "))
                val extra = JSONObject(prefs.getString("extra_headers", "{}") ?: "{}")
                val iterator = extra.keys()
                while (iterator.hasNext()) { val header = iterator.next(); connection.setRequestProperty(header, extra.get(header).toString()) }
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.doOutput = true
                val body = JSONObject().put("model", selectedModel)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Responda apenas: CLC conectado.")))
                    .put("max_tokens", 20)
                if (prefs.getBoolean("json_response", false)) body.put("response_format", JSONObject().put("type", "json_object"))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                if (status in 200..299) "Conexão funcionando! Modelo: $selectedModel"
                else "A API respondeu HTTP $status: " + (connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "sem detalhes")
            } catch (e: Exception) { "Falha na conexão: ${e.message}" }
            finally { connection?.disconnect() }
            runOnUiThread {
                testButton.isEnabled = true
                testButton.text = "Testar conexão com a IA"
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun activate() {
        if (!saveSettings()) return
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Permita a sobreposição e volte para ativar.", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("legacy result required for MediaProjection consent")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenAssistService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("projectionData", data)
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "CLC ativo. Toque na bolha para capturar.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val DEFAULT_PROMPT = "Você é o CLC, um assistente visual útil. Responda em português do Brasil, com clareza e precisão. Não invente detalhes que não estejam visíveis. Quando necessário, explique passo a passo."
    }
}
