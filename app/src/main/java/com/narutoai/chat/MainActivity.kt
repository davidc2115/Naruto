package com.narutoai.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("NewJarvisPrefs", Context.MODE_PRIVATE)

        // Demande de permission RECORD_AUDIO au démarrage
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        }

        // Initialisation de la synthèse vocale natif Android (Haut-parleurs)
        try {
            tts = TextToSpeech(this, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

            // Accord automatique des permissions web (Microphone) au WebView
            webView.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    runOnUiThread {
                        try {
                            request?.grant(request.resources)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        if (!url.contains("localhost") && !url.contains("file://")) {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                return true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
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
            try {
                tts?.language = Locale.FRENCH
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun initNativeSpeechRecognizer() {
        runOnUiThread {
            try {
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
                                webView.postDelayed({ startNativeListeningLoop() }, 500)
                            }
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                webView.post {
                                    val safeText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
                                    webView.evaluateJavascript("if(window.onNativeSpeechResult) window.onNativeSpeechResult('$safeText')", null)
                                }
                            }
                            if (isPermanentListening) {
                                webView.postDelayed({ startNativeListeningLoop() }, 400)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        try {
            tts?.stop()
            tts?.shutdown()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Synthèse Vocale Native (Haut-parleurs du Téléphone)
        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                try {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NewJarvisTTS")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Reconnaissance Vocale Native Android (Écoute & Mot Clé)
        @JavascriptInterface
        fun startNativeListening(permanent: Boolean) {
            isPermanentListening = permanent
            startNativeListeningLoop()
        }

        @JavascriptInterface
        fun stopNativeListening() {
            isPermanentListening = false
            runOnUiThread {
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Sauvegarde de clé permanente dans SharedPreferences du téléphone
        @JavascriptInterface
        fun savePref(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun getPref(key: String, defaultValue: String): String {
            return prefs.getString(key, defaultValue) ?: defaultValue
        }

        @JavascriptInterface
        fun getAppVersion(): String = "3.0.0-NewJarvis"

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Impossible d'ouvrir: $url", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun getInstalledApps(): String {
            val jsonArray = JSONArray()
            try {
                val pm = packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                for (appInfo in packages) {
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {

                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val pkgName = appInfo.packageName
                        val pInfo = try { pm.getPackageInfo(pkgName, 0) } catch (e: Exception) { null }
                        val versionName = pInfo?.versionName ?: "1.0"
                        val sourceDir = appInfo.sourceDir

                        val obj = JSONObject().apply {
                            put("name", appName)
                            put("packageName", pkgName)
                            put("versionName", versionName)
                            put("sourceDir", sourceDir)
                        }
                        jsonArray.put(obj)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return jsonArray.toString()
        }

        // Pont Native HTTP (Sans CORS)
        @JavascriptInterface
        fun nativeFetch(urlStr: String, method: String, headersJsonStr: String, bodyStr: String): String {
            val resultJson = JSONObject()
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = if (method.isEmpty()) "GET" else method.uppercase()
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doInput = true

                if (headersJsonStr.isNotEmpty() && headersJsonStr != "{}") {
                    val headers = JSONObject(headersJsonStr)
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        conn.setRequestProperty(key, headers.getString(key))
                    }
                }

                if ((conn.requestMethod == "POST" || conn.requestMethod == "PUT") && bodyStr.isNotEmpty()) {
                    conn.doOutput = true
                    val os = conn.outputStream
                    val writer = OutputStreamWriter(os, "UTF-8")
                    writer.write(bodyStr)
                    writer.flush()
                    writer.close()
                }

                val responseCode = conn.responseCode
                resultJson.put("status", responseCode)

                val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val sb = StringBuilder()
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    reader.close()
                }

                resultJson.put("ok", responseCode in 200..299)
                resultJson.put("data", sb.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                resultJson.put("ok", false)
                resultJson.put("status", 500)
                resultJson.put("error", e.message ?: "Erreur réseau")
            }
            return resultJson.toString()
        }
    }
}
