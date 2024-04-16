package com.inipage.homelylauncher.caches

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import com.inipage.homelylauncher.utils.Constants
import java.io.File

class FontCacheSync {

    private val fontOverrideToTypeface = HashMap<String, Typeface>()

    @Synchronized
    fun reload(ctx: Context) {
        maybeLoadTypeface(ctx, Constants.GRID_FONT_PATH)
        maybeLoadTypeface(ctx, Constants.LIST_FONT_PATH)
    }

    @Synchronized
    fun clear() {
        fontOverrideToTypeface.clear()
    }

    @Synchronized
    fun applyTypefaceToTextView(tv: TextView, fontOverridePath: String) {
        val tf = fontOverrideToTypeface[fontOverridePath] ?: return
        tv.typeface = tf
    }

    private fun maybeLoadTypeface(ctx: Context, path: String) {
        val fontOverrides = File(ctx.filesDir, Constants.FONT_OVERRIDES_PATH)
        if (!fontOverrides.exists()) {
            return
        }
        val fontFile = File(fontOverrides, path)
        if (!fontFile.exists()) {
            return
        }
        try {
            fontOverrideToTypeface[path] = Typeface.createFromFile(fontFile)
        } catch (ignored: Exception) {}
    }

    companion object {

        private var instance: FontCacheSync? = null

        fun get(): FontCacheSync {
            val existingInstance = instance
            return if (existingInstance == null) {
                val newInstance = FontCacheSync()
                instance = newInstance
                return newInstance
            } else {
                existingInstance
            }
        }
    }
}