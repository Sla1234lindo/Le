package com.screenai.assistant
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.net.URI

class MainActivity : Activity() {
    private val captureRequest = 701
    private lateinit var apiKey: EditText
    private lateinit var baseUrl: EditText
    private lateinit var model: EditText
    private val prefs by lazy { getPreferences(0) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = resources.getColor(id, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(color(R.color.bg_app)); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(28), dp(22), dp(28)) }

        val hero = card().apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(22), dp(20), dp(22)) }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle); setBackgroundResource(R.drawable.bg_bubble)
            scaleType = ImageView.ScaleType.CENTER; elevation = dp(5).toFloat()
        }, LinearLayout.LayoutParams(dp(62), dp(62)).apply { bottomMargin = dp(12) })
        hero.addView(TextView(this).apply { text = "CLC"; textSize = 30f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color(R.color.text_primary)) })
        hero.addView(TextView(this).apply {
            text = "Seu assistente inteligente para entender qualquer tela"; textSize = 14f
            gravity = Gravity.CENTER; setTextColor(color(R.color.text_secondary)); setPadding(dp(8), dp(6), dp(8), 0)
        })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val settings = card()
        settings.addView(title("Configuração da IA"))
        settings.addView(label("URL base do provedor"))
        baseUrl = field("https://api.openai.com/v1", prefs.getString("api_base_url", "https://api.openai.com/v1") ?: "")
        settings.addView(baseUrl, fieldParams())
        settings.addView(note("Compatível com APIs no formato OpenAI, incluindo provedores externos que ofereçam Chat Completions e visão."))
        settings.addView(label("Modelo"))
        model = field("gpt-4o-mini", prefs.getString("api_model", "gpt-4o-mini") ?: "")
        settings.addView(model, fieldParams())
        settings.addView(label("Chave da API"))
        apiKey = field("Cole sua chave de API", prefs.getString("api_key", "") ?: "", true)
        settings.addView(apiKey, fieldParams())
        settings.addView(note("A chave fica salva localmente no aparelho. Nunca publique chaves no GitHub."))
        root.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val info = card()
        info.addView(title("O que o CLC faz"))
        info.addView(note("• Captura a tela quando você toca na bolha.\n• Responde perguntas sobre o que aparece.\n• Permite perguntas de acompanhamento sobre a mesma imagem.\n• Comprime a captura para reduzir o uso de dados."))
        root.addView(info, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        root.addView(button("Salvar e ativar assistente", true) { activate() }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        root.addView(button("Parar assistente", false) {
            stopService(Intent(this, ScreenAssistService::class.java))
            Toast.makeText(this, "Assistente parado", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, dp(52)))
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18))
        setBackgroundResource(R.drawable.bg_card); elevation = dp(2).toFloat()
    }
    private fun title(v: String) = TextView(this).apply {
        text = v; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.text_primary)); setPadding(0, 0, 0, dp(10))
    }
    private fun label(v: String) = TextView(this).apply {
        text = v; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.text_primary)); setPadding(0, dp(10), 0, dp(6))
    }
    private fun note(v: String) = TextView(this).apply {
        text = v; textSize = 12f; setTextColor(color(R.color.text_secondary))
        setPadding(0, dp(7), 0, dp(4)); setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun field(h: String, v: String, secret: Boolean = false) = EditText(this).apply {
        hint = h; setHintTextColor(color(R.color.text_hint)); setTextColor(color(R.color.text_primary))
        textSize = 14f; singleLine = true
        inputType = if (secret) 129 else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        setBackgroundResource(R.drawable.bg_input); setPadding(dp(13), dp(12), dp(13), dp(12)); setText(v)
    }
    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) }
    private fun button(v: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = v; isAllCaps = false; textSize = 15f; stateListAnimator = null
        typeface = if (primary) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setTextColor(color(if (primary) R.color.text_on_primary else R.color.primary))
        background = getDrawable(if (primary) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        setOnClickListener { action() }
    }

    private fun saveSettings(): Boolean {
        val k = apiKey.text.toString().trim()
        val url = baseUrl.text.toString().trim().trimEnd('/')
        val selectedModel = model.text.toString().trim()
        if (k.isBlank()) { apiKey.error = "Informe sua chave de API"; return false }
        val parsed = try { URI(url) } catch (_: Exception) { null }
        if (parsed == null || parsed.scheme !in listOf("https", "http") || parsed.host.isNullOrBlank()) {
            baseUrl.error = "Informe uma URL válida"; return false
        }
        if (selectedModel.isBlank()) { model.error = "Informe o modelo"; return false }
        prefs.edit().putString("api_key", k).putString("api_base_url", url).putString("api_model", selectedModel).apply()
        return true
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
            Toast.makeText(this, "CLC ativo. Toque na bolha para começar.", Toast.LENGTH_LONG).show()
        }
    }
}
