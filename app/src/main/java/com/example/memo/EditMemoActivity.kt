package com.example.memo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Ensure this import is present
import androidx.appcompat.app.AppCompatActivity

class EditMemoActivity : AppCompatActivity() {

    private lateinit var editTextMemoContent: EditText
    private lateinit var editTextTags: EditText // New EditText for tags
    private lateinit var buttonSaveMemo: Button
    private lateinit var buttonDeleteMemo: Button

    private lateinit var dbHelper: MemosDatabaseHelper
    private var currentMemoId: Long = -1L
    private val TAG = "EditMemoActivity"

    companion object {
        const val EXTRA_MEMO_ID = "MEMO_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_memo)

        editTextMemoContent = findViewById(R.id.editTextMemoContent)
        editTextTags = findViewById(R.id.editTextTags) // Initialize new EditText
        buttonSaveMemo = findViewById(R.id.buttonSaveMemo)
        buttonDeleteMemo = findViewById(R.id.buttonDeleteMemo)

        dbHelper = MemosDatabaseHelper(this)
        currentMemoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)

        if (currentMemoId != -1L) {
            title = "Edit Memo"
            loadMemoContentAndTags() // Updated method name
            buttonDeleteMemo.visibility = View.VISIBLE
        } else {
            title = "Create Memo"
            buttonDeleteMemo.visibility = View.GONE
        }

        buttonSaveMemo.setOnClickListener { saveMemoAndTags() } // Updated method name
        buttonDeleteMemo.setOnClickListener { deleteMemo() }
    }

    private fun loadMemoContentAndTags() {
        val memoObject = dbHelper.getMemoById(currentMemoId)
        if (memoObject != null) {
            editTextMemoContent.setText(memoObject.optString("content"))
            val tags = dbHelper.getTagsForMemo(currentMemoId)
            editTextTags.setText(tags.joinToString(", "))
        } else {
            Log.e(TAG, "Memo with ID $currentMemoId not found.")
            Toast.makeText(this, "Error loading memo", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveMemoAndTags() {
        val content = editTextMemoContent.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "Content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        var memoIdToUse: Long = currentMemoId
        var success = false

        if (currentMemoId == -1L) { // New memo
            val newMemo = dbHelper.createMemo(content)
            if (newMemo != null) {
                memoIdToUse = newMemo.optLong("id", -1L)
                if (memoIdToUse != -1L) {
                    Toast.makeText(this, "Memo saved", Toast.LENGTH_SHORT).show()
                    success = true
                } else {
                    Toast.makeText(this, "Error saving memo (could not get ID)", Toast.LENGTH_SHORT).show()
                }
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

        if (success && memoIdToUse != -1L) {
            processAndSaveTags(memoIdToUse)
            setResult(Activity.RESULT_OK)
            finish()
        } else if (success && currentMemoId == -1L) {
            // New memo saved but ID retrieval failed, or some other issue
            Log.e(TAG, "Memo saved but issue with ID for tags, or success was false but new memo path.")
            // Not calling finish, user might need to retry or data is partially saved.
        }
    }

    private fun processAndSaveTags(memoId: Long) {
        dbHelper.unlinkAllTagsFromMemo(memoId) // Unlink old tags first

        val tagString = editTextTags.text.toString().trim()
        if (tagString.isNotEmpty()) {
            val tagNames = tagString.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct() // Avoid duplicate tags for the same memo

            for (tagName in tagNames) {
                val tagId = dbHelper.findOrCreateTag(tagName)
                if (tagId != -1L) {
                    dbHelper.linkMemoToTag(memoId, tagId)
                } else {
                    Log.e(TAG, "Failed to find or create tag: $tagName")
                }
            }
        }
    }

    private fun deleteMemo() {
        if (currentMemoId != -1L) {
            AlertDialog.Builder(this)
                .setTitle("Delete Memo")
                .setMessage("Are you sure you want to delete this memo?")
                .setPositiveButton("Delete") { _, _ ->
                    performDelete()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun performDelete() { // New method extracted for actual deletion
        if (currentMemoId != -1L) { // Check again, though logically covered
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
