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

class MainActivity : Activity() {
    private val captureRequest = 701
    private lateinit var key: EditText

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(resColor(R.color.bg_app))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Logo bubble
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sparkle)
            setBackgroundResource(R.drawable.bg_bubble)
            scaleType = ImageView.ScaleType.CENTER
            elevation = dp(6).toFloat()
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(18) })

        root.addView(TextView(this).apply {
            text = "Potato AI"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resColor(R.color.text_primary))
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "Seu assistente que explica o que aparece na sua tela"
            textSize = 14f
            setTextColor(resColor(R.color.text_secondary))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(28))
        })

        // Card with API key field
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(2).toFloat()
        }
        card.addView(TextView(this).apply {
            text = "Chave da API OpenAI"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resColor(R.color.text_primary))
            setPadding(0, 0, 0, dp(8))
        })
        key = EditText(this).apply {
            hint = "sk-..."
            setHintTextColor(resColor(R.color.text_hint))
            setTextColor(resColor(R.color.text_primary))
            inputType = 129
            textSize = 15f
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(getPreferences(0).getString("api_key", ""))
        }
        card.addView(key, LinearLayout.LayoutParams(-1, -2))
        card.addView(TextView(this).apply {
            text = "Fica salva só nas preferências locais deste app. A imagem capturada é enviada à API apenas quando você faz uma pergunta."
            textSize = 12f
            setTextColor(resColor(R.color.text_secondary))
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })

        // Primary button
        root.addView(Button(this).apply {
            text = "Ativar assistente flutuante"
            isAllCaps = false
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resColor(R.color.text_on_primary))
            background = getDrawable(R.drawable.bg_button_primary)
            stateListAnimator = null
            setOnClickListener { activate() }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })

        // Secondary (outline) button
        root.addView(Button(this).apply {
            text = "Parar assistente"
            isAllCaps = false
            textSize = 15f
            setTextColor(resColor(R.color.primary))
            background = getDrawable(R.drawable.bg_button_outline)
            stateListAnimator = null
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenAssistService::class.java))
                Toast.makeText(this@MainActivity, "Assistente parado", Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))

        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)
    }

    private fun resColor(id: Int) = resources.getColor(id, theme)

    private fun activate() {
        val k = key.text.toString().trim()
        if (!k.startsWith("sk-")) {
            Toast.makeText(this, "Insira uma chave de API válida", Toast.LENGTH_LONG).show()
            return
        }
        getPreferences(0).edit().putString("api_key", k).apply()
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Permita a sobreposição e volte ao app para ativar.", Toast.LENGTH_LONG).show()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }

    @Deprecated("legacy result required")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, ScreenAssistService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }
            startForegroundService(svc)
            Toast.makeText(this, "Assistente ativo. Toque na bolinha para analisar a tela.", Toast.LENGTH_LONG).show()
        }
    }
}
