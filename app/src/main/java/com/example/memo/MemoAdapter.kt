package com.example.memo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoAdapter(
    private var memos: List<JSONObject>,
    private val onItemClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_memo, parent, false)
        return MemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val memo = memos[position]
        holder.bind(memo, onItemClick)
    }

    override fun getItemCount(): Int = memos.size

    fun updateMemos(newMemos: List<JSONObject>) {
        this.memos = newMemos
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    class MemoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contentTextView: TextView = itemView.findViewById(R.id.textViewMemoContent)
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewMemoDate)
        private val tagsTextView: TextView = itemView.findViewById(R.id.textViewMemoTags) // New TextView for tags

        fun bind(memo: JSONObject, onItemClick: (JSONObject) -> Unit) {
            contentTextView.text = memo.optString("content", "No content")

            val createdTimestamp = memo.optLong("createdTs", 0)
            val updatedTimestamp = memo.optLong("updatedTs", 0)

            // Prioritize updatedTs if it's later than createdTs, otherwise use createdTs
            val displayTimestamp = if (updatedTimestamp > createdTimestamp) updatedTimestamp else createdTimestamp

            if (displayTimestamp > 0) {
                val date = Date(displayTimestamp * 1000) // Timestamps are in seconds
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                // Optionally, prefix with "Edited: " if updatedTimestamp > createdTimestamp
                val datePrefix = if (updatedTimestamp > createdTimestamp && createdTimestamp > 0) "(Edited) " else ""
                dateTextView.text = datePrefix + format.format(date)
            } else {
                dateTextView.text = "No date"
            }

            // Display tags
            val tagString = memo.optString("tagString", "") // Expecting "tagString" from MainActivity
            if (tagString.isNotEmpty()) {
                tagsTextView.text = "Tags: $tagString"
                tagsTextView.visibility = View.VISIBLE
            } else {
                tagsTextView.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(memo) }
        }
    }
}
