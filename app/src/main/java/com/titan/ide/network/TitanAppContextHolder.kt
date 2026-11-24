package com.titan.ide.network

import android.content.Context

/**
 * TitanAppContextHolder - Global Application Context Holder
 * Ensures 100% on-device direct engine execution without memory leaks.
 */
object TitanAppContextHolder {

    @Volatile
    var appContext: Context? = null

    val context: Context?
        get() = appContext

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) {
                    appContext = context.applicationContext ?: context
                }
            }
        }
    }

    fun getRequiredContext(): Context {
        return appContext ?: throw IllegalStateException(
            "TitanAppContextHolder is not initialized. Call TitanAppContextHolder.init(context) in Application or MainActivity."
        )
    }
}
