package com.inipage.homelylauncher.pocket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.grid.AppViewHolder;
import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.views.DecorViewDragger;

import java.util.List;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class PocketControllerDropView extends View implements DecorViewDragger.TargetedDragAwareComponent {

    private final static int SELECTED_IDX_UNSET = -1;
    private final Paint mPaint;
    private final Rect mSrcRect;
    private final RectF mDstRect;
    @SizeDimenAttribute(R.dimen.contextual_dock_height)
    int viewHeight = intValue();
    @SizeDimenAttribute(R.dimen.pocket_drop_view_internal_padding)
    int internalPadding = intValue();
    @Nullable
    AppViewHolder mDragTarget;
    @Nullable
    private Host mHost;
    @Nullable
    private ImmutableList<Pair<Float, Float>> mRanges;
    private int mSelectedIdx = SELECTED_IDX_UNSET;

    public PocketControllerDropView(Context context) {
        this(context, null);
    }

    public PocketControllerDropView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PocketControllerDropView(
        Context context,
        @Nullable AttributeSet attrs,
        int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, context);
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.FILL);
        mSrcRect = new Rect();
        mDstRect = new RectF();
        if (isInEditMode()) {
            attachHost(
                new Host() {
                    private final List<SwipeFolder> mFolders =
                        ImmutableList.of(
                            new SwipeFolder(
                                "Mock 1",
                                getClass().getPackage().toString(),
                                "ic_folder_white_48dp",
                                ImmutableList.of()),
                            new SwipeFolder(
                                "Mock 2",
                                getClass().getPackage().toString(),
                                "ic_info_white_48dp",
                                ImmutableList.of()));

                    @Override
                    public List<SwipeFolder> getFolders() {
                        return mFolders;
                    }
                });
        }
    }

    public void attachHost(Host host) {
        mHost = host;
        DecorViewDragger.get(getContext()).registerDragAwareComponent(this);
        recalculateZones();
        invalidate();
    }

    private void recalculateZones() {
        if (mHost == null || mHost.getFolders() == null) {
            mRanges = ImmutableList.of();
            return;
        }
        final int count = mHost.getFolders().size() + 1;
        final ImmutableList.Builder<Pair<Float, Float>> builder = ImmutableList.builder();
        final float sectionSize = getWidth() / (float) count;
        for (int i = 0; i < count; i++) {
            builder.add(new Pair<>(i * sectionSize, (i + 1) * sectionSize));
        }
        mRanges = builder.build();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mHost == null || mHost.getFolders() == null || mRanges == null) {
            return;
        }

        final List<SwipeFolder> folders = mHost.getFolders();
        final int count = folders.size() + 1;
        final float sectionWidth = (float) getWidth() / count;
        final float height = getHeight();
        final float diameter = Math.min(sectionWidth, height - (internalPadding * 2));
        final float radius = diameter / 2F;
        final float appWidthHalfSize = (float) Math.sqrt(Math.pow(radius, 2) / 2);
        final float folderWidthHalfSize = appWidthHalfSize * 0.8F;
        for (int folderIdx = 0; folderIdx < count; folderIdx++) {
            final boolean isAdditionIdx = folderIdx == folders.size();
            @Nullable final SwipeFolder folder = isAdditionIdx ? null : folders.get(folderIdx);
            final Pair<Float, Float> range = mRanges.get(folderIdx);
            final float centerX = range.first + (sectionWidth / 2F);
            final float centerY = getHeight() / 2F;

            // Draw pale circle
            mPaint.setAlpha(127);
            canvas.drawCircle(centerX, centerY, radius, mPaint);

            // Draw folder icon
            final Bitmap folderBitmap =
                IconCacheSync
                    .getInstance(getContext())
                    .getNamedResource(
                        isAdditionIdx ? Constants.PACKAGE : folder.getDrawablePackage(),
                        isAdditionIdx
                        ? "ic_add_circle_outline_white_48dp"
                        : folder.getDrawableName());
            mPaint.setAlpha(255);
            mSrcRect.set(0, 0, folderBitmap.getWidth(), folderBitmap.getHeight());
            mDstRect.set(
                centerX - folderWidthHalfSize,
                centerY - folderWidthHalfSize,
                centerX + folderWidthHalfSize,
                centerY + folderWidthHalfSize);
            canvas.drawBitmap(folderBitmap, mSrcRect, mDstRect, mPaint);

            // Draw app on top, potentially
            if (folderIdx == mSelectedIdx && mDragTarget != null) {
                final Bitmap appBitmap = IconCacheSync.getInstance(getContext())
                    .getActivityIcon(
                        mDragTarget.getAppIcon().getPackageName(),
                        mDragTarget.getAppIcon()
                            .getActivityName());
                mSrcRect.set(0, 0, appBitmap.getWidth(), appBitmap.getHeight());
                mDstRect.set(
                    centerX - appWidthHalfSize,
                    centerY - appWidthHalfSize,
                    centerX + appWidthHalfSize,
                    centerY + appWidthHalfSize);
                canvas.drawBitmap(appBitmap, mSrcRect, mDstRect, mPaint);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            recalculateZones();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int measuredHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            measuredHeight > viewHeight ? viewHeight : measuredHeight);
    }

    @Nullable
    @Override
    public View getDragAwareTargetView() {
        return this;
    }

    @Override
    public void onDrag(View v, DecorViewDragger.DragEvent event) {
        if (!(event.getLocalState() instanceof AppViewHolder)) {
            return;
        }
        final AppViewHolder appViewHolder = (AppViewHolder) event.getLocalState();
        if (appViewHolder.getAppIcon() == null ||
            mHost == null ||
            mRanges == null) {
            return;
        }

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                mDragTarget = (AppViewHolder) event.getLocalState();
                mSelectedIdx = SELECTED_IDX_UNSET;
                mHost.onDragStarted();
                invalidate();
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
                final int x = event.getRawXOffsetByView(this);
                for (int i = 0; i < mRanges.size(); i++) {
                    Pair<Float, Float> range = mRanges.get(i);
                    if (x >= range.first && x <= range.second) {
                        if (mSelectedIdx != i) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            mSelectedIdx = i;
                            invalidate();
                        }
                        break;
                    }
                }
                break;
            case DragEvent.ACTION_DROP:
                if (mSelectedIdx != SELECTED_IDX_UNSET) {
                    if (mSelectedIdx == mHost.getFolders().size()) {
                        mHost.onNewFolderRequested(mDragTarget.getAppIcon());
                    } else {
                        mHost.onAppAddedToFolder(mSelectedIdx, mDragTarget.getAppIcon());
                    }
                }
                break;
            case DragEvent.ACTION_DRAG_EXITED:
                mSelectedIdx = SELECTED_IDX_UNSET;
                invalidate();
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                mDragTarget = null;
                mHost.onDragEnded();
                invalidate();
                break;
        }
    }

    public interface Host {
        default void onDragStarted() {
        }

        default void onDragEnded() {
        }

        default void onAppAddedToFolder(int folderIdx, ApplicationIcon app) {
        }

        default void onNewFolderRequested(ApplicationIcon app) {
        }

        List<SwipeFolder> getFolders();
    }
}
