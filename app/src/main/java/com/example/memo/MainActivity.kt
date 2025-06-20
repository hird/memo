package com.example.memo

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var memosServer: MemosServer
    private val PORT = 8081
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 启动本地服务器
        startMemosServer()
        
        // 配置WebView
        val webView = findViewById<android.webkit.WebView>(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
        }
        
        // 添加JavaScript接口
        webView.addJavascriptInterface(WebAppInterface(), "Android")
        
        // 设置WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
                
                // 添加调试工具 - 在控制台上打印网络请求状态
                view?.evaluateJavascript("""
                    console.log('Memos Web App加载完成');
                    
                    // 重写fetch方法，添加更多日志
                    const originalFetch = window.fetch;
                    window.fetch = function(url, options = {}) {
                        console.log('发送请求:', url, options);
                        return originalFetch(url, options)
                            .then(response => {
                                console.log('收到响应:', url, response.status);
                                return response;
                            })
                            .catch(error => {
                                console.error('请求失败:', url, error);
                                throw error;
                            });
                    };
                """.trimIndent(), null)
            }
            
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                view?.loadUrl(url ?: "")
                return true
            }
        }
        
        // 设置WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d(TAG, "WebView Console: ${it.message()}")
                }
                return true
            }
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
        
        // 检查权限
        checkAndRequestPermissions()
        
        // 加载本地web应用
        webView.loadUrl("http://localhost:$PORT/")
    }
    
    override fun onBackPressed() {
        val webView = findViewById<android.webkit.WebView>(R.id.webview)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    private fun startMemosServer() {
        try {
            memosServer = MemosServer(this, PORT)
            memosServer.start()
            Log.d(TAG, "Memos server started on port $PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            Toast.makeText(this, "服务器启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        if (::memosServer.isInitialized) {
            memosServer.stop()
        }
        super.onDestroy()
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        
        val permissionsToRequest = ArrayList<String>()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                100
            )
        }
    }
    
    inner class WebAppInterface {
        @JavascriptInterface
        fun showToast(message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
        
        @JavascriptInterface
        fun isNetworkAvailable(): Boolean {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
        
        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "JS Log: $message")
        }
    }
} 