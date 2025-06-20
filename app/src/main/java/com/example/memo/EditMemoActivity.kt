package com.example.memo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// org.json.JSONObject is not strictly needed here if MemosDatabaseHelper is refactored
// to return a domain object or handle JSONObject internally for getMemoById.

class EditMemoActivity : AppCompatActivity() {

    private lateinit var editTextMemoContent: EditText
    private lateinit var buttonSaveMemo: Button
    private lateinit var buttonDeleteMemo: Button

    private lateinit var dbHelper: MemosDatabaseHelper
    private var currentMemoId: Long = -1L // Use -1L for Long
    private val TAG = "EditMemoActivity"

    companion object {
        const val EXTRA_MEMO_ID = "MEMO_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_memo)

        editTextMemoContent = findViewById(R.id.editTextMemoContent)
        buttonSaveMemo = findViewById(R.id.buttonSaveMemo)
        buttonDeleteMemo = findViewById(R.id.buttonDeleteMemo)

        dbHelper = MemosDatabaseHelper(this)

        currentMemoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)

        if (currentMemoId != -1L) {
            title = "Edit Memo" // Set activity title
            loadMemoContent()
            buttonDeleteMemo.visibility = View.VISIBLE
        } else {
            title = "Create Memo" // Set activity title
            buttonDeleteMemo.visibility = View.GONE
        }

        buttonSaveMemo.setOnClickListener { saveMemo() }
        buttonDeleteMemo.setOnClickListener { deleteMemo() }
    }

    private fun loadMemoContent() {
        val memoObject = dbHelper.getMemoById(currentMemoId) // Use new method

        if (memoObject != null) {
            editTextMemoContent.setText(memoObject.optString("content"))
        } else {
            Log.e(TAG, "Memo with ID $currentMemoId not found.")
            Toast.makeText(this, "Error loading memo", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveMemo() {
        val content = editTextMemoContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        var success = false
        if (currentMemoId == -1L) { // New memo
            val newMemo = dbHelper.createMemo(content)
            if (newMemo != null) {
                Toast.makeText(this, "Memo saved", Toast.LENGTH_SHORT).show()
                success = true
            } else {
                Toast.makeText(this, "Error saving memo", Toast.LENGTH_SHORT).show()
            }
        } else { // Existing memo
            val updatedMemo = dbHelper.updateMemo(currentMemoId, content)
            if (updatedMemo != null) {
                Toast.makeText(this, "Memo updated", Toast.LENGTH_SHORT).show()
                success = true
            } else {
                Toast.makeText(this, "Error updating memo", Toast.LENGTH_SHORT).show()
            }
        }
        if (success) {
             setResult(Activity.RESULT_OK)
             finish()
        }
    }

    private fun deleteMemo() {
        if (currentMemoId != -1L) {
            // Consider adding a confirmation dialog here
            val success = dbHelper.deleteMemo(currentMemoId)
            if (success) {
                Toast.makeText(this, "Memo deleted", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Error deleting memo", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
