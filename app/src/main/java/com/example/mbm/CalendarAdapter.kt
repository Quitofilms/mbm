package com.example.mbm

import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

class CalendarAdapter(
    private val items: List<Any>,
    private val vaultPath: File,
    private val onDayClick: (Date) -> Unit,
    private val onDayLongClick: (Date) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val fileDateFormatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
    private val selectedDates = mutableSetOf<Date>()

    companion object {
        const val TYPE_MONTH = 0
        const val TYPE_DAY = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_MONTH else TYPE_DAY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_MONTH) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_month_header, parent, false)
            MonthViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
            DayViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MonthViewHolder) {
            holder.tvMonthLabel.text = items[position] as String
        } else if (holder is DayViewHolder) {
            val date = items[position] as Date
            val calendar = Calendar.getInstance()
            calendar.time = date

            holder.tvDayNumber.text = calendar.get(Calendar.DAY_OF_MONTH).toString()

            val dateStr = fileDateFormatter.format(date)
            val videoFile = File(vaultPath, "MBM_$dateStr.mp4")
            val imageFile = File(vaultPath, "MBM_$dateStr.jpg")

            holder.ivStatusOverlay.visibility = if (videoFile.exists() || imageFile.exists()) View.VISIBLE else View.GONE

            val mediaFile = if (videoFile.exists()) videoFile else if (imageFile.exists()) imageFile else null

            if (mediaFile != null) {
                // Surgical Cache Invalidation: Use lastModified signature to force refresh pixels
                Glide.with(holder.itemView.context)
                    .load(mediaFile)
                    .signature(ObjectKey(mediaFile.lastModified()))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageDrawable(null)
            }

            if (selectedDates.contains(date)) {
                holder.itemView.setBackgroundColor(Color.parseColor("#8000BCD4"))
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#1A1A1A"))
            }

            holder.itemView.setOnClickListener { onDayClick(date) }
            holder.itemView.setOnLongClickListener {
                onDayLongClick(date)
                true
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun toggleSelection(date: Date) {
        if (selectedDates.contains(date)) {
            selectedDates.remove(date)
        } else {
            selectedDates.add(date)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedDates.clear()
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedDates.size
    fun getSelectedDates(): List<Date> = selectedDates.toList()

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDayNumber: TextView = view.findViewById(R.id.tv_day_number)
        val ivThumbnail: ImageView = view.findViewById(R.id.iv_thumbnail)
        val ivStatusOverlay: ImageView = view.findViewById(R.id.iv_status_indicator)
    }

    class MonthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMonthLabel: TextView = view.findViewById(R.id.tv_month_label)
    }
}