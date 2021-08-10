package com.inipage.homelylauncher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.views.BottomSheetContainer
import com.inipage.homelylauncher.views.BottomSheetHelper

/**
 * Renders a bottom sheet that presents a basic
 */
class NewUserBottomSheet(val context: Context) {

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.new_user_layout, null)
        ViewCompat.requireViewById<View>(view, R.id.grant_permissions_button).setOnClickListener { showPermissionsPrompts() }
        val usageButton = ViewCompat.requireViewById<View>(view, R.id.grant_app_usage_button)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            usageButton.setOnClickListener { showAppUsagePrompt() }
        } else {
            ViewCompat.requireViewById<View>(view, R.id.app_usage_explainer).visibility = View.GONE
            usageButton.visibility = View.GONE
        }
        val bottomSheetHelper = BottomSheetHelper()
            .setFixedScreenPercent(.85F)
            .setContentView(view)
        bottomSheetHelper.show(context, context.getString(R.string.welcome_title))
    }

    private fun showPermissionsPrompts() {
        val activity = ViewUtils.activityOf(context) ?: return
        activity.requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALENDAR),
            1000)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showAppUsagePrompt() {
        val activity = ViewUtils.activityOf(context) ?: return
        try {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (ignored: ActivityNotFoundException) {}
    }
}