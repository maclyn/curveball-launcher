package com.inipage.homelylauncher.drawer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.inipage.homelylauncher.model.ApplicationIcon;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class for rendering an A-Z scrollbar.
 */
public class FastScroller extends View {

    private final Paint mPaint = new Paint();
    private final Rect mRect = new Rect();
    private final int[] mLocation = new int[2];
    private List<SectionElement> mMappings;
    private RecyclerView mScrollView;
    private int mSelectedFolder;

    public FastScroller(Context context) {
        this(context, null);
    }

    public FastScroller(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FastScroller(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        AttributeApplier.applyDensity(this, context);
        mSelectedFolder = -1;
        mPaint.setColor(Color.WHITE);
        mPaint.setTypeface(Typeface.MONOSPACE);
        mPaint.setAntiAlias(true);

        if (isInEditMode()) {
            List<ApplicationIcon> dummyList = new ArrayList<>();
            dummyList.add(new ApplicationIcon("A"));
            dummyList.add(new ApplicationIcon("3"));
            setup(dummyList, null);
        }
    }

    public void setup(List<ApplicationIcon> list, RecyclerView scrollView) {
        this.mScrollView = scrollView;
        HashMap<Character, SectionElement> sectionMap = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            char initial = list.get(i).getScrollableField();
            SectionElement matchedElement;
            if (sectionMap.containsKey(initial)) {
                matchedElement = sectionMap.get(initial);
            } else {
                matchedElement = new SectionElement(i, initial);
                sectionMap.put(initial, matchedElement);
            }
            Objects.requireNonNull(matchedElement).increment();
        }
        mMappings = new ArrayList<>(sectionMap.size());
        Set<Map.Entry<Character, SectionElement>> entries = sectionMap.entrySet();
        for (Map.Entry<Character, SectionElement> entry : entries) {
            mMappings.add(entry.getValue());
        }
        mMappings.sort((lhs, rhs) ->
                           FastScrollable.getCharComparator()
                               .compare(lhs.getName(), rhs.getName()));
        for (int i = 0; i < mMappings.size(); i++) {
            float startPercent = ((float) i) / mMappings.size();
            float endPercent = ((float) i + 1) / mMappings.size();
            mMappings.get(i).setRange(startPercent, endPercent);
            mMappings.get(i).setIndex(i);
        }
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mMappings == null) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
            case MotionEvent.ACTION_MOVE:
                getLocationOnScreen(mLocation);
                final float startX = mLocation[0];
                final float rawX = event.getRawX();
                if (rawX > (getWidth() + startX) || rawX < mLocation[0]) {
                    return true;
                }
                final float percentScroll = (rawX - mLocation[0]) / getWidth();
                int scrollerPosition = -1;
                int selectedFolder = -1;
                for (SectionElement se : mMappings) {
                    if (se.getStartZone() <= percentScroll && se.getEndZone() >= percentScroll) {
                        selectedFolder = se.getIndex();
                        scrollerPosition = se.getStart();
                    }
                }
                if (scrollerPosition != -1 && selectedFolder != mSelectedFolder) {
                    mSelectedFolder = selectedFolder;
                    mScrollView.smoothScrollToPosition(scrollerPosition);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mSelectedFolder = -1;
                invalidate();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMappings == null || mSelectedFolder >= mMappings.size()) {
            return;
        }
        final boolean hasSelection = mSelectedFolder != -1;
        final float letterSpaceWidth = ((float) getWidth()) / mMappings.size();
        final float maxHeight = getHeight() * 0.4F;
        final float middleHeight = getHeight() * 0.35F;
        final float defaultHeight = getHeight() * 0.3F;
        float xPos = 0;
        for (int i = 0; i < mMappings.size(); i++) {
            char toRender = mMappings.get(i).getName();
            float textHeight = defaultHeight;
            if (hasSelection) {
                if (mSelectedFolder == i) {
                    textHeight = maxHeight;
                } else if (mSelectedFolder - 1 == i || mSelectedFolder + 1 == i) {
                    textHeight = middleHeight;
                }
            }
            if (Constants.DEBUG_RENDER) {
                mPaint.setColor(i % 2 == 0 ? Color.RED : Color.BLUE);
                canvas.drawRect(xPos, 0, xPos + letterSpaceWidth, getHeight(), mPaint);
            }
            renderChar(canvas, toRender, xPos, textHeight, letterSpaceWidth);
            xPos += letterSpaceWidth;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Fill all available space when set to wrap_content
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }

    private void renderChar(Canvas c, char ch, float cellStart, float textSize, float cellSize) {
        String character = String.valueOf(ch);
        float cellCenter = cellStart + (cellSize / 2);
        mPaint.setTextSize(textSize);
        mPaint.setColor(Color.WHITE);
        mPaint.getTextBounds(character, 0, character.length(), mRect);
        c.drawText(
            character,
            cellCenter - (mRect.width() / 2F),
            getHeight() / 2F + (mRect.height() / 2F),
            mPaint);
    }

    static class SectionElement {
        char name;
        int index;
        int count;
        int start;
        float startZone;
        float endZone;

        SectionElement(int start, char name) {
            this.count = 0;
            this.start = start;
            this.name = name;
        }

        public void increment() {
            this.count++;
        }

        public void setRange(float start, float end) {
            this.startZone = start;
            this.endZone = end;
        }

        public char getName() {
            return name;
        }

        public int getStart() {
            return start;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public float getStartZone() {
            return startZone;
        }

        public float getEndZone() {
            return endZone;
        }
    }
}
