package com.inipage.homelylauncher.utils.weather.model;

import androidx.annotation.Nullable;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict = false, name = "precipitation")
public class PrecipitationModel {
    @Attribute
    float value;

    @Attribute(required = false)
    @Nullable String unit;

    public float getValue() {
        return value;
    }

    @Nullable
    public String getUnit() {
        return unit;
    }
}
