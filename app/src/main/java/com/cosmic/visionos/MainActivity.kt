package com.cosmic.visionos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var backPressCount = 0
    private var lastBackPressTime = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(VisionNativeBridge(this), "VisionNative")

        loadVisionOS()
    }

    private fun loadVisionOS() {
        val otaFile = File(filesDir, "ota_os.html")
        if (otaFile.exists()) {
            val html = otaFile.readText(Charsets.UTF_8)
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
        } else {
            webView.loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Emergency Safe Mode: Agar TV remote ka Back button 2 second ke andar 5 baar dabaya jaye, toh OTA reset ho jayega!
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 600) {
                backPressCount++
            } else {
                backPressCount = 1
            }
            lastBackPressTime = now

            if (backPressCount >= 5) {
                val otaFile = File(filesDir, "ota_os.html")
                if (otaFile.exists()) otaFile.delete()
                Toast.makeText(this, "VisionOS Safe Mode: Restored Base OS!", Toast.LENGTH_LONG).show()
                webView.loadUrl("file:///android_asset/index.html")
                backPressCount = 0
                return true
            }

            webView.evaluateJavascript("if(window.onVisionBack) window.onVisionBack();", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    inner class VisionNativeBridge(private val context: Context) {

        @JavascriptInterface
        fun launchNativeApp(packageName: String): Boolean {
            return try {
                val pm = context.packageManager
                var intent = pm.getLeanbackLaunchIntentForPackage(packageName)
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(packageName)
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else {
                    runOnUiThread {
                        Toast.makeText(context, "App not installed: $packageName", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun getInstalledTvApps(): String {
            val arr = JSONArray()
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(0)
                for (app in packages) {
                    val launchIntent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                        ?: pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null && app.packageName != context.packageName) {
                        val obj = JSONObject()
                        obj.put("name", pm.getApplicationLabel(app).toString())
                        obj.put("package", app.packageName)
                        arr.put(obj)
                    }
                }
            } catch (e: Exception) {}
            return arr.toString()
        }

        @JavascriptInterface
        fun saveOtaSystem(htmlContent: String): Boolean {
            return try {
                val otaFile = File(context.filesDir, "ota_os.html")
                otaFile.writeText(htmlContent, Charsets.UTF_8)
                runOnUiThread {
                    Toast.makeText(context, "VisionOS OTA Updated!", Toast.LENGTH_SHORT).show()
                    loadVisionOS()
                }
                true
            } catch (e: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun resetOtaSystem() {
            val otaFile = File(context.filesDir, "ota_os.html")
            if (otaFile.exists()) otaFile.delete()
            runOnUiThread {
                Toast.makeText(context, "Reset to Factory VisionOS", Toast.LENGTH_SHORT).show()
                webView.loadUrl("file:///android_asset/index.html")
            }
        }

        @JavascriptInterface
        fun openSystemSettings() {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun showToast(msg: String) {
            runOnUiThread {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
