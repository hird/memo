package com.example.memo

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * 本地Memos服务器，用于处理API请求和提供静态资源
 */
class MemosServer(private val context: Context, private val port: Int = 8081) : NanoWSD(port) {
    
    private val TAG = "MemosServer"
    private val dataDir: File = File(context.filesDir, "memos_data")
    private val dbFile: File = File(dataDir, "memos.db")
    
    // API路径前缀
    private val API_PATH = "/api"
    
    // WebSocket连接Map
    private val webSocketConnections = mutableMapOf<String, WebSocket>()
    
    private val dbHelper = MemosDatabaseHelper(context)
    
    init {
        // 确保数据目录存在
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        
        // 初始化数据库
        initializeDatabase()
    }
    
    /**
     * 初始化SQLite数据库
     */
    private fun initializeDatabase() {
        try {
            // 如果数据库文件不存在，从资产中复制初始数据库
            if (!dbFile.exists()) {
                val inputStream = context.assets.open("memos/db/memos.db")
                dbFile.outputStream().use { fileOut ->
                    inputStream.copyTo(fileOut)
                }
                Log.d(TAG, "Database initialized from assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
        }
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Request: ${session.method} $uri")
        
        try {
            // 处理API请求
            if (uri.startsWith(API_PATH)) {
                return serveApiRequest(session)
            }
            
            // 处理静态资源
            return serveStaticFiles(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request", e)
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server Error: ${e.message}"
            )
        }
    }
    
    /**
     * 处理API请求
     */
    private fun serveApiRequest(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        
        // GET /api/memo - 获取备忘录列表
        if (uri == "$API_PATH/memo" && method == Method.GET) {
            val memos = dbHelper.getMemos()
            val jsonArray = JSONArray()
            
            for (memo in memos) {
                jsonArray.put(memo)
            }
            
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                jsonArray.toString()
            )
        }
        
        // POST /api/memo - 创建新备忘录
        if (uri == "$API_PATH/memo" && method == Method.POST) {
            val bodySize = session.headers.getOrDefault("content-length", "0").toInt()
            val buffer = ByteArray(bodySize)
            session.inputStream.read(buffer, 0, bodySize)
            val requestBody = String(buffer)
            
            try {
                val json = JSONObject(requestBody)
                val content = json.optString("content", "")
                
                if (content.isNotEmpty()) {
                    val newMemo = dbHelper.createMemo(content)
                    
                    if (newMemo != null) {
                        return newFixedLengthResponse(
                            Response.Status.OK,
                            "application/json",
                            newMemo.toString()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating memo", e)
            }
            
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Invalid request or content"
            )
        }
        
        // PATCH /api/memo/:id - 更新备忘录
        if (uri.matches("$API_PATH/memo/\\d+".toRegex()) && method == Method.PATCH) {
            val memoId = uri.substringAfterLast("/").toLongOrNull()
            
            if (memoId != null) {
                val bodySize = session.headers.getOrDefault("content-length", "0").toInt()
                val buffer = ByteArray(bodySize)
                session.inputStream.read(buffer, 0, bodySize)
                val requestBody = String(buffer)
                
                try {
                    val json = JSONObject(requestBody)
                    val content = json.optString("content", "")
                    
                    if (content.isNotEmpty()) {
                        val updatedMemo = dbHelper.updateMemo(memoId, content)
                        
                        if (updatedMemo != null) {
                            return newFixedLengthResponse(
                                Response.Status.OK,
                                "application/json",
                                updatedMemo.toString()
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating memo", e)
                }
            }
            
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Invalid request or memo not found"
            )
        }
        
        // DELETE /api/memo/:id - 删除备忘录
        if (uri.matches("$API_PATH/memo/\\d+".toRegex()) && method == Method.DELETE) {
            val memoId = uri.substringAfterLast("/").toLongOrNull()
            
            if (memoId != null) {
                val success = dbHelper.deleteMemo(memoId)
                
                if (success) {
                    val response = JSONObject()
                    response.put("success", true)
                    
                    return newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json",
                        response.toString()
                    )
                }
            }
            
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Invalid request or memo not found"
            )
        }
        
        // GET /api/user/me - 获取当前用户信息
        if (uri == "$API_PATH/user/me" && method == Method.GET) {
            val user = dbHelper.getUser(1)
            
            return if (user != null) {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    user.toString()
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "User not found"
                )
            }
        }
        
        // 处理其他API请求
        return newFixedLengthResponse(
            Response.Status.NOT_IMPLEMENTED,
            MIME_PLAINTEXT,
            "API endpoint not implemented"
        )
    }
    
    /**
     * 处理静态文件请求
     */
    private fun serveStaticFiles(session: IHTTPSession): Response {
        val uri = session.uri
        var filePath = uri
        
        // 处理根路径请求
        if (uri == "/" || uri.isEmpty()) {
            filePath = "/index.html"
        }
        
        // 尝试从assets目录加载文件
        try {
            val mimeType = getCustomMimeType(filePath)
            val assetPath = "memos/web${if (filePath.startsWith("/")) filePath else "/$filePath"}"
            
            Log.d(TAG, "Loading asset: $assetPath")
            val inputStream = context.assets.open(assetPath)
            
            return newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                inputStream,
                inputStream.available().toLong()
            )
        } catch (e: IOException) {
            Log.e(TAG, "File not found: $filePath", e)
            
            // 如果找不到文件，返回index.html（用于SPA路由）
            try {
                val inputStream = context.assets.open("memos/web/index.html")
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html",
                    inputStream,
                    inputStream.available().toLong()
                )
            } catch (e2: IOException) {
                Log.e(TAG, "Failed to serve index.html", e2)
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "File not found"
                )
            }
        }
    }
    
    /**
     * 获取MIME类型
     */
    private fun getCustomMimeType(uri: String): String {
        val extension = uri.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "html" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "webp" -> "image/webp"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * 处理WebSocket连接
     */
    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        val socket = MemosWebSocket(handshake)
        val clientId = UUID.randomUUID().toString()
        webSocketConnections[clientId] = socket
        return socket
    }
    
    /**
     * Memos WebSocket实现
     */
    inner class MemosWebSocket(handshakeRequest: IHTTPSession) : WebSocket(handshakeRequest) {
        override fun onOpen() {
            Log.d(TAG, "WebSocket opened")
        }

        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
            Log.d(TAG, "WebSocket closed: $reason")
        }

        override fun onMessage(message: WebSocketFrame) {
            Log.d(TAG, "WebSocket message: ${message.textPayload}")
        }

        override fun onPong(pong: WebSocketFrame) {
            Log.d(TAG, "WebSocket pong received")
        }

        override fun onException(exception: IOException) {
            Log.e(TAG, "WebSocket error", exception)
        }
    }
} 