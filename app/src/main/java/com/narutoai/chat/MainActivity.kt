package com.narutoai.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isPermanentListening = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Demande explicite de permission enregistrement audio au démarrage
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        // Initialisation de la synthèse vocale natif Android (Haut-parleur)
        tts = TextToSpeech(this, this)

        try {
            webView = WebView(this)
            setContentView(webView)

            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            // WebChromeClient pour accorder automatiquement la permission micro au WebView
            webView.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    runOnUiThread {
                        request?.grant(request.resources)
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                    return false
                }
            }

            webView.loadUrl("file:///android_asset/www/index.html")
            initNativeSpeechRecognizer()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur d'initialisation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.FRENCH
        }
    }

    private fun initNativeSpeechRecognizer() {
        runOnUiThread {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (isPermanentListening) {
                            startNativeListeningLoop()
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            webView.post {
                                webView.evaluateJavascript("onNativeSpeechResult('${text.replace("'", "\\'")}')", null)
                            }
                        }
                        if (isPermanentListening) {
                            startNativeListeningLoop()
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startNativeListeningLoop() {
        runOnUiThread {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        // TTS Natif Android (Son puissant dans les haut-parleurs)
        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UtteranceId")
            }
        }

        // Écoute Vocale Native Android
        @JavascriptInterface
        fun startNativeListening(permanent: Boolean) {
            isPermanentListening = permanent
            startNativeListeningLoop()
        }

        @JavascriptInterface
        fun stopNativeListening() {
            isPermanentListening = false
            runOnUiThread {
                try { speechRecognizer?.stopListening() } catch(e){}
            }
        }

        @JavascriptInterface
        fun getAppVersion(): String = "2.2.0-NewJarvis"

        @JavascriptInterface
        fun openUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            val jsonArray = JSONArray()
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            for (appInfo in packages) {
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    val appJson = JSONObject()
                    appJson.put("name", pm.getApplicationLabel(appInfo).toString())
                    appJson.put("packageName", appInfo.packageName)
                    jsonArray.put(appJson)
                }
            }
            return jsonArray.toString()
        }

        // Pont Native HTTP pour contourner 100% des erreurs CORS sur Groq, OpenAI, Gemini, Anthropic
        @JavascriptInterface
        fun nativeFetch(urlStr: String, method: String, headersJsonStr: String, bodyStr: String): String {
            val resultJson = JSONObject()
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = method.uppercase(Locale.ROOT)
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (headersJsonStr.isNotBlank() && headersJsonStr != "{}") {
                    val headersObj = JSONObject(headersJsonStr)
                    val keys = headersObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        conn.setRequestProperty(key, headersObj.getString(key))
                    }
                }

                if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) {
                    conn.doOutput = true
                    val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
                    writer.write(bodyStr)
                    writer.flush()
                    writer.close()
                }

                val responseCode = conn.responseCode
                resultJson.put("status", responseCode)
                resultJson.put("ok", responseCode in 200..299)

                val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                    resultJson.put("data", sb.toString())
                } else {
                    resultJson.put("data", "")
                }
            } catch (e: Exception) {
                resultJson.put("status", 500)
                resultJson.put("ok", false)
                resultJson.put("data", JSONObject().put("error", JSONObject().put("message", e.message ?: "Network error")).toString())
            }
            return resultJson.toString()
        }
    }
}
