package com.inipage.homelylauncher.drawer

import android.animation.Animator
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.views.DecorViewManager
import java.util.*
import kotlin.collections.HashMap

class FastScrollController(private val host: Host) {

    interface Host {

        fun getHostContext(): Context

        fun getHeaderToCountMap(): Map<String, Int>

        fun scrollToLetter(letter: Char)

        fun hostWidth(): Int

        fun onFastScrollStateChange(isEntering: Boolean)
    }

    private val languageMap = HashMap<String, Array<Char>>()
    private val lettersPerRow = 5

    init {
        // There appears to be no good way of pulling the alphabet for each language, so hard coding
        // this per language for now
        languageMap.put(Locale.US.language, arrayOf(
            '#', '?', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
            'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'))
    }

    fun enterFastScroll() {
        val languageKey = Locale.getDefault().language
        val headersToCounts = host.getHeaderToCountMap()
        val alphabet: Array<Char> =
            (if (languageMap.containsKey(languageKey))
                languageMap[languageKey] else
                headersToCounts.keys.map { key -> key[0] }.toTypedArray())
                    ?: return

        host.onFastScrollStateChange(true)
        val context = host.getHostContext()
        val root = ScrollView(context)
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        root.addView(container)

        // Iterate through each letter in the alphabet, creating a new view for each characters
        var workingContainer: LinearLayout? = null
        val itemHeight = context.resources.getDimension(R.dimen.fast_scroll_item_height).toInt()
        val itemWidth = host.hostWidth() / lettersPerRow
        for (idx in alphabet.indices) {
            if (idx % lettersPerRow == 0) {
                workingContainer = LinearLayout(context)
                workingContainer.orientation = LinearLayout.HORIZONTAL
                container.addView(workingContainer)
            }
            if (workingContainer == null) continue

            val key = alphabet[idx].toString()
            val hasMapping = host.getHeaderToCountMap().containsKey(key)
            val letter = TextView(context)
            letter.text = key
            letter.setTextColor(context.getColor(R.color.primary_text_color))
            letter.gravity = Gravity.CENTER
            letter.textSize = container.resources.getDimension(R.dimen.fast_scroll_text_size)
            letter.background = context.drawableFromAttribute(android.R.attr.selectableItemBackground)
            letter.alpha = if (hasMapping) 1F else 0.5F
            if (hasMapping) {
                letter.setOnClickListener {
                    host.scrollToLetter(alphabet[idx])
                }
            }
            workingContainer.addView(letter, LinearLayout.LayoutParams(itemWidth, itemHeight))
        }

        DecorViewManager.get(context).attachView(
            root,
            object : DecorViewManager.Callback {
                override fun shouldTintBackgroundView() = true

                override fun provideExitAnimation(view: View): Animator? = null

                override fun onDismissedByBackgroundTap(removedView: View) = leaveFastScroll()
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    private fun Context.drawableFromAttribute(attribute: Int): Drawable? {
        val attributes = obtainStyledAttributes(intArrayOf(attribute))
        val drawable = attributes.getDrawable(0)
        attributes.recycle()
        return drawable
    }

    private fun leaveFastScroll() {
        host.onFastScrollStateChange(false)
    }
}