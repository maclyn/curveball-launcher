package com.inipage.homelylauncher.utils.weather.model;

import com.inipage.homelylauncher.utils.Constants;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(strict = false, name = "weatherdata")
public class LTSForecastModel {
    @Element
    private ProductModel product;

    public static LTSForecastModel deserialize(String serialization) {
        return Constants.DEFAULT_GSON.fromJson(serialization, LTSForecastModel.class);
    }

    public ProductModel getProduct() {
        return product;
    }

    /**
     * Serialize the object for easier storage. I do see the irony of deserializing from XML and
     * then reserializing as JSON. ¯\_(ツ)_/¯
     *
     * @return Serialization.
     */
    public String serialize() {
        return Constants.DEFAULT_GSON.toJson(this);
    }
}

