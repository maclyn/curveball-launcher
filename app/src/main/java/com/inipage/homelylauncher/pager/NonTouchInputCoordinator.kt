package com.inipage.homelylauncher.pager

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.inipage.homelylauncher.state.LayoutEditingSingleton
import com.inipage.homelylauncher.utils.DebugLogUtils
import com.inipage.homelylauncher.utils.ViewUtils
import com.inipage.homelylauncher.views.DecorViewManager
import kotlin.math.abs

/**
 * Helps handle input devices that aren't touch devices for the main activity.
 */
class NonTouchInputCoordinator(private val host: Host, private val context: Activity): Handler.Callback {

    interface Host {

        // Getters

        fun isOnAppDrawer(): Boolean

        fun isOnLastPage(): Boolean

        fun isOnFirstHomeScreen(): Boolean

        fun getPager(): HomePager

        fun isPocketExpanded(): Boolean

        fun isAlphabeticalPickerOpen(): Boolean

        // "Setters"

        fun commitNonTouchInput(msg: NonTouchInputMessage)

        // Supporting access to HomeActivity

        fun defaultDispatchGenericMotionEvent(ev: MotionEvent): Boolean

        fun defaultDispatchKeyEvent(ev: KeyEvent): Boolean

        fun defaultOnKeyDown(keyCode: Int, ev: KeyEvent): Boolean
    }

    enum class NonTouchInputMessage {
        EXPAND_POCKET,
        COLLAPSE_POCKET,
        EXPAND_STATUS_BAR,
        SWITCH_RIGHT,
        SWITCH_LEFT,
        SWITCH_TO_HOME_SCREEN,
        NO_OP
    }

    private val handler = Handler(Looper.getMainLooper(), this)
    private val pagingSlop =
        ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat() * 2

    private var targetPointerId = 0
    private var virtualTrackpadXStart = 0F
    private var virtualTrackpadYStart = 0F

    fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (!ViewUtils.isEventFromVirtualTrackball(ev)) {
            return host.defaultDispatchGenericMotionEvent(ev)
        }

        val pager = host.getPager()
        val decorViewManager = DecorViewManager.get(context)
        val hasActiveOverlay = decorViewManager.hasOpenView()
        if (hasActiveOverlay) {
            decorViewManager.feedTrackballEvent(ev)
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                targetPointerId = ev.getPointerId(0)
                virtualTrackpadXStart = ev.rawX
                virtualTrackpadYStart = ev.rawY
            }
            MotionEvent.ACTION_UP -> {
                if (ev.getPointerId(0) != targetPointerId) {
                    return false
                }
                val xDelta: Float = ev.rawX - virtualTrackpadXStart
                val yDelta: Float = ev.rawY - virtualTrackpadYStart
                if (abs(xDelta) < pagingSlop && abs(yDelta) < pagingSlop) {
                    return false
                }
                val isVerticalScroll = abs(yDelta) > abs(xDelta)
                if (isVerticalScroll) {
                    if (yDelta > 0 && !host.isOnAppDrawer()) {
                        if (host.isPocketExpanded()) {
                            queueMessage(NonTouchInputMessage.COLLAPSE_POCKET)
                        } else {
                            queueMessage(NonTouchInputMessage.EXPAND_STATUS_BAR)
                        }
                    } else if (!host.isOnAppDrawer()) {
                        queueMessage(NonTouchInputMessage.EXPAND_POCKET)
                    }
                } else {
                    if (xDelta > 0) {
                        if (!host.isOnAppDrawer()) {
                            queueMessage(NonTouchInputMessage.SWITCH_LEFT)
                        }
                    } else {
                        if (!host.isOnLastPage()) {
                            queueMessage(NonTouchInputMessage.SWITCH_RIGHT)
                        }
                    }
                }
            }
        }
        if (host.isOnAppDrawer()) {
            if (!pager.appDrawerController.isSearching) {
                pager.appDrawerController.feedTrackballEvent(ev)
            }
        }
        return false
    }

    fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        DebugLogUtils.needle(DebugLogUtils.TAG_VIRTUAL_TRACKPAD, "dispatchKeyEvent", ev)
        if (DecorViewManager.get(context).hasOpenView()) {
            return host.defaultDispatchKeyEvent(ev)
        }

        if (host.isOnAppDrawer() &&
            host.isAlphabeticalPickerOpen() &&
            ViewUtils.isSquarishDevice(context)
        ) {
            DebugLogUtils.needle(
                DebugLogUtils.TAG_VIRTUAL_TRACKPAD,
                "dispatchKeyEvent to alphabet picker"
            )
            host.getPager().appDrawerController.feedKeyboardEvent(ev)
            return false
        }
        return host.defaultDispatchKeyEvent(ev)
    }

    fun onKeyDown(keyCode: Int, ev: KeyEvent): Boolean {
        DebugLogUtils.needle(DebugLogUtils.TAG_VIRTUAL_TRACKPAD, "onKeyDown", ev)
        if (ViewUtils.isEventFromVirtualTrackball(ev)) {
            // These are caught (and ignored) in dispatchGenericMotionEvent();
            // this is probably a KEYCODE_PROG_BLUE for some reason
            DebugLogUtils.needle(
                DebugLogUtils.TAG_VIRTUAL_TRACKPAD,
                "onKeyDown, isEventFromVirtualTrackball"
            )
            return true
        }
        if (ViewUtils.isEventFromPhysicalKeyboard(ev)) {
            DebugLogUtils.needle(
                DebugLogUtils.TAG_VIRTUAL_TRACKPAD,
                "onKeyDown, isEventFromPhysicalKeyboard"
            )
            runMessageImmediately(NonTouchInputMessage.NO_OP)
            if (!DecorViewManager.get(context).hasOpenView()) {
                if (!host.isOnAppDrawer()) {
                    host.getPager().appDrawerController.feedKeyboardEvent(ev)
                    return true
                } else if (!host.getPager().appDrawerController.isSearching) {
                    host.getPager().appDrawerController.feedKeyboardEvent(ev)
                    return true
                }
            }
            return host.defaultOnKeyDown(keyCode, ev)
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (DecorViewManager.get(context).detachTopView()) {
                return true
            }
            if (host.isPocketExpanded()) {
                runMessageImmediately(NonTouchInputMessage.COLLAPSE_POCKET)
                return true
            }
            if (host.isOnAppDrawer()) {
                runMessageImmediately(NonTouchInputMessage.SWITCH_TO_HOME_SCREEN)
                return true
            }
            if (LayoutEditingSingleton.getInstance().isEditing) {
                LayoutEditingSingleton.getInstance().isEditing = false
                return true
            }
            if (!host.isOnFirstHomeScreen()) {
                runMessageImmediately(NonTouchInputMessage.SWITCH_TO_HOME_SCREEN)
                return true
            }
            return false
        }
        return host.defaultOnKeyDown(keyCode, ev)
    }

    override fun handleMessage(msg: Message): Boolean {
        return true
    }

    private fun runMessageImmediately(msg: NonTouchInputMessage) {
        handler.removeCallbacksAndMessages(null)
        host.commitNonTouchInput(msg)
    }

    private fun queueMessage(msg: NonTouchInputMessage) {
        handler.removeCallbacksAndMessages(null)
        handler.sendMessageDelayed(
            Message.obtain(handler) { runMessageImmediately(msg) }, ACTION_DELAY)
    }

    companion object {
        /**
         * Additional delay before running actions because a swipe motion event might complete a
         * few milliseconds before a KeyEvent.ACTION_UP
         */
        private const val ACTION_DELAY = 20L
    }
}