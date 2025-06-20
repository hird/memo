package com.example.memo

import android.app.Activity // Required for RESULT_OK
import android.content.Intent // Required for starting activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View // Added import
import android.widget.TextView // Added import
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts // For modern activity result handling
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    
    private lateinit var recyclerViewMemos: RecyclerView
    private lateinit var fabAddMemo: FloatingActionButton
    private lateinit var memoAdapter: MemoAdapter
    private lateinit var dbHelper: MemosDatabaseHelper
    private lateinit var textViewEmptyList: TextView // Added property

     // ActivityResultLauncher for EditMemoActivity
    private val editMemoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadMemos() // Refresh list if memo was saved/deleted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = MemosDatabaseHelper(this)

        recyclerViewMemos = findViewById(R.id.recyclerViewMemos)
        fabAddMemo = findViewById(R.id.fabAddMemo)
        textViewEmptyList = findViewById(R.id.textViewEmptyList) // Initialize property

        setupRecyclerView()
        loadMemos()

        fabAddMemo.setOnClickListener {
            val intent = Intent(this, EditMemoActivity::class.java)
            editMemoLauncher.launch(intent)
        }
        
        checkAndRequestPermissions()
    }

    private fun setupRecyclerView() {
        memoAdapter = MemoAdapter(emptyList()) { memo ->
            val intent = Intent(this, EditMemoActivity::class.java)
            intent.putExtra(EditMemoActivity.EXTRA_MEMO_ID, memo.optLong("id", -1L))
            editMemoLauncher.launch(intent)
        }
        recyclerViewMemos.adapter = memoAdapter
        recyclerViewMemos.layoutManager = LinearLayoutManager(this)
    }

    private fun loadMemos() {
        val rawMemos = dbHelper.getMemos()
        val memosWithTags = mutableListOf<JSONObject>()

        for (memoJson in rawMemos) {
            val memoId = memoJson.optLong("id", -1L)
            if (memoId != -1L) {
                val tags = dbHelper.getTagsForMemo(memoId)
                if (tags.isNotEmpty()) {
                    memoJson.put("tagString", tags.joinToString(", "))
                } else {
                    memoJson.put("tagString", "") // Ensure key exists even if empty
                }
            }
            memosWithTags.add(memoJson)
        }

        memoAdapter.updateMemos(memosWithTags)
        Log.d(TAG, "Loaded ${memosWithTags.size} memos with tags.")

        if (memosWithTags.isEmpty()) {
            recyclerViewMemos.visibility = View.GONE
            textViewEmptyList.visibility = View.VISIBLE
        } else {
            recyclerViewMemos.visibility = View.VISIBLE
            textViewEmptyList.visibility = View.GONE
        }
    }
    
    // onResume is no longer strictly needed if using ActivityResultLauncher for refresh
    // override fun onResume() {
    //     super.onResume()
    //     // loadMemos() // Can be removed if editMemoLauncher handles refresh
    // }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
            // android.Manifest.permission.INTERNET, // REMOVED
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
}