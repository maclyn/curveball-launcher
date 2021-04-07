package com.inipage.homelylauncher.caches;

/**
 * These modifications reflect the availability of packages installed on removable media.
 */
public class PackagesBulkModifiedEvent {

    final private String[] mPackageNames;
    final private Availability mAvailability;

    PackagesBulkModifiedEvent(String[] packageName, Availability availability) {
        mPackageNames = packageName;
        mAvailability = availability;
    }

    public String[] getPackageName() {
        return mPackageNames;
    }

    public Availability getAvailability() {
        return mAvailability;
    }

    public enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }
}
