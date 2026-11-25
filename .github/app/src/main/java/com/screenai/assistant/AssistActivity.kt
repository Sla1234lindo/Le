package com.screenai.assistant

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        apiKey=getSharedPreferences("ScreenAI",0).getString("api_key","") ?: ""
        // MainActivity stores in its private preference file; Android's default name is MainActivity.
        apiKey=getSharedPreferences("MainActivity",0).getString("api_key", apiKey) ?: apiKey
        val path=intent.getStringExtra("imagePath") ?: ""
        val bitmap=BitmapFactory.decodeFile(path)
        val out=ByteArrayOutputStream(); bitmap?.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,out); bitmap?.recycle()
        imageData=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,20,18,18) }
        root.addView(TextView(this).apply { text="Pergunte sobre sua tela"; textSize=23f; setTypeface(null,1) })
        root.addView(TextView(this).apply { text="A imagem é enviada à API somente quando você tocar em Analisar."; textSize=13f; setPadding(0,8,0,10) })
        val preview=ImageView(this).apply { setImageBitmap(BitmapFactory.decodeFile(path)); scaleType=ImageView.ScaleType.FIT_CENTER }
        root.addView(preview,LinearLayout.LayoutParams(-1,0,1f))
        question=EditText(this).apply { hint="Ex.: O que está acontecendo?"; minLines=2 }
        root.addView(question,ViewGroup.LayoutParams(-1,-2))
        val btn=Button(this).apply { text="Analisar tela"; setOnClickListener { ask() } }; root.addView(btn,ViewGroup.LayoutParams(-1,-2))
        answer=TextView(this).apply { textSize=16f; setPadding(0,12,0,0) }; root.addView(answer,ViewGroup.LayoutParams(-1,-2))
        setContentView(root)
    }
    private fun ask() { val q=question.text.toString().trim().ifBlank { "Explique o que está acontecendo nesta tela e o que posso fazer." }; answer.text="Analisando..." 
        thread { try {
            val conn=URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod="POST"; conn.setRequestProperty("Authorization","Bearer $apiKey"); conn.setRequestProperty("Content-Type","application/json"); conn.doOutput=true; conn.connectTimeout=30000; conn.readTimeout=60000
            val content=JSONArray().put(JSONObject().put("type","text").put("text",q)).put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,$imageData")))
            val body=JSONObject().put("model","gpt-4o-mini").put("messages",JSONArray().put(JSONObject().put("role","user").put("content",content))).put("max_tokens",700)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream=if(conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response=stream.bufferedReader().readText(); val json=JSONObject(response)
            val result=if(conn.responseCode in 200..299) json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") else "Erro da API: "+json.optJSONObject("error")?.optString("message",response)
            runOnUiThread { answer.text=result }
            conn.disconnect()
        } catch(e:Exception) { runOnUiThread { answer.text="Não consegui conectar: ${e.message}" } } }
    }
}
