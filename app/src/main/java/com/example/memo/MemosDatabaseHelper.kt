package com.example.memo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Memos数据库助手类
 */
class MemosDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val TAG = "MemosDatabaseHelper"
        private const val DATABASE_NAME = "memos.db"
        private const val DATABASE_VERSION = 1
        
        // 表名
        const val TABLE_MEMOS = "memo"
        const val TABLE_USERS = "user"
        
        // 列名
        const val COLUMN_ID = "id"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_CREATED_TS = "created_ts"
        const val COLUMN_UPDATED_TS = "updated_ts"
        const val COLUMN_USERNAME = "username"
        const val COLUMN_NICKNAME = "nickname"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_ROLE = "role"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建用户表
        val createUserTable = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_USERNAME TEXT NOT NULL,
                $COLUMN_NICKNAME TEXT,
                $COLUMN_EMAIL TEXT,
                $COLUMN_ROLE TEXT NOT NULL,
                $COLUMN_CREATED_TS INTEGER NOT NULL,
                $COLUMN_UPDATED_TS INTEGER NOT NULL
            )
        """.trimIndent()
        
        // 创建备忘录表
        val createMemoTable = """
            CREATE TABLE $TABLE_MEMOS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CONTENT TEXT NOT NULL,
                $COLUMN_USER_ID INTEGER NOT NULL,
                $COLUMN_CREATED_TS INTEGER NOT NULL,
                $COLUMN_UPDATED_TS INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_USER_ID) REFERENCES $TABLE_USERS($COLUMN_ID)
            )
        """.trimIndent()
        
        db.execSQL(createUserTable)
        db.execSQL(createMemoTable)
        
        // 创建默认用户
        val defaultUser = ContentValues().apply {
            put(COLUMN_USERNAME, "user")
            put(COLUMN_NICKNAME, "本地用户")
            put(COLUMN_EMAIL, "")
            put(COLUMN_ROLE, "HOST")
            put(COLUMN_CREATED_TS, System.currentTimeMillis() / 1000)
            put(COLUMN_UPDATED_TS, System.currentTimeMillis() / 1000)
        }
        
        val userId = db.insert(TABLE_USERS, null, defaultUser)
        
        // 创建默认备忘录
        if (userId != -1L) {
            val defaultMemo = ContentValues().apply {
                put(COLUMN_CONTENT, "欢迎使用Memos！这是一个本地笔记应用。")
                put(COLUMN_USER_ID, userId)
                put(COLUMN_CREATED_TS, System.currentTimeMillis() / 1000)
                put(COLUMN_UPDATED_TS, System.currentTimeMillis() / 1000)
            }
            db.insert(TABLE_MEMOS, null, defaultMemo)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }
    
    /**
     * 获取所有备忘录
     */
    fun getMemos(): List<JSONObject> {
        val memoList = mutableListOf<JSONObject>()
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_MEMOS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_TS DESC"
        )
        
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
            val userId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_USER_ID))
            val createdTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_TS))
            val updatedTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_TS))
            
            val memo = JSONObject()
            memo.put("id", id)
            memo.put("content", content)
            memo.put("userId", userId)
            memo.put("createdTs", createdTs)
            memo.put("updatedTs", updatedTs)
            
            memoList.add(memo)
        }
        
        cursor.close()
        return memoList
    }
    
    /**
     * 创建新备忘录
     */
    fun createMemo(content: String): JSONObject? {
        val db = writableDatabase
        val timestamp = System.currentTimeMillis() / 1000
        
        val values = ContentValues()
        values.put(COLUMN_CONTENT, content)
        values.put(COLUMN_USER_ID, 1) // 默认用户
        values.put(COLUMN_CREATED_TS, timestamp)
        values.put(COLUMN_UPDATED_TS, timestamp)
        
        val id = db.insert(TABLE_MEMOS, null, values)
        
        return if (id != -1L) {
            val memo = JSONObject()
            memo.put("id", id)
            memo.put("content", content)
            memo.put("userId", 1)
            memo.put("createdTs", timestamp)
            memo.put("updatedTs", timestamp)
            memo
        } else {
            null
        }
    }
    
    /**
     * 更新备忘录
     */
    fun updateMemo(memoId: Long, content: String): JSONObject? {
        val db = writableDatabase
        val timestamp = System.currentTimeMillis() / 1000
        
        val values = ContentValues()
        values.put(COLUMN_CONTENT, content)
        values.put(COLUMN_UPDATED_TS, timestamp)
        
        val rowsAffected = db.update(
            TABLE_MEMOS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(memoId.toString())
        )
        
        return if (rowsAffected > 0) {
            // 获取更新后的备忘录
            val cursor = db.query(
                TABLE_MEMOS,
                null,
                "$COLUMN_ID = ?",
                arrayOf(memoId.toString()),
                null,
                null,
                null
            )
            
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val updatedContent = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT))
                val userId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_USER_ID))
                val createdTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_TS))
                val updatedTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_TS))
                
                val memo = JSONObject()
                memo.put("id", id)
                memo.put("content", updatedContent)
                memo.put("userId", userId)
                memo.put("createdTs", createdTs)
                memo.put("updatedTs", updatedTs)
                
                cursor.close()
                memo
            } else {
                cursor.close()
                null
            }
        } else {
            null
        }
    }
    
    /**
     * 删除备忘录
     */
    fun deleteMemo(memoId: Long): Boolean {
        val db = writableDatabase
        val deletedRows = db.delete(
            TABLE_MEMOS,
            "$COLUMN_ID = ?",
            arrayOf(memoId.toString())
        )
        
        return deletedRows > 0
    }
    
    /**
     * 获取用户信息
     */
    fun getUser(userId: Long): JSONObject? {
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        )
        
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val username = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME))
            val nickname = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NICKNAME))
            val email = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL))
            val role = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ROLE))
            val createdTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_TS))
            val updatedTs = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_TS))
            
            val user = JSONObject()
            user.put("id", id)
            user.put("username", username)
            user.put("nickname", nickname)
            user.put("email", email)
            user.put("role", role)
            user.put("createdTs", createdTs)
            user.put("updatedTs", updatedTs)
            
            cursor.close()
            return user
        }
        
        cursor.close()
        return null
    }
} 