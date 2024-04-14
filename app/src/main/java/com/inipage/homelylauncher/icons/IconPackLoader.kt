package com.inipage.homelylauncher.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import com.inipage.homelylauncher.persistence.PrefsHelper
import org.xmlpull.v1.XmlPullParser
import android.util.Pair as APair

class IconPackLoader(context: Context, private val packageName: String) {

    val resources = context.packageManager.getResourcesForApplication(packageName)

    private val standIns = PrefsHelper.loadStandIns(packageName)
    private val knownDrawables = ArrayList<String>()
    val iconPackDrawables: List<String> = knownDrawables
    private val drawableNameToResId = HashMap<String, Int>()
    private val componentToDrawableName = HashMap<APair<String, String>, String>()

    fun loadDrawableByName(drawableName: String): Drawable? {
        val id = getResIdFromDrawableName(drawableName) ?: return null
        return resources.getDrawable(id)
    }

    fun loadDrawableForComponent(component: APair<String, String>): Drawable? {
        val pair = APair.create(component.first, component.second)
        val standIn = standIns[pair]
        if (standIn != null) {
            return loadDrawableByName(standIn)
        }
        val drawableName = componentToDrawableName[component] ?: return null
        return loadDrawableByName(drawableName)
    }

    fun loadDrawableForComponent(pkg: String, activity: String): Drawable? =
        loadDrawableForComponent(APair.create(pkg, activity))

    /**
     * "Probably" because we don't do any explicit validation that just because the icon pack
     * *defines* a mapping between component -> icon, doesn't mean the needed drawable actually
     * exists.
     */
    fun probablyHasIconForComponent(pkg: String, activity: String): Boolean {
        val pair = APair.create(pkg, activity)
        if (standIns.contains(pair)) {
            return true
        }
        return componentToDrawableName.contains(pair)
    }

    /**
     * This is useful for enumerating all icons in a pack. It is *abysmal* for performance to
     * actually go from drawable name to resource ID before we need to, so we don't do that ahead of
     * time.
     */
    @SuppressLint("DiscouragedApi")
    private fun loadKnownDrawables() {
        val xmlParser = getXmlForName("drawable") ?: return

        // Go through and grab drawable="" components
        var eventType = xmlParser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType != XmlPullParser.START_TAG) {
                eventType = xmlParser.next()
                continue
            }
            val attrValue = xmlParser.getAttributeValue(null, "drawable")
            if (attrValue != null) {
                knownDrawables.add(attrValue);
            }
            eventType = xmlParser.next()
        }
        xmlParser.close()
    }

    private fun loadComponentLookupMap() {
        val xmlParser = getXmlForName("appfilter") ?: return

        var eventType = xmlParser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType != XmlPullParser.START_TAG) {
                eventType = xmlParser.next()
                continue
            }
            val componentValue = xmlParser.getAttributeValue(null, "component")
            val drawableValue = xmlParser.getAttributeValue(null, "drawable")
            eventType = xmlParser.next()
            if (componentValue != null && drawableValue != null) {
                // ComponentInfo{packageName/activity}
                val startIdx = componentValue.indexOf("{") + 1
                val endIdx = componentValue.indexOf("}")
                val midPoint = componentValue.indexOf("/")
                if (startIdx >= componentValue.length|| endIdx < 0 || midPoint < 0 ||
                    midPoint > endIdx || midPoint <= startIdx) {
                    continue
                }
                val packageName = componentValue.substring(startIdx, midPoint)
                val activityName = componentValue.substring(midPoint + 1, endIdx)
                componentToDrawableName[APair.create(packageName, activityName)] = drawableValue
            }
        }

        xmlParser.close()
    }

    private fun getResIdFromDrawableName(name: String): Int? {
        if (drawableNameToResId.contains(name)) {
            return drawableNameToResId[name]
        }
        val resId = resources.getIdentifier(
            name,
            "drawable",
            packageName
        )
        if (resId != 0) {
            try {
                drawableNameToResId[name] = resId
                return resId
            } catch (ignored: Resources.NotFoundException) {}
        }
        return null
    }

    @SuppressLint("DiscouragedApi")
    private fun getXmlForName(name: String): XmlResourceParser? {
        val id = resources.getIdentifier(name, "xml", packageName)
        if (id == 0) {
            return null
        }
        return resources.getXml(id)
    }

    init {
        loadKnownDrawables()
        loadComponentLookupMap()
    }

    companion object {
        fun resolveIconPacks(context: Context): List<APair<String, String>> {
            val list = ArrayList<APair<String, String>>()
            // com.novalauncher.THEME probably also works
            val allPacksIntent = Intent("com.gau.go.launcherex.theme", null)
            val matches = context.packageManager.queryIntentActivities(allPacksIntent, 0)
            for (resolveInfo in matches) {
                list.add(
                    APair(
                        resolveInfo.activityInfo.packageName,
                        resolveInfo.loadLabel(context.packageManager) as String))
            }
            return list
        }
    }
}