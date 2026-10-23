package com.alokx.modz

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku

class MemoryEngine(private val ctx: Context, private val onState: (String) -> Unit) {

    var pid = -1
    private var svc: IMemoryService? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(ctx.packageName, MemoryService::class.java.name)
    ).daemon(false).processNameSuffix("mem").debuggable(false).version(1)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            svc = IMemoryService.Stub.asInterface(binder)
            onState("connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            svc = null
            pid = -1
            onState("disconnected")
        }
    }

    fun bind() {
        if (!Shizuku.pingBinder()) { onState("Shizuku NOT running"); return }
        try {
            Shizuku.bindUserService(args, conn)
            onState("binding...")
        } catch (e: Exception) {
            onState("bind FAILED: ${e.message}")
        }
    }

    fun unbind() {
        try { Shizuku.unbindUserService(args, conn, true) } catch (e: Exception) {}
    }

    fun tryAttach(pkg: String) {
        if (pid > 0) return
        pid = try { svc?.attach(pkg) ?: -1 } catch (e: Exception) { -1 }
    }

    fun snapshot(): FloatArray =
        try { svc?.snapshot() ?: FloatArray(0) } catch (e: Exception) { FloatArray(0) }

    fun setAim(x: Float, y: Float, z: Float, w: Float) {
        try { svc?.setCameraRotation(x, y, z, w) } catch (e: Exception) {}
    }

    fun setNoSpread(on: Boolean) {
        try { svc?.setNoSpread(on) } catch (e: Exception) {}
    }

    fun status(): String {
        if (svc == null) return "helper: NULL (not connected)\n"
        return try { svc!!.status() } catch (e: Exception) { "status err: ${e.message}\n" }
    }
}
