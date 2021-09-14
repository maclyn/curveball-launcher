package com.inipage.homelylauncher.dock.items;

import android.content.Context;

import com.inipage.homelylauncher.dock.DockControllerItem;

/**
 * Base class for synchronously loaded controller item (i.e. items that can quickly fetch what
 * they need when they're instantiated, or items instantiated with the values they're holding).
 */
public abstract class SynchDockControllerItem extends DockControllerItem {}
