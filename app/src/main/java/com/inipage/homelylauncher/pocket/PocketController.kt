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
import kotlin.math.abs

/**
 * Renders a pocket of folders at the bottom of the homescreen. This pocket animates in with an
 * expand animation that's handled by [how?]. It also connects to
 */
class PocketController(
    private val context: Context,
    private val host: Host,
    private val container: ForwardingContainer,
    private val dockView: View,
    private val dropView: PocketControllerDropView,
    private val idleView: PocketOpenArrowView
) : PocketControllerDropView.Host, ForwardingContainer.ForwardingListener {

    interface Host {
        fun onPartiallyExpandedPocket(percent: Float)
        fun onPocketExpanded()
        fun onPocketCollapsed()
        fun clearActiveDragTarget()
    }

    private val view = LayoutInflater.from(context).inflate(R.layout.pocket_container_view, container, true)
    private val scrollView: ScrollView = ViewCompat.requireViewById(view, R.id.pocket_container_view_scroll_view)
    private val rowContainer: LinearLayout = ViewCompat.requireViewById(view, R.id.pocket_container_view_row_container)
    private val topScrim: View = ViewCompat.requireViewById(view, R.id.pocket_top_scrim)
    private val bottomScrim: View = ViewCompat.requireViewById(view, R.id.pocket_bottom_scrim)

    @SizeDimenAttribute(R.dimen.actuation_distance)
    var actuationDistance = 0
    @SizeValAttribute(48F)
    var appViewSize = 0
    @SizeValAttribute(8F)
    var betweenItemMargin = 0

    private var isSwiping = false
    private var velocityTracker: VelocityTracker? = null
    private var folders: MutableList<SwipeFolder> = DatabaseEditor.get().gestureFavorites
    var isExpanded = false
        private set

    fun applyScrims(topScrimSize: Int, bottomScrimSize: Int) {
        ViewUtils.setHeight(topScrim, topScrimSize)
        ViewUtils.setHeight(bottomScrim, bottomScrimSize)
        val activity = ViewUtils.activityOf(context) ?: return
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
                        aspectRatio < 1.5 -> (windowHeight * 0.80).toInt()
                        else -> (windowHeight * 0.75).toInt()
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
        animator.duration = animationDuration
        animator.start()
    }

    fun expand() {
        if (isExpanded) {
            return
        }
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener { animation: ValueAnimator -> setPercentExpanded(animation.animatedValue as Float) }
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                container.visibility = View.VISIBLE
            }
            override fun onAnimationEnd(animation: Animator) {
                commitExpand()
            }

            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })
        animator.duration = animationDuration
        animator.start()
    }

    fun editFolderOrder() {
        ReorderFolderBottomSheet.show(
            context,
            folders
        ) { reorderedFolders: MutableList<SwipeFolder> ->
            folders = reorderedFolders
            DatabaseEditor.get().saveGestureFavorites(folders)
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
        if (!isSwiping) {
            isSwiping = true
            velocityTracker = VelocityTracker.obtain()
            container.visibility = View.VISIBLE
            container.isFocusableInTouchMode = true
        }
        velocityTracker?.addMovement(event)
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSwiping = false
                if (percentExpanded <= 0) {
                    velocityTracker?.recycle()
                    commitCollapse()
                    return
                } else if (percentExpanded >= 1) {
                    velocityTracker?.recycle()
                    commitExpand()
                    return
                }
                val context = container.context
                velocityTracker?.computeCurrentVelocity(
                    1000,  // px/s
                    ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
                )
                val speed = velocityTracker?.yVelocity ?: 5F
                // Though deltaY will be > 0, speed is negative in an upwards flings
                val flingIsExpand = speed < 0
                val startPoint = container.translationY
                val startVelocityScalar = Math.max(
                    abs(speed),
                    context.resources.getDimension(R.dimen.min_dot_start_velocity_dp_s)
                )
                val startVelocity = startVelocityScalar * if (flingIsExpand) 1 else -1
                val minValue = 0f
                val maxValue: Float = if (flingIsExpand) {
                    startPoint
                } else {
                    actuationDistance.toFloat()
                }
                val flingAnimation = FlingAnimation(
                    this, object : FloatPropertyCompat<PocketController?>("translationY") {
                        override fun getValue(`object`: PocketController?): Float {
                            return container.translationY
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
        container.alpha = percent
        container.translationY = actuationDistance - percent * actuationDistance
        container.scaleX = 1 - (1 - percent) * scaleDelta
        container.scaleY = 1 - (1 - percent) * scaleDelta
        idleView.rotation = percent
        host.onPartiallyExpandedPocket(percent)
    }

    private fun commitCollapse() {
        setPercentExpanded(0f)
        container.visibility = View.GONE
        container.isFocusableInTouchMode = false
        isSwiping = false
        isExpanded = isSwiping
        host.onPocketCollapsed()
        scrollView.scrollTo(0, 0)
    }

    private fun commitExpand() {
        setPercentExpanded(1f)
        container.visibility = View.VISIBLE
        container.isFocusableInTouchMode = true
        isExpanded = true
        isSwiping = false
        host.onPocketExpanded()
    }

    override fun onDragStarted() {
        dockView.animate().alpha(0f).start()
        idleView.animate().alpha(0f).start()
        dropView.alpha = 0f
        dropView.visibility = View.VISIBLE
        dropView
            .animate()
            .alpha(1f)
            .setListener(ViewUtils.onEndListener {
                dockView.alpha = 0f
                idleView.alpha = 0f
                dropView.alpha = 1f
            })
            .start()
    }

    override fun onDragEnded() {
        dockView.animate().alpha(1f).start()
        idleView.animate().alpha(1f).start()
        dropView
            .animate()
            .alpha(0f)
            .setListener(ViewUtils.onEndListener {
                dockView.alpha = 1f
                idleView.alpha = 1f
                dropView.visibility = View.GONE
            })
            .start()
    }

    override fun onAppAddedToFolder(folderIdx: Int, app: ApplicationIcon) {
        folders[folderIdx].addApp(Pair(app.packageName, app.activityName))
        DatabaseEditor.get().saveGestureFavorites(folders)
        host.clearActiveDragTarget()
        rebind()
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
            context,
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
                    folders.add(newFolder)
                    DatabaseEditor.get().saveGestureFavorites(folders)
                    rebind()
                }

                override fun onFolderDeleted() {}
            })
    }

    override fun getFolders(): List<SwipeFolder> {
        return folders
    }

    private fun rebind() {
        dropView.attachHost(this)
        val inflater = LayoutInflater.from(context)
        rowContainer.removeAllViews()
        for (i in folders.indices) {
            val folder = folders[i]
            val folderItem = inflater.inflate(R.layout.pocket_folder_row, rowContainer, false)
            (folderItem.findViewById<View>(R.id.pocket_folder_row_folder_icon) as ImageView)
                .setImageBitmap(folder.getIcon(context))
            (folderItem.findViewById<View>(R.id.pocket_folder_row_folder_title) as TextView).text =
                folder.title
            folderItem.setOnLongClickListener { v: View? ->
                editFolder(i)
                collapse()
                true
            }
            val appContainer =
                ViewCompat.requireViewById<LinearLayout>(folderItem, R.id.pocket_folder_row_app_container)
            val folderSize = folders[i].shortcutApps.size
            for (j in 0 until folderSize) {
                val app = folders[i].shortcutApps.get(j)
                val appView = inflater.inflate(R.layout.pocket_app_view, appContainer, false) as ImageView
                appView.setImageBitmap(app.getIcon(context))
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
                    appContainer.addView(ViewUtils.createFillerView(context, betweenItemMargin))
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
            context,
            folders[row],
            false,
            object : FolderEditingBottomSheet.Callback {
                override fun onFolderSaved(
                    title: String,
                    iconPackage: String,
                    iconDrawable: String,
                    reorderedApps: List<SwipeApp>
                ) {
                    folders[row].title = title
                    folders[row].setDrawable(iconDrawable, iconPackage)
                    folders[row].replaceApps(reorderedApps)
                    DatabaseEditor.get().saveGestureFavorites(folders)
                    rebind()
                }

                override fun onFolderDeleted() {
                    folders.removeAt(row)
                    DatabaseEditor.get().saveGestureFavorites(folders)
                    rebind()
                }
            })
    }

    init {
        AttributeApplier.applyDensity(this, context)
        idleView.setOnClickListener {
            if (isExpanded) {
                collapse()
            } else {
                expand()
            }
        }
        rebind()
    }

    companion object {
        const val scaleDelta = 0.1F
        private const val animationDuration = 200L
    }
}