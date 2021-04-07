package com.inipage.homelylauncher.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.security.InvalidParameterException;

public class AttributeApplier {

    public static void applyDensity(Object source, Context paramSource) {
        for (Field field : source.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(SizeValAttribute.class)) {
                applyValAttribute(field, source, paramSource);
            } else if (field.isAnnotationPresent(SizeDimenAttribute.class)) {
                applyDimenAttribute(field, source, paramSource);
            } else if (field.isAnnotationPresent(ColorAttribute.class)) {
                applyColorAttribute(field, source, paramSource);
            }
        }
    }

    private static void applyValAttribute(Field field, Object source, Context context) {
        @Nullable final SizeValAttribute attribute = field.getAnnotation(SizeValAttribute.class);
        if (attribute == null) {
            return;
        }
        final SizeValAttribute.AttributeType type = attribute.attrType();
        if (field.getType() != Float.TYPE && field.getType() != Integer.TYPE) {
            throw new InvalidParameterException(
                "The SizeValAttribute annotation must be applied to a float or integer!");
        }

        try {

            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            field.setAccessible(true);
            float value = attribute.value();
            float newValue = TypedValue.applyDimension(
                type == SizeValAttribute.AttributeType.SP ? TypedValue.COMPLEX_UNIT_SP :
                (
                    type == SizeValAttribute.AttributeType.DIP ? TypedValue.COMPLEX_UNIT_DIP :
                    TypedValue.COMPLEX_UNIT_IN),
                value, metrics);
            if (field.getType() == Float.TYPE) {
                field.setFloat(source, newValue);
            } else {
                field.setInt(source, (int) newValue);
            }
        } catch (Exception ignored) {
        }
    }


    private static void applyDimenAttribute(Field field, Object source, Context paramSource) {
        @Nullable final SizeDimenAttribute attribute =
            field.getAnnotation(SizeDimenAttribute.class);
        if (attribute == null) {
            return;
        }
        if (field.getType() != Float.TYPE && field.getType() != Integer.TYPE) {
            throw new InvalidParameterException(
                "The SizeDimenAttribute annotation must be applied to a float or integer!");
        }

        try {
            field.setAccessible(true);
            float newValue = paramSource.getResources().getDimension(attribute.value());
            if (field.getType() == Float.TYPE) {
                field.setFloat(source, newValue);
            } else {
                field.setInt(source, (int) newValue);
            }
        } catch (Exception ignored) {
        }
    }


    private static void applyColorAttribute(Field field, Object source, Context paramSource) {
        @Nullable final ColorAttribute attribute =
            field.getAnnotation(ColorAttribute.class);
        if (attribute == null) {
            return;
        }
        if (field.getType() != Integer.TYPE) {
            throw new InvalidParameterException(
                "The ColorAttribute annotation must be applied to a float or integer!");
        }

        try {
            field.setAccessible(true);
            float newValue = paramSource.getResources().getColor(attribute.value());
            field.setInt(source, (int) newValue);
        } catch (Exception ignored) {
        }
    }

    public static int intValue() {
        return 0;
    }

    public static float floatValue() {
        return 0F;
    }
}
