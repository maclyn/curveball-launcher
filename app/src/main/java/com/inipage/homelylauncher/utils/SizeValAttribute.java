package com.inipage.homelylauncher.utils;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SizeValAttribute {
    AttributeType attrType() default AttributeType.DIP;

    float value();

    enum AttributeType {
        DIP, SP
    }
}
