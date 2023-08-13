package com.inipage.homelylauncher.dock

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.utils.ViewUtils

/**
 * Renders dock items with padding on the left and right.
 */
class DockAdapter(context: Context, val items: List<DockControllerItem>) : RecyclerView.Adapter<DockAdapter.DockItemViewHolder>() {

    private val isSquarish: Boolean = ViewUtils.isSquarishDevice(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockItemViewHolder =
        DockItemViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.dock_collapsed_item, parent, false))

    override fun onBindViewHolder(holder: DockItemViewHolder, position: Int) {
        val item = items[position]

        holder.containerView.visibility = if (item.isLoaded) View.VISIBLE else View.GONE
        if (!item.isLoaded) {
            return
        }

        // Setup background color
        holder.containerView.background.colorFilter = PorterDuffColorFilter(
            item.tint,
            PorterDuff.Mode.SRC_IN
        )

        // Map icon
        val icon = item.icon
        val bitmap = item.bitmap
        val drawable = item.drawable
        if (icon != 0) {
            holder.iconView.setImageResource(icon)
        } else if (bitmap != null) {
            holder.iconView.setImageBitmap(bitmap)
        } else if (drawable != null) {
            holder.iconView.setImageDrawable(drawable)
        }

        // Map text
        val label = item.label
        if (label != null) {
            holder.primaryLabel.text = label
            holder.primaryLabel.visibility = View.VISIBLE
        } else {
            holder.primaryLabel.visibility = View.GONE
        }
        val secondaryLabel = item.secondaryLabel
        if (secondaryLabel != null && !isSquarish) {
            holder.secondaryLabel.text = secondaryLabel
            holder.secondaryLabel.visibility = View.VISIBLE
        } else {
            holder.secondaryLabel.visibility = View.GONE
        }

        // Map actions
        holder.itemView.setOnClickListener { item.getAction(it).run() }
        holder.itemView.setOnLongClickListener {
            val action = item.getSecondaryAction(it)
            action?.run()
            action != null
        }
    }

    override fun getItemCount() = items.size

    class DockItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val containerView: View = ViewCompat.requireViewById(view, R.id.dock_item_root_container)
        val iconView: ImageView = ViewCompat.requireViewById(view, R.id.contextual_dock_item_icon)
        val primaryLabel: TextView = ViewCompat.requireViewById(view, R.id.contextual_dock_item_label)
        val secondaryLabel: TextView = ViewCompat.requireViewById(view, R.id.contextual_dock_item_secondary_label)
    }
}