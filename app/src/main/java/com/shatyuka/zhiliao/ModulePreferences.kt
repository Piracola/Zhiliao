package com.shatyuka.zhiliao

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 模块 App 侧的配置通道：绑定 LSPosed 服务，读写远端偏好（zhiliao_preferences）。
 * 绑定成功后自动把 scope 收敛到只有知乎，并把老版本留在本地的配置搬上去（一次）。
 *
 * 服务未绑上（LSPosed 未启用本模块的服务、或远端偏好不可用）时自动回落到本地
 * SharedPreferences：界面照常可用，Hook 侧经读穿透仍能读到本地值；绑上后再切远端。
 */
object ModulePreferences {
    const val GROUP = "zhiliao_preferences"
    private const val TARGET = "com.zhihu.android"
    private var registered = false
    private var service: XposedService? = null
    private var preferences: SharedPreferences? = null
    private var appContext: Context? = null
    private val listeners = mutableSetOf<(SharedPreferences) -> Unit>()

    @Synchronized
    fun connect(context: Context, listener: (SharedPreferences) -> Unit) {
        listeners += listener
        appContext = context.applicationContext
        preferences?.let(listener)
        service?.let(::syncScope)
        if (registered) return
        registered = true
        // 先给界面一个可用的本地实现（远端绑定是异步的）
        val local = context.applicationContext.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
        preferences = local
        listener(local)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(bound: XposedService) {
                service = bound
                val remote = bound.getRemotePreferences(GROUP)
                synchronized(this@ModulePreferences) {
                    preferences = remote
                }
                syncScope(bound)
                migrateLocal(context.applicationContext, remote)
                synchronized(this@ModulePreferences) { listeners.toList() }.forEach { it(remote) }
            }

            override fun onServiceDied(dead: XposedService) {
                if (service === dead) {
                    service = null
                    // 服务掉了退回本地，保证界面仍可读写
                    appContext?.let { ctx ->
                        val local = ctx.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
                        synchronized(this@ModulePreferences) { preferences = local }
                        synchronized(listeners) { listeners.toList() }.forEach { it(local) }
                    }
                }
            }
        })
    }

    fun refreshScope() {
        service?.let(::syncScope)
    }

    private fun syncScope(service: XposedService) {
        runCatching {
            val scope = service.scope
            val extra = scope.filterNot { it == TARGET }
            if (extra.isNotEmpty()) service.removeScope(extra)
            if (TARGET !in scope) {
                service.requestScope(listOf(TARGET), object : XposedService.OnScopeEventListener {})
            }
        }
    }

    /** 老版本把配置写在模块自身数据目录（新 UI 首次运行时可能存在），搬去远端后清掉。 */
    private fun migrateLocal(context: Context, remote: SharedPreferences) {
        val local = context.getSharedPreferences(GROUP, Context.MODE_PRIVATE)
        if (local.all.isEmpty()) return
        val editor = remote.edit()
        for ((key, value) in local.all) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
            }
        }
        if (editor.commit()) local.edit().clear().apply()
    }

    /** 诊断用：当前生效的通道名。 */
    fun activeBackend(): String =
        if (service != null) "remote" else "local"
}
