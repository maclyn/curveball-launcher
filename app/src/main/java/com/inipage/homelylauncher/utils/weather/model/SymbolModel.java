package com.inipage.homelylauncher.utils.weather.model;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict = false, name = "symbol")
public class SymbolModel {
    @Attribute
    String id;

    @Attribute
    String number;

    @Attribute
    String code;

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getCode() {
        return code;
    }
}
