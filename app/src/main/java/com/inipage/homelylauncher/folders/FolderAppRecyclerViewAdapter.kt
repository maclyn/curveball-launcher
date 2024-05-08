package com.inipage.homelylauncher.folders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.caches.AppLabelCache
import com.inipage.homelylauncher.caches.IconCacheSync
import com.inipage.homelylauncher.drawer.BitmapView
import com.inipage.homelylauncher.model.GridFolderApp
import com.inipage.homelylauncher.utils.InstalledAppUtils

class FolderAppRecyclerViewAdapter(val data: List<GridFolderApp>) :
    RecyclerView.Adapter<FolderAppRecyclerViewAdapter.ApplicationViewHolder>()
{

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
        return ApplicationViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.folder_app_icon, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
        val icon = data[position]
        holder.title.text =
            AppLabelCache.getInstance(holder.title.context).getLabel(icon.packageName, icon.activityName)
        holder.icon.setBitmap(
            IconCacheSync
                .getInstance(holder.icon.context)
                .getActivityIcon(icon.packageName, icon.activityName))
        holder.itemView.setOnClickListener {
            InstalledAppUtils.launchApp(
                holder.itemView,
                icon.packageName,
                icon.activityName,
                InstalledAppUtils.AppLaunchSource.FOLDER)
        }
    }

    override fun getItemCount(): Int = data.size

    class ApplicationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView =  itemView.findViewById(R.id.grid_icon_label)
        val icon: BitmapView = itemView.findViewById(R.id.grid_icon_image)
    }
}