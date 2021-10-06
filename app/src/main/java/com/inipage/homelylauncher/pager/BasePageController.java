package com.inipage.homelylauncher.pager;

import android.app.Activity;

import com.inipage.homelylauncher.views.DecorViewDragger;

import org.greenrobot.eventbus.EventBus;

public interface BasePageController {

    default void attach(Activity activity) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        DecorViewDragger.get(activity).registerDragAwareComponent(getDragAwareComponent());
    }

    DecorViewDragger.TargetedDragAwareComponent getDragAwareComponent();

    default void detach(Activity activity) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        DecorViewDragger.get(activity).unregisterDragAwareComponent(getDragAwareComponent());
    }

    default void onPause() {
    }

    default void onResume() {
    }
}
