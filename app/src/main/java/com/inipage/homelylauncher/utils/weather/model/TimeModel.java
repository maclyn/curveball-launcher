package com.inipage.homelylauncher.utils.weather.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.util.Date;

@Root(strict = false, name = "time")
public class TimeModel {
    @Element
    LocationModel location;
    @Attribute
    private Date from;
    @Attribute
    private Date to;

    public Date getFrom() {
        return from;
    }

    public Date getTo() {
        return to;
    }

    public LocationModel getLocation() {
        return location;
    }
}
