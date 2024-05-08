package com.inipage.homelylauncher.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.caches.IconCacheSync
import com.inipage.homelylauncher.drawer.BitmapView
import com.inipage.homelylauncher.model.ApplicationIconCheckable

class ActivitySelectorAdapter(
    val data: List<ApplicationIconCheckable>) :
    RecyclerView.Adapter<ActivitySelectorAdapter.ApplicationViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        return ApplicationViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.app_list_row_checkable, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val icon = data[position]
        holder.title.text = icon.name
        holder.icon.setBitmap(
            IconCacheSync
                .getInstance(holder.icon.context)
                .getActivityIcon(icon.packageName, icon.activityName))
        holder.checkbox.isChecked = icon.isChecked
        holder.itemView.setOnClickListener {
            val newState = !holder.checkbox.isChecked
            holder.checkbox.isChecked = newState
            icon.isChecked = newState
        }
        holder.checkbox.setOnClickListener {
            val newState = !holder.checkbox.isChecked
            holder.checkbox.isChecked = newState
            icon.isChecked = newState
        }
    }

    override fun getItemCount(): Int = data.size

    class ApplicationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView =  itemView.findViewById(R.id.app_icon_label)
        val icon: BitmapView = itemView.findViewById(R.id.app_icon_image)
        val checkbox: CheckBox = itemView.findViewById(R.id.app_icon_checkbox)
    }
}