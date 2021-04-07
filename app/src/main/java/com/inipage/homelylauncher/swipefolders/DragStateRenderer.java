package com.inipage.homelylauncher.swipefolders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Pair;
import android.view.View;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.caches.IconCacheSync;
import com.inipage.homelylauncher.model.SwipeApp;
import com.inipage.homelylauncher.model.SwipeFolder;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.SizeValAttribute;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;
import static com.inipage.homelylauncher.utils.SizeValAttribute.AttributeType.SP;

/**
 * Render the drag state.
 */
public class DragStateRenderer {

    private final int CREATE_NEW_FOLDER_RES = R.string.add_to_new_folder;
    private final int ADD_TO_FOLDER_STRING_RES = R.string.add_to_existing_folder;
    private final int CANNOT_ADD_TO_FOLDER_STRING_RES = R.string.cant_add_to_folder;

    private final float SELECTED_ALPHA = 1F;
    private final float PARTIALLY_SELECTED_ALPHA = 0.75F;
    private final Rect mRect = new Rect();
    private final RectF mRectF = new RectF();
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mFolderPaint = new Paint();
    private final Paint mFolderTextPaint = new Paint();
    private final Paint.FontMetrics mFontMetrics;

    // INTERNAL_PADDING, HORIZONTAL_PADDING, FOLDER_WIDTH are calculated elsewhere
    @SizeValAttribute(2)
    private final int FOLDER_ICON_PADDING = intValue();
    @SizeValAttribute(4)
    private final int FOLDER_EXTERNAL_PADDING = intValue();
    @SizeValAttribute(4)
    private final int FOLDER_CORNER_RADIUS_DP = intValue();
    @SizeValAttribute(attrType = SP, value = 14)
    private final int FOLDER_TEXT_SIZE_SP = intValue();
    @SizeValAttribute(32)
    private final int FOLDER_TEXT_CONTAINER_SIZE_DP = intValue();
    @SizeValAttribute(4)
    private final int FOLDER_TEXT_SIDE_PADDING = intValue();

    DragStateRenderer(Context context) {
        AttributeApplier.applyDensity(this, context);
        mBackgroundPaint.setAntiAlias(true);
        mFolderPaint.setAntiAlias(true);
        mFolderPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mFolderTextPaint.setAntiAlias(true);
        mFolderTextPaint.setColor(Color.WHITE);
        mFolderTextPaint.setTextSize(FOLDER_TEXT_SIZE_SP);
        mFontMetrics = mFolderTextPaint.getFontMetrics();
    }

    public void render(
        PerDragGestureValues dragValues,
        SwipeAttributes sharedAttrs,
        View view,
        Canvas c) {
        final int selectedFolderIdx = dragValues.getSelectedFolder();

        // Render selection text
        if (selectedFolderIdx != PerDragGestureValues.NO_ITEM_UNDER_DRAG) {
            final Pair<Float, Float> range =
                dragValues.getFolderRanges().get(dragValues.getSelectedFolder());
            final float centerPointX = range.first + ((range.second - range.first) / 2);
            final boolean isAdditionSelected =
                selectedFolderIdx == dragValues.getShortcuts().size();
            String message;
            if (isAdditionSelected) {
                message = view.getContext().getString(CREATE_NEW_FOLDER_RES);
            } else {
                final SwipeFolder selectedFolder =
                    dragValues.getShortcuts().get(selectedFolderIdx);
                final boolean isErrorState =
                    selectedFolder.getShortcutApps().size() >=
                        SwipeAttributes.MAXIMUM_ELEMENTS_PER_ROW;
                message = !isErrorState ?
                          view.getContext().getString(
                              ADD_TO_FOLDER_STRING_RES,
                              dragValues.getShortcutApp().getLabel(view.getContext()),
                              selectedFolder.getTitle()) :
                          view.getContext().getString(
                              CANNOT_ADD_TO_FOLDER_STRING_RES,
                              selectedFolder.getTitle());
            }


            final float textRegionBaseline = view.getHeight() - FOLDER_TEXT_CONTAINER_SIZE_DP;
            final float totalTextYSpace = FOLDER_TEXT_CONTAINER_SIZE_DP;
            mFolderTextPaint.getTextBounds(message, 0, message.length(), mRect);
            final int textWidth = mRect.width();
            final int textHeight = (int) (mFontMetrics.descent - mFontMetrics.ascent);
            final float textYBaseline =
                textRegionBaseline + (totalTextYSpace / 2F) + (textHeight / 2F);
            float textXStartPoint = centerPointX - (textWidth / 2F);
            // Left clamp
            if (textXStartPoint < FOLDER_TEXT_SIDE_PADDING) {
                textXStartPoint = FOLDER_TEXT_SIDE_PADDING;
                // Right clamp
            } else if (
                (textXStartPoint + textWidth) > (view.getWidth() - FOLDER_TEXT_SIDE_PADDING)) {
                textXStartPoint = view.getWidth() - FOLDER_TEXT_SIDE_PADDING - textWidth;
            }

            // Draw text
            c.drawText(message, textXStartPoint, textYBaseline, mFolderTextPaint);
        }

        // Folders can be at most $HEIGHT - $FOLDER_TEXT_CONTAINER_SIZE
        final float maxHeight =
            view.getHeight() - FOLDER_TEXT_CONTAINER_SIZE_DP - (FOLDER_EXTERNAL_PADDING * 2);
        final float folderWidth = dragValues.getFolderWidth() - (FOLDER_EXTERNAL_PADDING * 2);
        final float folderSize = Math.min(folderWidth, maxHeight);
        final float horizontalPadding = (folderWidth - folderSize) / 2F;
        final float verticalPadding =
            (view.getHeight() - FOLDER_TEXT_CONTAINER_SIZE_DP - folderSize) / 2F;
        final int folderCount = dragValues.getFolderRanges().size();
        for (int i = 0; i < folderCount; i++) {
            final int alpha =
                i == selectedFolderIdx ?
                SwipeAttributes.getAlphaFromFloat(1F, SELECTED_ALPHA) :
                SwipeAttributes.getAlphaFromFloat(1F, PARTIALLY_SELECTED_ALPHA);
            final Pair<Float, Float> range = dragValues.getFolderRanges().get(i);
            final float startX = range.first + horizontalPadding + FOLDER_EXTERNAL_PADDING;
            final float endX = range.second - horizontalPadding - FOLDER_EXTERNAL_PADDING;
            final float startY = verticalPadding;
            final float endY = verticalPadding + folderSize;
            final boolean isAdditionFolder = i == dragValues.getShortcuts().size();
            final int elementsInFolder = isAdditionFolder ?
                                         0 :
                                         dragValues.getShortcuts().get(i).getShortcutApps().size();
            final boolean canAddToFolder =
                elementsInFolder < SwipeAttributes.MAXIMUM_ELEMENTS_PER_ROW;
            mBackgroundPaint.setColor(
                isAdditionFolder ?
                Color.WHITE :
                dragValues.getShortcuts().get(i).getTint(view.getContext()));
            mBackgroundPaint.setAlpha(alpha);
            c.drawRoundRect(
                startX,
                startY,
                endX,
                endY,
                FOLDER_CORNER_RADIUS_DP,
                FOLDER_CORNER_RADIUS_DP,
                mBackgroundPaint);

            final float internalIconSize = (folderSize - (FOLDER_ICON_PADDING * 2F)) / 3F;
            final float appIconStartX = startX + FOLDER_ICON_PADDING;
            final float appIconStartY = startY + FOLDER_ICON_PADDING;
            if (!isAdditionFolder) {
                final SwipeFolder card = dragValues.getShortcuts().get(i);
                for (int j = 0; j < card.getShortcutApps().size(); j++) {
                    final SwipeApp app = card.getShortcutApps().get(j);
                    final int col = j % SwipeAttributes.MAXIMUM_ELEMENTS_PER_COLUMN;
                    final int row = (int) Math.floor(
                        j / SwipeAttributes.MAXIMUM_ELEMENTS_PER_COLUMN);
                    final float x = appIconStartX + (col * internalIconSize);
                    final float y = appIconStartY + (row * internalIconSize);
                    mRectF.set(x, y, x + internalIconSize, y + internalIconSize);
                    c.drawBitmap(
                        app.getIcon(view.getContext()),
                        null,
                        mRectF,
                        mFolderPaint);
                }
            }
            if (canAddToFolder && i == selectedFolderIdx) {
                // Render icon in the folder
                Bitmap appBitmap =
                    IconCacheSync.getInstance(view.getContext()).getActivityIcon(
                        dragValues.getAppViewHolder().getItem().getPackageName(),
                        dragValues.getAppViewHolder().getItem().getActivityName());
                final int col = elementsInFolder % SwipeAttributes.MAXIMUM_ELEMENTS_PER_COLUMN;
                final int row =
                    (int) Math.floor(
                        elementsInFolder / SwipeAttributes.MAXIMUM_ELEMENTS_PER_COLUMN);
                final float x = appIconStartX + (col * internalIconSize);
                final float y = appIconStartY + (row * internalIconSize);
                mRectF.set(x, y, x + internalIconSize, y + internalIconSize);
                c.drawBitmap(appBitmap, null, mRectF, mFolderPaint);
            }
        }
    }
}
