package com.inipage.homelylauncher.model

class ApplicationIconCheckable(appIcon: ApplicationIconHideable) :
    ApplicationIcon(appIcon.packageName, appIcon.activityName, appIcon.name)
{

    var isChecked: Boolean = false

    override fun hashCode(): Int {
        return super.hashCode() * if (isChecked) 1 else -1
    }

    override fun equals(other: Any?): Boolean {
        return if (other !is ApplicationIconHideable) {
            false
        } else other.hashCode() == this.hashCode()
    }

    override fun toString(): String {
        return super.toString() + if (isChecked) " (checked)" else ""
    }
}