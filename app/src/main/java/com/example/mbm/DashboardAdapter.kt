package com.example.mbm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import java.io.File

class DashboardAdapter(
    private val folders: List<File>,
    private val journalNames: Map<String, String>,
    private val onFolderClick: (File) -> Unit,
    private val onFolderLongClick: (File) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPreview: ImageView = view.findViewById(R.id.iv_journal_preview)
        val tvName: TextView = view.findViewById(R.id.tv_journal_display_name)
        val ivPlay: ImageView = view.findViewById(R.id.iv_play_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]

        val displayName = journalNames[folder.name]
        if (!displayName.isNullOrEmpty()) {
            holder.tvName.text = displayName.uppercase()
        } else {
            holder.tvName.text = "JOURNAL ${folder.name}"
        }

        // Logic Preserved: Check for manual cover before applying alphanumeric fallback
        val coverFile = File(folder, "MBM_COVER.jpg")
        val firstMedia = if (coverFile.exists()) {
            coverFile
        } else {
            folder.listFiles { file ->
                (file.name.endsWith(".mp4") || file.name.endsWith(".jpg")) && file.name != "MBM_COVER.jpg"
            }?.sortedBy { it.name }?.firstOrNull()
        }

        if (firstMedia != null) {
            // Cache Busting: Using signature(ObjectKey(file.lastModified()))
            // forces Glide to refresh if the file content changes even if the name is the same.
            Glide.with(holder.itemView.context)
                .load(firstMedia)
                .signature(ObjectKey(firstMedia.lastModified()))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .centerCrop()
                .into(holder.ivPreview)

            holder.ivPlay.visibility = if (firstMedia.name.endsWith(".mp4")) View.VISIBLE else View.GONE
        } else {
            holder.ivPreview.setImageResource(android.R.drawable.ic_menu_gallery)
            holder.ivPlay.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onFolderClick(folder) }
        holder.itemView.setOnLongClickListener {
            onFolderLongClick(folder)
            true
        }
    }

    override fun getItemCount(): Int = folders.size
}