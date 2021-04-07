package com.inipage.homelylauncher.pocket;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FlingAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.dock.ForwardingContainer;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.persistence.DatabaseEditor;
import com.inipage.homelylauncher.swipefolders.FolderEditingBottomSheet;
import com.inipage.homelylauncher.swipefolders.ReorderFolderBottomSheet;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.DebugLogUtils;
import com.inipage.homelylauncher.utils.InstalledAppUtils;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.SizeValAttribute;
import com.inipage.homelylauncher.utils.ViewUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import butterknife.BindView;
import butterknife.ButterKnife;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Renders a pocket of folders at the bottom of the homescreen. This pocket animates in with an
 * expand animation that's handled by [how?]. It also connects to
 */
public class PocketController implements PocketControllerDropView.Host, ForwardingContainer.ForwardingListener {

    public final static float SCALE_DELTA = 0.1F;
    private final Context mContext;
    private final Host mHost;
    private final ForwardingContainer mContainer;
    private final View mDockView;
    private final PocketControllerDropView mDropView;
    private final PocketControllerIdleView mIdleView;
    @SizeDimenAttribute(R.dimen.actuation_distance)
    int actuationDistance;
    @SizeDimenAttribute(R.dimen.home_activity_margin)
    int homeMargin;
    @SizeValAttribute(48)
    int appViewSize;
    @SizeValAttribute(8)
    int betweenItemMargin;
    @BindView(R.id.pocket_folder_container)
    LinearLayout folderContainer;
    @BindView(R.id.pocket_folder_app_container)
    LinearLayout appContainer;
    @BindView(R.id.pocket_top_scrim)
    View topScrim;
    @BindView(R.id.pocket_bottom_scrim)
    View bottomScrim;
    private boolean mIsSwiping;
    private boolean mIsExpanded;
    private VelocityTracker mVelocityTracker;
    private List<SwipeFolder> mFolders;

    public PocketController(
        Context context,
        Host host,
        ForwardingContainer container,
        View dockView,
        PocketControllerDropView dropView,
        PocketControllerIdleView idleView) {
        AttributeApplier.applyDensity(this, context);
        mContext = context;
        mHost = host;
        mContainer = container;
        mDockView = dockView;
        mDropView = dropView;
        mIdleView = idleView;
        mFolders = DatabaseEditor.get().getGestureFavorites();
        mIdleView.setOnClickListener(v -> {
            if (isExpanded()) {
                collapse();
            } else {
                expand();
            }
        });
        final LayoutInflater inflater = LayoutInflater.from(context);
        ButterKnife.bind(this, inflater.inflate(R.layout.pocket_container_view, mContainer, true));
        rebind();
    }

    public void applyScrims(int topScrimSize, int bottomScrimSize) {
        ViewUtils.setHeight(topScrim, topScrimSize);
        ViewUtils.setHeight(bottomScrim, bottomScrimSize);
    }

    public void collapse() {
        if (!isExpanded()) {
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(1, 0);
        animator.addUpdateListener(animation -> {
            setPercentExpanded((Float) animation.getAnimatedValue());
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                commitCollapse();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        animator.start();
    }

    public void expand() {
        if (isExpanded()) {
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(animation -> {
            setPercentExpanded((Float) animation.getAnimatedValue());
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                commitExpand();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        animator.start();
    }

    public void editFolderOrder() {
        ReorderFolderBottomSheet.show(mContext, mFolders, reorderedFolders -> {
            mFolders = reorderedFolders;
            DatabaseEditor.get().saveGestureFavorites(mFolders);
            rebind();
        });
    }

    @Override
    public void onForwardEvent(MotionEvent event, float deltaY) {
        log("deltaY = " + deltaY);

        // deltaY = mStartY - event.getRawY()
        // < 0 = going down screen
        // > 0 = = swiping up on screen
        float percentExpanded = deltaY / actuationDistance; // Works for expanding
        if (isExpanded()) { // Collapsing
            percentExpanded = 1 - (-deltaY / actuationDistance);
        }
        if (percentExpanded < 0) {
            percentExpanded = 0;
        } else if (percentExpanded > 1) {
            percentExpanded = 1;
        }
        setPercentExpanded(percentExpanded);
        log("Percent expanded ", String.valueOf(deltaY));

        if (!mIsSwiping) {
            mIsSwiping = true;
            mVelocityTracker = VelocityTracker.obtain();
            mContainer.setVisibility(VISIBLE);
            mContainer.setFocusableInTouchMode(true);
        }
        mVelocityTracker.addMovement(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsSwiping = false;

                if (percentExpanded <= 0) {
                    mVelocityTracker.recycle();
                    commitCollapse();
                    return;
                } else if (percentExpanded >= 1) {
                    mVelocityTracker.recycle();
                    commitExpand();
                    return;
                }

                final Context context = mContainer.getContext();
                mVelocityTracker.computeCurrentVelocity(
                    1000, // px/s
                    ViewConfiguration.get(context).getScaledMaximumFlingVelocity());
                final float speed = mVelocityTracker.getYVelocity();
                // Though deltaY will be > 0, speed is negative in an upwards flings
                final boolean flingIsExpand = speed < 0;
                final float startPoint = mContainer.getTranslationY();
                final float startVelocityScalar =
                    Math.max(
                        Math.abs(speed),
                        context.getResources().getDimension(R.dimen.min_dot_start_velocity_dp_s));
                final float startVelocity = startVelocityScalar * (flingIsExpand ? 1 : -1);
                float minValue = 0;
                float maxValue;
                if (flingIsExpand) {
                    maxValue = startPoint;
                } else {
                    maxValue = actuationDistance;
                }
                final FlingAnimation flingAnimation =
                    new FlingAnimation(
                        this, new FloatPropertyCompat<PocketController>("translationY") {

                        @Override
                        public float getValue(PocketController object) {
                            return mContainer.getTranslationY();
                        }

                        @Override
                        public void setValue(PocketController object, float value) {
                            log("Interpolating to ", +value);
                            final float percentExpanded = 1 - (value / actuationDistance);
                            log("As a percent expanded, that's ", percentExpanded);
                            setPercentExpanded(1 - (value / actuationDistance));
                        }
                    });
                flingAnimation.setMinValue(minValue);
                flingAnimation.setMaxValue(maxValue);
                flingAnimation.setStartVelocity(startVelocityScalar * (flingIsExpand ? -1 : 1));
                flingAnimation.setFriction(ViewConfiguration.getScrollFriction());
                flingAnimation.addEndListener((animation, canceled, value, velocity) -> {
                    mIsExpanded = flingIsExpand;
                    if (flingIsExpand) {
                        commitExpand();
                    } else {
                        commitCollapse();
                    }
                });
                log(
                    "About to start animation: velocity=",
                    startVelocity,
                    ", isExpand=",
                    flingIsExpand, ", min=",
                    minValue,
                    ", max=",
                    maxValue,
                    ", startPoint = ",
                    startPoint);
                flingAnimation.start();
                break;
        }
    }

    @Override
    public boolean shouldHandleEvent(float deltaY) {
        log("Should handle event for deltaY=" + deltaY);
        if (isExpanded()) {
            return deltaY < 0; // swipe down
        } else {
            return deltaY > 0; // swipe up
        }
    }

    private void log(Object... out) {
        DebugLogUtils.needle(
            DebugLogUtils.TAG_POCKET_ANIMATION,
            Arrays.stream(out).map(String::valueOf).collect(Collectors.joining()));
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    private void setPercentExpanded(float percent) {
        mContainer.setAlpha(percent);
        mContainer.setTranslationY(actuationDistance - (percent * actuationDistance));
        mContainer.setScaleX(1 - ((1 - percent) * SCALE_DELTA));
        mContainer.setScaleY(1 - ((1 - percent) * SCALE_DELTA));
        mIdleView.setRotation(percent);
        mHost.onPartiallyExpandedPocket(percent);
    }

    private void commitCollapse() {
        setPercentExpanded(0);
        mContainer.setVisibility(GONE);
        mContainer.setFocusableInTouchMode(false);
        mIsExpanded = mIsSwiping = false;
        mHost.onPocketCollapsed();
        if (!mFolders.isEmpty()) {
            setAppContainer(0);
        }
    }

    private void commitExpand() {
        setPercentExpanded(1);
        mContainer.setVisibility(VISIBLE);
        mContainer.setFocusableInTouchMode(true);
        mIsExpanded = true;
        mIsSwiping = false;
        mHost.onPocketExpanded();
    }

    private void setAppContainer(int selectedFolder) {
        for (int i = 1; i <= mFolders.size(); i++) {
            folderContainer.getChildAt(i).setAlpha(selectedFolder == (i - 1) ? 1F : 0.8F);
        }
        appContainer.removeAllViews();
        appContainer.addView(ViewUtils.createFillerWidthView(mContext, homeMargin));
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        for (SwipeApp app : mFolders.get(selectedFolder).getShortcutApps()) {
            final ImageView view =
                (ImageView) inflater.inflate(R.layout.pocket_app_view, appContainer, false);
            view.setImageBitmap(app.getIcon(mContext));
            view.setOnClickListener(v -> {
                InstalledAppUtils.launchApp(
                    v, app.getComponent().first, app.getComponent().second);
            });
            final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(appViewSize, appViewSize);
            params.leftMargin = params.rightMargin = betweenItemMargin;
            params.gravity = Gravity.CENTER_VERTICAL;
            appContainer.addView(view, params);
        }
        appContainer.addView(ViewUtils.createFillerWidthView(mContext, homeMargin));
    }

    @Override
    public void onDragStarted() {
        mDockView.animate().alpha(0).start();
        mIdleView.animate().alpha(0).start();
        mDropView.setAlpha(0F);
        mDropView.setVisibility(VISIBLE);
        mDropView
            .animate()
            .alpha(1)
            .setListener(ViewUtils.onEndListener(() -> {
                mDockView.setAlpha(0F);
                mIdleView.setAlpha(0F);
                mDropView.setAlpha(1F);
            }))
            .start();
    }

    @Override
    public void onDragEnded() {
        mDockView.animate().alpha(1).start();
        mIdleView.animate().alpha(1).start();
        mDropView
            .animate()
            .alpha(0)
            .setListener(ViewUtils.onEndListener(() -> {
                mDockView.setAlpha(1F);
                mIdleView.setAlpha(1F);
                mDropView.setVisibility(GONE);
            }))
            .start();
    }

    @Override
    public void onAppAddedToFolder(int folderIdx, ApplicationIcon app) {
        mFolders.get(folderIdx).addApp(new Pair<>(app.getPackageName(), app.getActivityName()));
        DatabaseEditor.get().saveGestureFavorites(mFolders);
        mIdleView.setDotCount(mFolders.size());
        mHost.clearActiveDragTarget();
    }

    @Override
    public void onNewFolderRequested(ApplicationIcon ai) {
        final SwipeFolder newFolder = new SwipeFolder(
            "",
            Constants.PACKAGE,
            Constants.DEFAULT_FOLDER_ICON,
            ImmutableList.of(
                new SwipeApp(ai.getPackageName(), ai.getActivityName())));
        FolderEditingBottomSheet.show(
            mContext,
            newFolder,
            true,
            new FolderEditingBottomSheet.Callback() {
                @Override
                public void onFolderSaved(
                    String title,
                    String iconPackage,
                    String iconDrawable,
                    List<SwipeApp> reorderedApps) {
                    newFolder.replaceApps(reorderedApps);
                    newFolder.setTitle(title);
                    newFolder.setDrawable(iconDrawable, iconPackage);
                    mFolders.add(newFolder);
                    DatabaseEditor.get().saveGestureFavorites(mFolders);
                    rebind();
                }

                @Override
                public void onFolderDeleted() {
                }
            });
    }

    @Override
    public List<SwipeFolder> getFolders() {
        return mFolders;
    }

    private void rebind() {
        mDropView.attachHost(this);
        mIdleView.setDotCount(mFolders.size());

        final LayoutInflater inflater = LayoutInflater.from(mContext);
        folderContainer.removeAllViews();
        folderContainer.addView(ViewUtils.createFillerWidthView(mContext, homeMargin));
        for (int i = 0; i < mFolders.size(); i++) {
            final SwipeFolder folder = mFolders.get(i);
            final View folderItem =
                inflater.inflate(R.layout.pocket_folder_view, folderContainer, false);
            ((ImageView) folderItem.findViewById(R.id.pocket_folder_icon_view))
                .setImageBitmap(folder.getIcon(mContext));
            ((TextView) folderItem.findViewById(R.id.pocket_folder_text_view))
                .setText(folder.getTitle());
            final int folderIdx = i;
            folderItem.setOnClickListener(v -> {
                setAppContainer(folderIdx);
            });
            folderItem.setOnLongClickListener(v -> {
                editFolder(folderIdx);
                collapse();
                return true;
            });
            final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.leftMargin = params.rightMargin = betweenItemMargin;
            params.gravity = Gravity.CENTER;
            folderContainer.addView(folderItem, params);
        }
        folderContainer.addView(ViewUtils.createFillerWidthView(mContext, homeMargin));
        if (!mFolders.isEmpty()) {
            setAppContainer(0);
        }
    }

    private void editFolder(final int row) {
        FolderEditingBottomSheet.show(
            mContext,
            mFolders.get(row),
            false,
            new FolderEditingBottomSheet.Callback() {
                @Override
                public void onFolderSaved(
                    String title,
                    String iconPackage,
                    String iconDrawable,
                    List<SwipeApp> reorderedApps) {
                    mFolders.get(row).setTitle(title);
                    mFolders.get(row).setDrawable(iconDrawable, iconPackage);
                    mFolders.get(row).replaceApps(reorderedApps);
                    DatabaseEditor.get().saveGestureFavorites(mFolders);
                    rebind();
                }

                @Override
                public void onFolderDeleted() {
                    mFolders.remove(row);
                    DatabaseEditor.get().saveGestureFavorites(mFolders);
                    rebind();
                }
            });
    }

    public interface Host {
        void onPartiallyExpandedPocket(float percent);

        void onPocketExpanded();

        void onPocketCollapsed();

        void clearActiveDragTarget();
    }
}
