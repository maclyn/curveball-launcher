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

    DecorViewDragger.DragAwareComponent getDragAwareComponent();

    default void detach(Activity activity) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        DecorViewDragger.get(activity).registerDragAwareComponent(getDragAwareComponent());
    }

    default void onPause() {
    }

    default void onResume() {
    }
}
