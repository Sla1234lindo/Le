package com.potato.ai

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val captureRequest = 4102
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 28, 24, 24) }
        fun label(s: String) { box.addView(TextView(this).apply { text=s; textSize=14f; setPadding(0,10,0,3) }) }
        fun field(hintText: String, value: String = "", secret: Boolean = false) = EditText(this).apply {
            hint=hintText; setText(value); textSize=14f
            if(secret) inputType=0x81
            box.addView(this, ViewGroup.LayoutParams(-1,-2))
        }
        box.addView(TextView(this).apply { text="Potato AI V21"; textSize=25f })
        box.addView(TextView(this).apply { text="Assistente flutuante para dicas, visão da tela sob autorização e integrações de IA/MCP."; textSize=14f })
        label("Provedor principal")
        val provider = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("OpenAI", "Claude", "Gemini", "OpenAI-compatible")) }
        box.addView(provider)
        val endpoint = field("Endpoint personalizado (opcional)")
        val model = field("Modelo", "gpt-4o-mini")
        val key = field("Chave da API (armazenada localmente)", secret=true)
        label("MCP — servidor HTTP Streamable ou endpoint JSON-RPC")
        val mcpUrl = field("URL HTTPS do servidor MCP")
        val mcpKey = field("Token MCP (opcional)", secret=true)
        box.addView(Button(this).apply { text="Salvar configurações"; setOnClickListener {
            getSharedPreferences("potato_ai", MODE_PRIVATE).edit()
                .putString("provider", provider.selectedItem.toString()).putString("endpoint", endpoint.text.toString().trim())
                .putString("model", model.text.toString().trim()).putString("key", key.text.toString())
                .putString("mcp_url", mcpUrl.text.toString().trim()).putString("mcp_key", mcpKey.text.toString()).apply()
            Toast.makeText(this@MainActivity,"Configurações salvas neste aparelho",Toast.LENGTH_SHORT).show()
        } })
        box.addView(Button(this).apply { text="Ativar bolha flutuante"; setOnClickListener {
            if(!Settings.canDrawOverlays(this@MainActivity)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else startService(Intent(this@MainActivity,FloatingAssistantService::class.java))
        } })
        box.addView(Button(this).apply { text="Analisar tela atual do Roblox"; setOnClickListener {
            if(!Settings.canDrawOverlays(this@MainActivity)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))); return@setOnClickListener }
            val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(),captureRequest)
        } })
        box.addView(Button(this).apply { text="Parar assistente"; setOnClickListener { stopService(Intent(this@MainActivity,FloatingAssistantService::class.java)) } })
        setContentView(ScrollView(this).apply { addView(box) })
        val p=getSharedPreferences("potato_ai",MODE_PRIVATE)
        val saved=p.getString("provider","OpenAI") ?: "OpenAI"; provider.setSelection((0 until provider.count).firstOrNull { provider.getItemAtPosition(it)==saved } ?: 0)
        endpoint.setText(p.getString("endpoint","")); model.setText(p.getString("model","gpt-4o-mini")); key.setText(p.getString("key","")); mcpUrl.setText(p.getString("mcp_url","")); mcpKey.setText(p.getString("mcp_key",""))
    }
    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==captureRequest && resultCode==RESULT_OK && data!=null) {
            val i=Intent(this,FloatingAssistantService::class.java).putExtra("capture_result",resultCode).putExtra("capture_data",data)
            if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i)
            Toast.makeText(this,"Captura autorizada. A bolha vai analisar um quadro da tela.",Toast.LENGTH_LONG).show()
        }
    }
}