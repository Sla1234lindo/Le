package com.screenai.assistant

import android.app.Activity
import android.content.Intent
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 32, 28, 24); gravity = Gravity.CENTER_VERTICAL }
        root.addView(TextView(this).apply { text = "ScreenAI"; textSize = 30f; setTextColor(0xff20202a.toInt()); setTypeface(null, 1) })
        root.addView(TextView(this).apply { text = "Assistente que explica o que aparece na sua tela."; textSize = 16f; setPadding(0, 8, 0, 22) })
        root.addView(TextView(this).apply { text = "Chave da API OpenAI (necessária para respostas):"; textSize = 14f })
        key = EditText(this).apply { hint = "sk-..."; inputType = 129; setText(getPreferences(0).getString("api_key", "")) }
        root.addView(key, ViewGroup.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "A chave fica salva apenas nas preferências locais deste app. A imagem capturada é enviada à API quando você faz uma pergunta."; textSize = 13f; setPadding(0, 10, 0, 18) })
        root.addView(Button(this).apply { text = "Ativar assistente flutuante"; setOnClickListener { activate() } }, ViewGroup.LayoutParams(-1, -2))
        root.addView(Button(this).apply { text = "Parar assistente"; setOnClickListener { stopService(Intent(this@MainActivity, ScreenAssistService::class.java)); Toast.makeText(this@MainActivity, "Assistente parado", Toast.LENGTH_SHORT).show() } }, ViewGroup.LayoutParams(-1, -2))
        setContentView(root)
    }
    private fun activate() {
        val k = key.text.toString().trim()
        if (!k.startsWith("sk-")) { Toast.makeText(this, "Insira uma chave de API válida", Toast.LENGTH_LONG).show(); return }
        getPreferences(0).edit().putString("api_key", k).apply()
        if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this, "Permita a sobreposição e volte ao app para ativar.", Toast.LENGTH_LONG).show(); return }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
    }
    @Deprecated("legacy result required")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val svc = Intent(this, ScreenAssistService::class.java).apply { putExtra("resultCode", resultCode); putExtra("projectionData", data) }
            startForegroundService(svc)
            Toast.makeText(this, "Assistente ativo. Toque na bolinha para analisar a tela.", Toast.LENGTH_LONG).show()
        }
    }
}
