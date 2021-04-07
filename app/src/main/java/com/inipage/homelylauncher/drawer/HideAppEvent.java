package com.inipage.homelylauncher.drawer;

import com.inipage.homelylauncher.model.ApplicationIcon;

public class HideAppEvent {

    private final ApplicationIcon mApplicationIcon;

    HideAppEvent(ApplicationIcon app) {
        mApplicationIcon = app;
    }

    public ApplicationIcon app() {
        return mApplicationIcon;
    }
}
