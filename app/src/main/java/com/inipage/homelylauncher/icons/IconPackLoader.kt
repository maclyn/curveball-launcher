package com.inipage.homelylauncher.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import com.inipage.homelylauncher.persistence.PrefsHelper
import org.xmlpull.v1.XmlPullParser
import android.util.Pair as APair

class IconPackLoader(private val context: Context, private val packageName: String) {

    private val iconListCache =  ArrayList<APair<String, Int>>()
    val iconList: List<APair<String, Int>> = iconListCache

    val resources = context.packageManager.getResourcesForApplication(packageName)

    private val standIns = PrefsHelper.loadStandIns(packageName);
    private val drawableNameToResId = HashMap<String, Int>()
    private val componentToDrawableName = HashMap<APair<String, String>, String>()

    fun loadDrawableByName(drawableName: String): Drawable? {
        val id = drawableNameToResId[drawableName] ?: return null
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

    fun hasIconForComponent(pkg: String, activity: String): Boolean {
        val pair = APair.create(pkg, activity)
        if (standIns.contains(pair)) {
            return true
        }
        return componentToDrawableName.contains(pair)
    }

    @SuppressLint("DiscouragedApi")
    private fun loadDrawableNameMap() {
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
                val resId = resources.getIdentifier(
                    attrValue,
                    "drawable",
                    packageName
                )
                if (resId != 0) {
                    val name = resources.getResourceEntryName(resId)
                    drawableNameToResId[name] = resId
                }
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
                if (!drawableNameToResId.contains(drawableValue)) {
                    continue
                }
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

    @SuppressLint("DiscouragedApi")
    private fun getXmlForName(name: String): XmlResourceParser? {
        val id = resources.getIdentifier(name, "xml", packageName)
        if (id == 0) {
            return null
        }
        return resources.getXml(id)
    }

    init {
        loadDrawableNameMap()
        loadComponentLookupMap()
        drawableNameToResId.entries.forEach {
            iconListCache.add(APair(it.key, it.value))
        }
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