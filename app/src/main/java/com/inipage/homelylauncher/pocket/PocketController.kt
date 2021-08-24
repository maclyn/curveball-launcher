package com.inipage.homelylauncher.pocket

import android.animation.Animator
import com.inipage.homelylauncher.dock.ForwardingContainer
import com.inipage.homelylauncher.R
import android.widget.LinearLayout
import com.inipage.homelylauncher.model.SwipeFolder
import android.animation.ValueAnimator
import android.content.Context
import android.util.Pair
import android.view.*
import com.inipage.homelylauncher.persistence.DatabaseEditor
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import com.inipage.homelylauncher.model.SwipeApp
import com.inipage.homelylauncher.model.ApplicationIcon
import android.widget.TextView
import android.widget.ImageView
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import com.google.common.collect.ImmutableList
import com.inipage.homelylauncher.utils.*
import java.util.*
import java.util.stream.Collectors

/**
 * Renders a pocket of folders at the bottom of the homescreen. This pocket animates in with an
 * expand animation that's handled by [how?]. It also connects to
 */
class PocketController(
    context: Context,
    host: Host,
    container: ForwardingContainer,
    dockView: View,
    dropView: PocketControllerDropView,
    idleView: PocketOpenArrowView
) : PocketControllerDropView.Host, ForwardingContainer.ForwardingListener {
    private val mContext: Context
    private val mHost: Host
    private val mContainer: ForwardingContainer
    private val mDockView: View
    private val mDropView: PocketControllerDropView
    private val mIdleView: PocketOpenArrowView
    private val scrollView: ScrollView
    private val rowContainer: LinearLayout
    private val topScrim: View
    private val bottomScrim: View

    @SizeDimenAttribute(R.dimen.actuation_distance)
    var actuationDistance = 0
    @SizeValAttribute(48F)
    var appViewSize = 0
    @SizeValAttribute(8F)
    var betweenItemMargin = 0

    val _scaleDelta = 0.1F

    companion object {
        val SCALE_DELTA = 0.1F
    }

    private var mIsSwiping = false
    var isExpanded = false
        private set
    private var mVelocityTracker: VelocityTracker? = null
    private var mFolders: MutableList<SwipeFolder>

    fun applyScrims(topScrimSize: Int, bottomScrimSize: Int) {
        ViewUtils.setHeight(topScrim, topScrimSize)
        ViewUtils.setHeight(bottomScrim, bottomScrimSize)
        val activity = ViewUtils.activityOf(mContext) ?: return
        activity.window.decorView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (bottom - top <= 0) return
                if (activity.window.decorView.width == 0) return
                val scrollViewParams = scrollView.layoutParams
                val windowHeight = activity.window.decorView.height
                val aspectRatio = windowHeight / activity.window.decorView.width
                scrollViewParams.height =
                    when {
                        aspectRatio < 1.1 -> (windowHeight * 0.85).toInt()
                        aspectRatio < 1.5 -> (windowHeight * 0.66).toInt()
                        else -> windowHeight / 2
                    }
                scrollView.layoutParams = scrollViewParams
                activity.window.decorView.removeOnLayoutChangeListener(this)
            }
        })
    }

    fun collapse() {
        if (!isExpanded) {
            return
        }
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.addUpdateListener { animation: ValueAnimator -> setPercentExpanded(animation.animatedValue as Float) }
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                commitCollapse()
            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator.start()
    }

    fun expand() {
        if (isExpanded) {
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener { animation: ValueAnimator -> setPercentExpanded(animation.animatedValue as Float) }
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                commitExpand()
            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator.start()
    }

    fun editFolderOrder() {
        ReorderFolderBottomSheet.show(
            mContext,
            mFolders
        ) { reorderedFolders: MutableList<SwipeFolder> ->
            mFolders = reorderedFolders
            DatabaseEditor.get().saveGestureFavorites(mFolders)
            rebind()
        }
    }

    override fun onForwardEvent(event: MotionEvent, deltaY: Float) {
        log("deltaY = $deltaY")

        // deltaY = mStartY - event.getRawY()
        // < 0 = going down screen
        // > 0 = = swiping up on screen
        var percentExpanded = deltaY / actuationDistance // Works for expanding
        if (isExpanded) { // Collapsing
            percentExpanded = 1 - -deltaY / actuationDistance
        }
        if (percentExpanded < 0) {
            percentExpanded = 0f
        } else if (percentExpanded > 1) {
            percentExpanded = 1f
        }
        setPercentExpanded(percentExpanded)
        log("Percent expanded ", deltaY.toString())
        if (!mIsSwiping) {
            mIsSwiping = true
            mVelocityTracker = VelocityTracker.obtain()
            mContainer.visibility = View.VISIBLE
            mContainer.isFocusableInTouchMode = true
        }
        mVelocityTracker?.addMovement(event)
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mIsSwiping = false
                if (percentExpanded <= 0) {
                    mVelocityTracker?.recycle()
                    commitCollapse()
                    return
                } else if (percentExpanded >= 1) {
                    mVelocityTracker?.recycle()
                    commitExpand()
                    return
                }
                val context = mContainer.context
                mVelocityTracker?.computeCurrentVelocity(
                    1000,  // px/s
                    ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
                )
                val speed = mVelocityTracker?.yVelocity ?: 5F
                // Though deltaY will be > 0, speed is negative in an upwards flings
                val flingIsExpand = speed < 0
                val startPoint = mContainer.translationY
                val startVelocityScalar = Math.max(
                    Math.abs(speed),
                    context.resources.getDimension(R.dimen.min_dot_start_velocity_dp_s)
                )
                val startVelocity = startVelocityScalar * if (flingIsExpand) 1 else -1
                val minValue = 0f
                val maxValue: Float
                maxValue = if (flingIsExpand) {
                    startPoint
                } else {
                    actuationDistance.toFloat()
                }
                val flingAnimation = FlingAnimation(
                    this, object : FloatPropertyCompat<PocketController?>("translationY") {
                        override fun getValue(`object`: PocketController?): Float {
                            return mContainer.translationY
                        }

                        override fun setValue(`object`: PocketController?, value: Float) {
                            log("Interpolating to ", +value)
                            val percentExpanded = 1 - value / actuationDistance
                            log("As a percent expanded, that's ", percentExpanded)
                            setPercentExpanded(1 - value / actuationDistance)
                        }
                    })
                flingAnimation.setMinValue(minValue)
                flingAnimation.setMaxValue(maxValue)
                flingAnimation.setStartVelocity(startVelocityScalar * if (flingIsExpand) -1 else 1)
                flingAnimation.friction = ViewConfiguration.getScrollFriction()
                flingAnimation.addEndListener { animation: DynamicAnimation<*>?, canceled: Boolean, value: Float, velocity: Float ->
                    isExpanded = flingIsExpand
                    if (flingIsExpand) {
                        commitExpand()
                    } else {
                        commitCollapse()
                    }
                }
                log(
                    "About to start animation: velocity=",
                    startVelocity,
                    ", isExpand=",
                    flingIsExpand, ", min=",
                    minValue,
                    ", max=",
                    maxValue,
                    ", startPoint = ",
                    startPoint
                )
                flingAnimation.start()
            }
        }
    }

    private val _scratchArray = IntArray(2)
    override fun shouldHandleEvent(event: MotionEvent, deltaY: Float): Boolean {
        log("Should handle event for deltaY=$deltaY")
        return if (isExpanded) {
            scrollView.getLocationOnScreen(_scratchArray)
            val touchWithinScrollView = event.rawX >= _scratchArray[0] &&
                    event.rawX <= _scratchArray[0] + scrollView.width &&
                    event.rawY >= _scratchArray[1] &&
                    event.rawY <= _scratchArray[1] + scrollView.height
            val scrollsDown = scrollView.canScrollVertically(1)
            val scrollsUp = scrollView.canScrollVertically(-1)
            val doesntScroll = !scrollsUp && !scrollsDown
            // Swipe down
            deltaY < 0 && ((!touchWithinScrollView) || doesntScroll || !scrollsUp);
        } else {
            // Swipe up
            deltaY > 0
        }
    }

    private fun log(vararg out: Any) {
        DebugLogUtils.needle(
            DebugLogUtils.TAG_POCKET_ANIMATION,
            Arrays.stream(out).map { obj: Any -> obj.toString() }
                .collect(Collectors.joining()))
    }

    private fun setPercentExpanded(percent: Float) {
        mContainer.alpha = percent
        mContainer.translationY = actuationDistance - percent * actuationDistance
        mContainer.scaleX = 1 - (1 - percent) * _scaleDelta
        mContainer.scaleY = 1 - (1 - percent) * _scaleDelta
        mIdleView.rotation = percent
        mHost.onPartiallyExpandedPocket(percent)
    }

    private fun commitCollapse() {
        setPercentExpanded(0f)
        mContainer.visibility = View.GONE
        mContainer.isFocusableInTouchMode = false
        mIsSwiping = false
        isExpanded = mIsSwiping
        mHost.onPocketCollapsed()
        scrollView.scrollTo(0, 0)
    }

    private fun commitExpand() {
        setPercentExpanded(1f)
        mContainer.visibility = View.VISIBLE
        mContainer.isFocusableInTouchMode = true
        isExpanded = true
        mIsSwiping = false
        mHost.onPocketExpanded()
    }

    override fun onDragStarted() {
        mDockView.animate().alpha(0f).start()
        mIdleView.animate().alpha(0f).start()
        mDropView.alpha = 0f
        mDropView.visibility = View.VISIBLE
        mDropView
            .animate()
            .alpha(1f)
            .setListener(ViewUtils.onEndListener {
                mDockView.alpha = 0f
                mIdleView.alpha = 0f
                mDropView.alpha = 1f
            })
            .start()
    }

    override fun onDragEnded() {
        mDockView.animate().alpha(1f).start()
        mIdleView.animate().alpha(1f).start()
        mDropView
            .animate()
            .alpha(0f)
            .setListener(ViewUtils.onEndListener {
                mDockView.alpha = 1f
                mIdleView.alpha = 1f
                mDropView.visibility = View.GONE
            })
            .start()
    }

    override fun onAppAddedToFolder(folderIdx: Int, app: ApplicationIcon) {
        mFolders[folderIdx].addApp(Pair(app.packageName, app.activityName))
        DatabaseEditor.get().saveGestureFavorites(mFolders)
        mHost.clearActiveDragTarget()
    }

    override fun onNewFolderRequested(ai: ApplicationIcon) {
        val newFolder = SwipeFolder(
            "",
            Constants.PACKAGE,
            Constants.DEFAULT_FOLDER_ICON,
            ImmutableList.of(
                SwipeApp(ai.packageName, ai.activityName)
            )
        )
        FolderEditingBottomSheet.show(
            mContext,
            newFolder,
            true,
            object : FolderEditingBottomSheet.Callback {
                override fun onFolderSaved(
                    title: String,
                    iconPackage: String,
                    iconDrawable: String,
                    reorderedApps: List<SwipeApp>
                ) {
                    newFolder.replaceApps(reorderedApps)
                    newFolder.title = title
                    newFolder.setDrawable(iconDrawable, iconPackage)
                    mFolders.add(newFolder)
                    DatabaseEditor.get().saveGestureFavorites(mFolders)
                    rebind()
                }

                override fun onFolderDeleted() {}
            })
    }

    override fun getFolders(): List<SwipeFolder> {
        return mFolders
    }

    private fun rebind() {
        mDropView.attachHost(this)
        val inflater = LayoutInflater.from(mContext)
        rowContainer.removeAllViews()
        for (i in mFolders.indices) {
            val folder = mFolders[i]
            val folderItem = inflater.inflate(R.layout.pocket_folder_row, rowContainer, false)
            (folderItem.findViewById<View>(R.id.pocket_folder_row_folder_icon) as ImageView)
                .setImageBitmap(folder.getIcon(mContext))
            (folderItem.findViewById<View>(R.id.pocket_folder_row_folder_title) as TextView).text =
                folder.title
            folderItem.setOnLongClickListener { v: View? ->
                editFolder(i)
                collapse()
                true
            }
            val appContainer =
                ViewCompat.requireViewById<LinearLayout>(folderItem, R.id.pocket_folder_row_app_container)
            val folderSize = mFolders[i].shortcutApps.size
            for (j in 0 until folderSize) {
                val app = mFolders[i].shortcutApps.get(j)
                val appView = inflater.inflate(R.layout.pocket_app_view, appContainer, false) as ImageView
                appView.setImageBitmap(app.getIcon(mContext))
                appView.setOnClickListener { v: View? ->
                    InstalledAppUtils.launchApp(
                        v, app.component.first, app.component.second
                    )
                }
                val params = LinearLayout.LayoutParams(appViewSize, appViewSize)
                params.rightMargin = betweenItemMargin
                params.leftMargin = if (j != 0) params.rightMargin else 0
                params.gravity = Gravity.CENTER_VERTICAL
                appContainer.addView(appView, params)
                if (j != folderSize - 1) {
                    appContainer.addView(ViewUtils.createFillerView(mContext, betweenItemMargin))
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            rowContainer.addView(folderItem, params)
        }
    }

    private fun editFolder(row: Int) {
        FolderEditingBottomSheet.show(
            mContext,
            mFolders[row],
            false,
            object : FolderEditingBottomSheet.Callback {
                override fun onFolderSaved(
                    title: String,
                    iconPackage: String,
                    iconDrawable: String,
                    reorderedApps: List<SwipeApp>
                ) {
                    mFolders[row].title = title
                    mFolders[row].setDrawable(iconDrawable, iconPackage)
                    mFolders[row].replaceApps(reorderedApps)
                    DatabaseEditor.get().saveGestureFavorites(mFolders)
                    rebind()
                }

                override fun onFolderDeleted() {
                    mFolders.removeAt(row)
                    DatabaseEditor.get().saveGestureFavorites(mFolders)
                    rebind()
                }
            })
    }

    interface Host {
        fun onPartiallyExpandedPocket(percent: Float)
        fun onPocketExpanded()
        fun onPocketCollapsed()
        fun clearActiveDragTarget()
    }

    init {
        AttributeApplier.applyDensity(this, context)
        mContext = context
        mHost = host
        mContainer = container
        mDockView = dockView
        mDropView = dropView
        mIdleView = idleView
        mFolders = DatabaseEditor.get().gestureFavorites
        mIdleView.setOnClickListener { v: View? ->
            if (isExpanded) {
                collapse()
            } else {
                expand()
            }
        }
        val view = LayoutInflater.from(context).inflate(R.layout.pocket_container_view, mContainer, true)
        rowContainer = ViewCompat.requireViewById(view, R.id.pocket_container_view_row_container)
        scrollView = ViewCompat.requireViewById(view, R.id.pocket_container_view_scroll_view)
        topScrim = ViewCompat.requireViewById(view, R.id.pocket_top_scrim)
        bottomScrim = ViewCompat.requireViewById(view, R.id.pocket_bottom_scrim)
        rebind()
    }
}