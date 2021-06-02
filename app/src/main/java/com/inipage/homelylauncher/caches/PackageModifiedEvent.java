package com.inipage.homelylauncher.caches;

/**
 * These modifications are *persistent* changes. Transient changes -- an add and remove _during_
 * an upgrade -- aren't sent.
 */
public class PackageModifiedEvent {

    final private String mPackageName;
    final private Modification mModification;

    PackageModifiedEvent(String packageName, Modification modification) {
        mPackageName = packageName;
        mModification = modification;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public Modification getModification() {
        return mModification;
    }

    public enum Modification {
        ADDED,
        REMOVED,
        UPDATED
    }
}
