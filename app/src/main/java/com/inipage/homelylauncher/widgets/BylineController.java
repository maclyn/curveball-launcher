package com.inipage.homelylauncher.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.TextAppearanceSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.inipage.homelylauncher.R;
import com.inipage.homelylauncher.utils.AlarmUtils;
import com.inipage.homelylauncher.utils.AttributeApplier;
import com.inipage.homelylauncher.utils.CalendarUtils;
import com.inipage.homelylauncher.utils.SizeDimenAttribute;
import com.inipage.homelylauncher.utils.weather.WeatherController;
import com.inipage.homelylauncher.utils.weather.model.CleanedUpWeatherModel;
import com.inipage.homelylauncher.utils.weather.model.LTSForecastModel;

import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import static com.inipage.homelylauncher.utils.AttributeApplier.intValue;

public class BylineController implements WeatherController.WeatherPresenter {

    private static final SimpleDateFormat CALENDAR_FORMAT =
        new SimpleDateFormat("EEEE, MMMM d", Locale.US);
    private static final SimpleDateFormat TIME_FORMAT =
        new SimpleDateFormat("h:mm aa", Locale.US);
    private final TextView mContainer;
    private final Context mContext;
    private final float mIconSize;
    @SizeDimenAttribute(R.dimen.byline_text_size)
    private final int mTextSize = intValue();
    // Weather is a delayed fetch
    private boolean mFetchedWeather = false;
    @Nullable
    private String mWeatherString = null;
    private int mWeatherRes = -1;

    public BylineController(TextView container) {
        AttributeApplier.applyDensity(this, container.getContext());
        mContainer = container;
        mContext = container.getContext();
        Paint p = new Paint();
        p.setTextSize(mTextSize);
        Paint.FontMetrics fontMetrics = p.getFontMetrics();
        float fontSizeHeight = fontMetrics.descent - fontMetrics.ascent;
        mIconSize = fontSizeHeight / 1.3F;
        render();
        WeatherController.requestWeather(mContext, this);
    }

    public void render() {
        mContainer.setText(R.string.loading);
        mContainer.setLinksClickable(true);
        mContainer.setClickable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mContainer.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);
        }
        mContainer.setMovementMethod(LinkMovementMethod.getInstance());

        final StringBuilder bylineBuilder = new StringBuilder();

        // Day of week + calendar events
        Date currentDate = new Date();
        bylineBuilder.append("It is X ");
        final int calendarIndex = bylineBuilder.length() - 2;
        bylineBuilder.append(CALENDAR_FORMAT.format(currentDate));
        bylineBuilder.append(getDateSuffix(currentDate));
        final int calendarIndexEnd = bylineBuilder.length();
        int eventIndex = -1;
        int eventIndexEnd = -1;
        @Nullable final CalendarUtils.Event event = CalendarUtils.findRelevantEvent(mContext);
        if (event != null) {
            bylineBuilder.append(", and you have X ");
            eventIndex = bylineBuilder.length() - 2;
            bylineBuilder.append(event.getTitle());
            if (event.getAllDay()) {
                bylineBuilder.append(" all day");
            } else {
                bylineBuilder.append(" at ");
                bylineBuilder.append(TIME_FORMAT.format(new Date(event.getStart())));
            }
            eventIndexEnd = bylineBuilder.length();
        }
        bylineBuilder.append(". ");

        // Weather
        int weatherIndex = -1;
        int weatherIndexEnd = -1;
        if (mFetchedWeather) {
            bylineBuilder.append("The weather is X ");
            weatherIndex = bylineBuilder.length() - 2;
            bylineBuilder.append(mWeatherString);
            weatherIndexEnd = bylineBuilder.length();
            bylineBuilder.append(". ");
        }

        // Alarm
        int alarmIndex = -1;
        int alarmIndexEnd = -1;
        if (AlarmUtils.hasAlarm(mContext)) {
            bylineBuilder.append("You have an X ");
            alarmIndex = bylineBuilder.length() - 2;
            bylineBuilder.append("alarm set for ");
            bylineBuilder.append(AlarmUtils.getNextAlarmTime(mContext));
            alarmIndexEnd = bylineBuilder.length();
            bylineBuilder.append(". ");
        }

        SpannableString spannableString = new SpannableString(bylineBuilder.toString().trim());
        applyClickable(
            spannableString,
            () -> {
            },
            false,
            calendarIndex,
            calendarIndexEnd,
            R.drawable.ic_outline_today_24);
        if (weatherIndex != -1) {
            applyClickable(
                spannableString,
                () -> {
                },
                true,
                weatherIndex,
                weatherIndexEnd,
                mWeatherRes);
        }
        if (alarmIndex != -1) {
            applyClickable(
                spannableString,
                () -> {
                    try {
                        PendingIntent alarmIntent = AlarmUtils.getAlarmIntent(mContext);
                        if (alarmIntent != null) {
                            alarmIntent.send();
                        }
                    } catch (PendingIntent.CanceledException ignored) {
                    }
                },
                false,
                alarmIndex,
                alarmIndexEnd,
                R.drawable.ic_outline_alarm_24);
        }
        if (eventIndex != -1) {
            applyClickable(
                spannableString,
                () -> CalendarUtils.launchEvent(mContext, event.getID()),
                false,
                eventIndex,
                eventIndexEnd,
                R.drawable.ic_outline_event_24);
        }
        mContainer.setText(spannableString);
    }

    // Wtf JDK
    // https://stackoverflow.com/questions/4011075/how-do-you-format-the-day-of-the-month-to-say-11th-21st-or-23rd-ordinal
    private String getDateSuffix(Date date) {
        final Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(date);
        final int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        if (dayOfMonth >= 11 && dayOfMonth <= 13) {
            return "th";
        }
        switch (dayOfMonth % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
        }
        return "th";
    }

    public void applyClickable(
        SpannableString spannable,
        Runnable runnable,
        boolean cropIn,
        int startIndex,
        int endIndex,
        int drawableId) {

        spannable.setSpan(
            new DrawableIdSpan(mContext, drawableId, (int) mIconSize, cropIn),
            startIndex,
            startIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(
            new TextAppearanceSpan(mContext, R.style.BylineStyle_Bolded),
            startIndex,
            endIndex,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                runnable.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setUnderlineText(false);
                ds.setColor(Color.WHITE);
            }
        }, startIndex, endIndex, Spanned.SPAN_POINT_POINT);
    }

    @Override
    public void requestLocationPermission() {
    }

    @Override
    public void onWeatherFound(LTSForecastModel weather) {
        mFetchedWeather = true;
        CleanedUpWeatherModel cModel =
            CleanedUpWeatherModel.parseFromLTSForecastModel(weather, mContext);
        final String condition = cModel.getCondition();
        final String temperature = cModel.getCurrentTemp();
        mWeatherString = String.format("%1$s and %2$s", temperature, condition);
        // mWeatherRes = cModel.getResourceId();
        mWeatherRes = R.drawable.ic_delete_white_48dp;
        render();
    }

    @Override
    public void onFetchFailure() {
    }

    private static class DrawableIdSpan extends DynamicDrawableSpan {

        private final Context mContext;
        private final int mResourceId;
        private final int mIconSize;
        private final boolean mCroppingIn;

        public DrawableIdSpan(
            Context context,
            @DrawableRes int resourceId,
            int iconSize,
            boolean croppingIn) {
            mContext = context;
            mResourceId = resourceId;
            mIconSize = iconSize;
            mCroppingIn = croppingIn;
        }

        @Override
        public void draw(
            @NonNull Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            @NonNull Paint paint) {
            canvas.save();
            Drawable d = getDrawable();
            final int yOffset = mIconSize / 8;
            if (mCroppingIn) {
                d.setBounds(
                    (int) (x - (mIconSize / 2F)),
                    (int) (y - (mIconSize * 1.5F) + yOffset),
                    (int) (x + (mIconSize * 1.5)),
                    (int) (y + mIconSize * 0.5F) + yOffset);
            } else {
                d.setBounds((int) x, y - mIconSize + yOffset, (int) x + mIconSize, y + yOffset);
            }
            d.draw(canvas);
            canvas.restore();
        }

        @Override
        public Drawable getDrawable() {
            Drawable d = mContext.getDrawable(mResourceId);
            d.setBounds(0, 0, mIconSize, mIconSize);
            return d;
        }
    }
}
