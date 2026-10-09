package com.aistra.hail.app

import android.content.pm.ApplicationInfo
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.utils.HPackages

class AppInfo(
    val packageName: String,
    var whitelisted: Boolean = false,
    var tagId: Int = 0
) {
    enum class State { NOT_FOUND, UNFROZEN, FROZEN }

    val applicationInfo: ApplicationInfo? get() = HPackages.getApplicationInfoOrNull(packageName)
    val name get() = applicationInfo?.loadLabel(app.packageManager) ?: packageName
    val state
        get() = when {
            applicationInfo == null -> State.NOT_FOUND
            AppManager.isAppFrozen(packageName) -> State.FROZEN
            else -> State.UNFROZEN
        }

    override fun equals(other: Any?): Boolean = other is AppInfo && other.packageName == packageName
    override fun hashCode(): Int = packageName.hashCode()
}
