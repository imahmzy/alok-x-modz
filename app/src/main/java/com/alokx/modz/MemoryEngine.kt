package com.alokx.modz

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

class MemoryEngine(private val ctx: Context, private val onState: (String) -> Unit) {

    var pid = -1
    private var svc: IMemoryService? = null

    private val args = Shizuku.UserServiceArgs(
        ComponentName(ctx.packageName, MemoryService::class.java.name)
    ).daemon(false).processNameSuffix("mem").debuggable(true).version(1)

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
        if (svc == null) return "helper: NULL\n"
        return try { svc!!.status() } catch (e: Exception) { "status err: ${e.message}\n" }
    }

    fun probe(): String {
        val sb = StringBuilder()
        sb.append("ping=").append(Shizuku.pingBinder()).append('\n')
        sb.append("perm=")
        sb.append(
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                    "GRANTED" else "DENIED"
            } catch (e: Throwable) { "ERR " + e.message }
        ).append('\n')
        sb.append("uid=").append(try { Shizuku.getUid() } catch (e: Throwable) { -1 }).append('\n')
        sb.append(sh("id")).append('\n')

        val script = "P=\$(pidof com.dts.freefireth); echo pid=\$P; " +
            "wc -l < /proc/\$P/maps 2>&1; " +
            "A=\$(head -1 /proc/\$P/maps | cut -d- -f1); " +
            "dd if=/proc/\$P/mem bs=1 skip=\$((0x\$A)) count=16 2>/dev/null | od -An -tx1"
        sb.append(sh(script)).append('\n')
        return sb.toString()
    }

   private fun sh(cmd: String): String {
        return try {
            val clazz = Class.forName("rikka.shizuku.Shizuku")
            val method = clazz.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = proc.inputStream.bufferedReader().use { it.readText() }.trim()
            val err = proc.errorStream.bufferedReader().use { it.readText() }.trim()
            proc.destroy()
            if (err.isNotEmpty()) "$out | ERR:$err" else out
        } catch (e: Throwable) {
            "ERR ${e.message}"
        }
    }
