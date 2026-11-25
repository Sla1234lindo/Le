package com.potato.ai

import android.app.*
import android.content.*
import android.graphics.*
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class FloatingAssistantService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View?=null
    private var panel: View?=null
    private var latestFrame: String?=null

    override fun onBind(intent:Intent?)=null

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.hasExtra("capture_data")==true) {
            startProjectionForeground()
            val data=intent.getParcelableExtra<Intent>("capture_data")
            if(data!=null) captureOneFrame(intent.getIntExtra("capture_result",Activity.RESULT_OK),data)
        }
        if(bubble==null) showBubble()
        return START_STICKY
    }

    private fun startProjectionForeground() {
        val channel="potato_capture"
        if(Build.VERSION.SDK_INT>=26) {
            val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel,"Potato AI screen analysis",NotificationManager.IMPORTANCE_LOW))
        }
        val n=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,channel) else Notification.Builder(this)
        n.setContentTitle("Potato AI").setContentText("Analisando um quadro autorizado da tela").setSmallIcon(android.R.drawable.ic_menu_view)
        if(Build.VERSION.SDK_INT>=29) startForeground(7,n.build(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7,n.build())
    }

    private fun captureOneFrame(result:Int,data:Intent) {
        try {
            val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection=mgr.getMediaProjection(result,data)
            val metrics=resources.displayMetrics
            val reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,2)
            projection.registerCallback(object:MediaProjection.Callback(){
                override fun onStop(){ try { reader.close() } catch(_:Exception){} }
            },Handler(Looper.getMainLooper()))
            val vd=projection.createVirtualDisplay("PotatoAIFrame",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,0,reader.surface,null,null)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image=reader.acquireLatestImage()
                    if(image!=null) {
                        val plane=image.planes[0]
                        val buffer=plane.buffer
                        val pixelStride=plane.pixelStride
                        val rowStride=plane.rowStride
                        val rowPadding=rowStride-pixelStride*metrics.widthPixels
                        val wide=Bitmap.createBitmap(metrics.widthPixels+rowPadding/pixelStride,metrics.heightPixels,Bitmap.Config.ARGB_8888)
                        wide.copyPixelsFromBuffer(buffer)
                        val bmp=Bitmap.createBitmap(wide,0,0,metrics.widthPixels,metrics.heightPixels)
                        val out=ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG,65,out)
                        latestFrame=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
                        image.close()
                        bmp.recycle()
                        wide.recycle()
                    }
                } catch(_:Exception) {
                } finally {
                    vd.release()
                    reader.close()
                    projection.stop()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    panel?.let { panelView ->
                        panelView.post {
                            (panelView as? ViewGroup)?.let { group ->
                                for(i in 0 until group.childCount) {
                                    val child=group.getChildAt(i)
                                    if(child is Button && child.text=="Analisar último quadro") child.isEnabled=latestFrame!=null
                                }
                            }
                        }
                    }
                }
            },900)
        } catch(e:Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun winParams()=WindowManager.LayoutParams(
        -2,-2,
        if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity=Gravity.TOP or Gravity.END; x=18; y=260 }

    private fun showBubble() {
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        val b=TextView(this).apply {
            text="✳"; textSize=27f; gravity=Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(16,105,82)); setPadding(15,8,15,8)
        }
        bubble=b
        val p=winParams()
        b.setOnTouchListener(object:View.OnTouchListener {
            var sx=0f; var sy=0f; var ox=0; var oy=0
            override fun onTouch(v:View,e:MotionEvent):Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=p.x;oy=p.y;return true}
                    MotionEvent.ACTION_MOVE->{p.x=(ox+sx-e.rawX).toInt();p.y=(oy+e.rawY-sy).toInt();wm.updateViewLayout(b,p);return true}
                    MotionEvent.ACTION_UP->{if(kotlin.math.abs(e.rawX-sx)<8&&kotlin.math.abs(e.rawY-sy)<8) showPanel();return true}
                }
                return true
            }
        })
        wm.addView(b,p)
    }

    private fun showPanel() {
        if(panel!=null) { panel?.visibility=View.VISIBLE; return }
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,14,16,12); setBackgroundColor(Color.rgb(25,29,35)) }
        val title=TextView(this).apply { text="Potato AI V21 • Assistente";textSize=18f;setTextColor(Color.WHITE) }
        val output=TextView(this).apply { text="Pronto. Posso conversar, analisar um quadro autorizado da tela e consultar ferramentas MCP.";setTextColor(Color.WHITE);setPadding(0,10,0,10) }
        val input=EditText(this).apply { hint="Pergunte ou use /mcp para listar ferramentas";setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY) }
        val send=Button(this).apply { text="Enviar" }
        val analyze=Button(this).apply { text="Analisar último quadro"; isEnabled=latestFrame!=null }
        val close=Button(this).apply { text="Fechar";setOnClickListener { panel?.let { wm.removeView(it) };panel=null } }
        root.addView(title);root.addView(output);root.addView(input);root.addView(send);root.addView(analyze);root.addView(close)
        val pp=winParams().apply {
            width=(resources.displayMetrics.widthPixels*.88).toInt()
            height=-2
            flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            gravity=Gravity.CENTER
        }
        panel=root
        wm.addView(root,pp)
        fun runTask(q:String,vision:Boolean) {
            if(q.isBlank()&&!vision)return
            send.isEnabled=false
            analyze.isEnabled=false
            output.text="Consultando…"
            thread {
                val ans=if(q.startsWith("/mcp")) handleMcp(q) else askProvider(q,if(vision) latestFrame else null)
                Handler(Looper.getMainLooper()).post {
                    output.text=ans
                    send.isEnabled=true
                    analyze.isEnabled=latestFrame!=null
                }
            }
        }
        send.setOnClickListener { runTask(input.text.toString().trim(),false) }
        analyze.setOnClickListener { runTask("Analise a tela do jogo Roblox. Descreva apenas o que está visível e sugira dicas/configurações úteis.",true) }
    }

    private fun request(url:String,headers:Map<String,String>,body:JSONObject):JSONObject {
        val c=URL(url).openConnection() as HttpURLConnection
        c.requestMethod="POST"
        c.connectTimeout=15000
        c.readTimeout=45000
        c.doOutput=true
        c.setRequestProperty("Content-Type","application/json")
        c.setRequestProperty("Accept","application/json, text/event-stream")
        headers.forEach { (k,v)->c.setRequestProperty(k,v) }
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code=c.responseCode
        val stream=if(code in 200..299)c.inputStream else c.errorStream
        val raw=stream.bufferedReader().use{it.readText()}
        c.disconnect()
        if(code !in 200..299) throw Exception("HTTP $code: ${raw.take(500)}")
        return JSONObject(raw)
    }

    private fun askProvider(question:String,image:String?):String {
        val p=getSharedPreferences("potato_ai",MODE_PRIVATE)
        val provider=p.getString("provider","OpenAI")?:"OpenAI"
        val model=p.getString("model","gpt-4o-mini")?:"gpt-4o-mini"
        val key=p.getString("key","")?:""
        val custom=p.getString("endpoint","")?:""
        if(key.isBlank()) return "Configure sua chave de API na tela principal."
        return try {
            when(provider) {
                "Claude" -> {
                    val content=JSONArray().put(JSONObject().put("type","text").put("text",question))
                    if(image!=null) content.put(JSONObject().put("type","image").put("source",JSONObject().put("type","base64").put("media_type","image/jpeg").put("data",image)))
                    val j=request(if(custom.isNotBlank())custom else "https://api.anthropic.com/v1/messages",mapOf("x-api-key" to key,"anthropic-version" to "2023-06-01"),JSONObject().put("model",model).put("max_tokens",1200).put("messages",JSONArray().put(JSONObject().put("role","user").put("content",content))))
                    j.optJSONArray("content")?.optJSONObject(0)?.optString("text")?:j.toString()
                }
                "Gemini" -> {
                    val parts=JSONArray().put(JSONObject().put("text",question))
                    if(image!=null)parts.put(JSONObject().put("inline_data",JSONObject().put("mime_type","image/jpeg").put("data",image)))
                    val u=if(custom.isNotBlank())custom else "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                    val j=request(u,if(custom.isNotBlank())mapOf("x-goog-api-key" to key) else emptyMap(),JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",parts))))
                    j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")?:j.toString()
                }
                else -> {
                    val content:Any=if(image==null) question else JSONArray()
                        .put(JSONObject().put("type","text").put("text",question))
                        .put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,$image")))
                    val endpoint=if(custom.isNotBlank())custom else "https://api.openai.com/v1/chat/completions"
                    val j=request(endpoint,mapOf("Authorization" to "Bearer $key"),JSONObject().put("model",model).put("messages",JSONArray()
                        .put(JSONObject().put("role","system").put("content","Você é o Potato AI, assistente de jogo. Analise apenas dados e imagens fornecidos; dê dicas e recomendações, sem controlar contas ou automatizar partidas."))
                        .put(JSONObject().put("role","user").put("content",content))))
                    j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?:j.toString()
                }
            }
        } catch(e:Exception) { "Erro na IA: ${e.message}" }
    }

    private fun mcpCall(method:String,params:JSONObject):JSONObject {
        val p=getSharedPreferences("potato_ai",MODE_PRIVATE)
        val u=p.getString("mcp_url","")?:""
        val token=p.getString("mcp_key","")?:""
        if(u.isBlank()) throw Exception("Configure a URL do servidor MCP primeiro.")
        val headers=if(token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
        return request(u,headers,JSONObject().put("jsonrpc","2.0").put("id",System.currentTimeMillis()).put("method",method).put("params",params))
    }

    private fun handleMcp(q:String):String = try {
        val rest=q.removePrefix("/mcp").trim()
        if(rest.isBlank()||rest=="list") {
            val r=mcpCall("tools/list",JSONObject())
            val tools=r.optJSONObject("result")?.optJSONArray("tools")?:JSONArray()
            if(tools.length()==0) "Servidor respondeu, mas não anunciou ferramentas: ${r}"
            else buildString {
                append("Ferramentas MCP disponíveis:\n")
                for(i in 0 until tools.length()) {
                    val t=tools.getJSONObject(i)
                    append("• ${t.optString("name")}: ${t.optString("description")}\n")
                }
                append("Para executar: /mcp nome_da_ferramenta {\"arg\":\"valor\"}")
            }
        } else {
            val name=rest.substringBefore(" ")
            val raw=rest.substringAfter(" ","").trim()
            val args=if(raw.isBlank())JSONObject() else JSONObject(raw)
            val r=mcpCall("tools/call",JSONObject().put("name",name).put("arguments",args))
            r.optJSONObject("result")?.toString(2)?:r.toString(2)
        }
    } catch(e:Exception) { "Falha MCP: ${e.message}" }

    override fun onDestroy() {
        try { panel?.let{wm.removeView(it)};bubble?.let{wm.removeView(it)} } catch(_:Exception){}
        panel=null
        bubble=null
        super.onDestroy()
    }
}