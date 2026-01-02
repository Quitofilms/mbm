package com.example.mbm

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MediaPickerAdapter(
    private val items: List<Any>,
    private val onMediaClick: (Uri) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_MEDIA = 1

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tv_picker_header_date)
    }

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.iv_picker_thumbnail)
        val ivPlayIcon: ImageView = view.findViewById(R.id.iv_picker_play_icon)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_MEDIA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_picker_media, parent, false)
            MediaViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.tvHeader.text = items[position] as String
        } else if (holder is MediaViewHolder) {
            val uri = items[position] as Uri

            Glide.with(holder.itemView.context)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(holder.ivThumbnail)

            holder.ivPlayIcon.visibility = if (uri.toString().contains("video")) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onMediaClick(uri) }
        }
    }

    override fun getItemCount(): Int = items.size
}