package com.aesloxia.mindlocus

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aesloxia.mindlocus.databinding.ItemTagBinding

class TagAdapter(private val onDelete: (String) -> Unit) : RecyclerView.Adapter<TagAdapter.TagViewHolder>() {

    private var tags = listOf<String>()

    fun setTags(newTags: List<String>) {
        tags = newTags
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) = holder.bind(tags[position])

    override fun getItemCount(): Int = tags.size

    inner class TagViewHolder(private val binding: ItemTagBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tagId: String) {
            binding.tvTagId.text = tagId
            // Heuristic to show different icon for NFC vs QR
            val isNfc = tagId.length <= 16 && tagId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            binding.ivTagType.setImageResource(if (isNfc) R.drawable.ic_nfc else R.drawable.ic_qr_code)

            binding.btnDeleteTag.setImageResource(R.drawable.ic_delete)
            binding.btnDeleteTag.setOnClickListener { onDelete(tagId) }
        }
    }
}
