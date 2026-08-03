package com.narutoai.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            webView = WebView(this)
            setContentView(webView)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }

            webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            webView.webChromeClient = WebChromeClient()
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
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erreur d'initialisation: ${e.message}", Toast.LENGTH_LONG).show()
        }
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

        @JavascriptInterface
        fun getAppVersion(): String = "2.0.0-NewJarvis"

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
                    // Filtrer pour obtenir les apps utilisateur principales
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
    }
}
