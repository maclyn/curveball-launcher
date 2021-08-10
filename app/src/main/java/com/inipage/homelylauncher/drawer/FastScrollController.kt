package com.inipage.homelylauncher.drawer

import android.animation.Animator
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.inipage.homelylauncher.R
import com.inipage.homelylauncher.views.DecorViewManager
import java.util.*
import java.util.function.BinaryOperator
import kotlin.collections.HashMap

class FastScrollController(host: Host) {

    interface Host {

        fun getHostContext(): Context

        fun getHeaderToCountMap(): Map<String, Int>

        fun onFastScrollStateChange(isEntering: Boolean)
    }

    val _host = host
    val _languageMap = HashMap<String, Array<Char>>()
    val _lettersPerRow = 5

    init {
        // There appears to be no good way of pulling the alphabet for each language, so hard coding
        // this per language for now
        _languageMap.put(Locale.US.language, arrayOf(
            '#', '?', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
            'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'))
    }

    fun enterFastScroll() {
        val languageKey = Locale.getDefault().language
        val headersToCounts = _host.getHeaderToCountMap()
        val alphabet: Array<Char> =
            (if (_languageMap.containsKey(languageKey))
                _languageMap[languageKey] else
                headersToCounts.keys.map { key -> key[0] }.toTypedArray())
                    ?: return

        _host.onFastScrollStateChange(true)
        val context = _host.getHostContext()
        val root = ScrollView(context)
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        root.addView(container)

        // Iterate through each letter in the alphabet, creating a new view for each characters
        var workingContainer: LinearLayout? = null
        for (idx in alphabet.indices) {
            if (idx % _lettersPerRow == 0) {
                workingContainer = LinearLayout(context)
                workingContainer.orientation = LinearLayout.HORIZONTAL
                container.addView(workingContainer)
            }
            if (workingContainer == null) continue

            // TODO: Proper views
            val tmp = TextView(context)
            tmp.text = alphabet[idx].toString()
            tmp.setTextColor(context.getColor(R.color.primary_text_color))
            tmp.textSize = container.resources.getDimension(R.dimen.byline_text_size)
            workingContainer.addView(tmp)
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

    fun leaveFastScroll() {
        _host.onFastScrollStateChange(false)
    }
}