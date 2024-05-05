package com.inipage.homelylauncher.views

import android.content.Context
import android.view.View

class BottomSheetContainerLayoutHost(
    private val context: Context,
    private val decorViewKey: String
) : DraggableLayout.Host {

    override fun onAnimationPartial(percentComplete: Float, translationMagnitude: Float) {
        updateBottomSheetLocation(percentComplete, translationMagnitude)
    }

    override fun onAnimationOutComplete() {
        DecorViewManager.get(context).removeView(decorViewKey)
    }

    override fun onAnimationInComplete() {
        updateBottomSheetLocation(0.0F, 0.0F)
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