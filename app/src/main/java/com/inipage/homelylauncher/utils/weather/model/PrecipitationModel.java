package com.inipage.homelylauncher.utils.weather.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict = false, name = "precipitation")
public class PrecipitationModel {
    @Attribute
    float value;
}
