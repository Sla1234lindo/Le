package com.potato.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class DemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 28) }
        root.addView(TextView(this).apply { text = "Potato AI V21 — Integration Alpha"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Assistente flutuante, chat com IA, análise de um quadro autorizado da tela e cliente MCP básico. Para usar, conceda a permissão de sobreposição e configure sua API."; textSize = 15f; setPadding(0, 12, 0, 20) })
        root.addView(Button(this).apply { text = "Abrir Potato AI"; setOnClickListener { startActivity(Intent(this@DemoActivity, com.potato.ai.MainActivity::class.java)) } })
        setContentView(root)
    }
}