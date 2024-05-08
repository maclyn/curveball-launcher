package com.inipage.homelylauncher.views

import android.content.Context
import android.util.Log
import android.view.View

class BottomSheetContainerLayoutHost(
    private val context: Context,
    private val decorViewKey: String,
    private val dismissedCallback: OnDismissedBySwipeCallback
) : DraggableLayout.Host {

    fun interface OnDismissedBySwipeCallback {
        fun onDismissedBySwipe()
    }

    override fun onAnimationPartial(percentComplete: Float, translationMagnitude: Float) {
        updateBottomSheetLocation(percentComplete, translationMagnitude)
    }

    override fun onAnimationOutComplete() {
        dismissedCallback.onDismissedBySwipe()
        DecorViewManager.get(context).removeView(decorViewKey)
    }

    override fun onAnimationInComplete() {
        updateBottomSheetLocation(100.0F, 0.0F)
    }

    private fun updateBottomSheetLocation(percentComplete: Float, translationMagnitude: Float) {
        val v = getTargetView() ?: return
        v.translationY = translationMagnitude
        DecorViewManager.get(context).updateTintPercent(decorViewKey, percentComplete)
    }

    private fun getTargetView(): View? {
        return DecorViewManager.get(context).getView(decorViewKey)
    }
}