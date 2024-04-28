package com.inipage.homelylauncher.model

object ModelUtils {

    public fun Int.isValueSet(): Boolean = this == unsetValue

    const val unsetValue = -1
}