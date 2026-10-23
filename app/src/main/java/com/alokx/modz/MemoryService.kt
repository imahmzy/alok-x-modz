package com.alokx.modz

import java.io.File
import java.io.RandomAccessFile

class MemoryService : IMemoryService.Stub() {

    private var pid = -1
    private var mem: RandomAccessFile? = null
    private var il2cppBase = 0L
    private var spreadAddr = 0L
    private var spreadOrig: Float? = null

    override fun attach(pkg: String): Int {
        val p = findPid(pkg) ?: return -1
        pid = p
        mem = RandomAccessFile("/proc/$p/mem", "rw")
        il2cppBase = findModuleBase(p, Offsets.IL2CPP)
        return p
    }

    override fun getPid(): Int = pid

    override fun getModuleBase(name: String): Long = findModuleBase(pid, name)

    private fun findPid(pkg: String): Int? {
        val procs = File("/proc").listFiles() ?: return null
        for (d in procs) {
            val first = d.name.firstOrNull() ?: continue
            if (!first.isDigit()) continue
            val cmd = try {
                File(d, "cmdline").readText().trim('\u0000')
            } catch (e: Exception) { continue }
            if (cmd == pkg) return d.name.toInt()
        }
        return null
    }

    private fun findModuleBase(p: Int, name: String): Long {
        if (p < 0) return 0L
        try {
            File("/proc/$p/maps").forEachLine { line ->
                if (line.endsWith("/$name")) return line.substringBefore('-').toLong(16)
            }
        } catch (e: Exception) {}
        return 0L
    }

    // little-endian helpers (RandomAccessFile is big-endian)
    private fun rl(a: Long): Long { mem!!.seek(a); return java.lang.Long.reverseBytes(mem!!.readLong()) }
    private fun ri(a: Long): Int { mem!!.seek(a); return java.lang.Integer.reverseBytes(mem!!.readInt()) }
    private fun rf(a: Long): Float = Float.fromBits(ri(a))
    private fun wi(a: Long, v: Int) { mem!!.seek(a); mem!!.writeInt(java.lang.Integer.reverseBytes(v)) }
    private fun wf(a: Long, v: Float) = wi(a, v.toRawBits())
    private fun vec3(a: Long) = floatArrayOf(rf(a), rf(a + 4), rf(a + 8))
    private fun quat(a: Long) = floatArrayOf(rf(a), rf(a + 4), rf(a + 8), rf(a + 12))
    private fun floats(a: Long, n: Int) = FloatArray(n) { rf(a + it * 4L) }

    override fun readMem(address: Long, size: Int): ByteArray {
        val b = ByteArray(size)
        mem!!.seek(address)
        mem!!.readFully(b)
        return b
    }

    private fun localPlayer(): Long {
        if (pid < 0 || il2cppBase == 0L || mem == null) return 0L
        val gm = rl(il2cppBase + Offsets.STATIC_GAME_FACADE)
        if (gm == 0L) return 0L
        return rl(gm + Offsets.GF_LOCAL_PLAYER)
    }

    override fun setCameraRotation(x: Float, y: Float, z: Float, w: Float) {
        if (il2cppBase == 0L || mem == null) return
        val cam = rl(il2cppBase + Offsets.STATIC_CAMERA)
        if (cam == 0L) return
        val t = rl(cam + Offsets.C_TRANSFORM)
        if (t == 0L) return
        wf(t + Offsets.T_ROTATION, x)
        wf(t + Offsets.T_ROTATION + 4, y)
        wf(t + Offsets.T_ROTATION + 8, z)
        wf(t + Offsets.T_ROTATION + 12, w)
    }

    override fun setNoSpread(on: Boolean) {
        val lp = localPlayer()
        if (lp == 0L) return
        val addr = lp + Offsets.P_SPREAD
        if (on) {
            if (spreadOrig == null) {
                spreadOrig = rf(addr)
                spreadAddr = addr
            }
            wf(addr, 0f)
        } else {
            spreadOrig?.let {
                if (spreadAddr != 0L) wf(spreadAddr, it)
                spreadOrig = null
            }
        }
    }

    /**
     * Layout: [0..15] worldToCamera, [16..31] projection, [32..34] camPos,
     * [35..38] camRot, [39] count, then per player 9 floats:
     * x,y,z, headX,headY,headZ, health, team, isLocal
     */
    override fun snapshot(): FloatArray {
        if (pid < 0 || il2cppBase == 0L || mem == null) return FloatArray(0)
        return try {
            val cam = rl(il2cppBase + Offsets.STATIC_CAMERA)
            if (cam == 0L) return FloatArray(0)
            val camT = rl(cam + Offsets.C_TRANSFORM)
            val camPos = vec3(camT + Offsets.T_POSITION)
            val camRot = quat(camT + Offsets.T_ROTATION)
            val w2c = floats(cam + Offsets.C_WORLD_TO_CAMERA, 16)
            val proj = floats(cam + Offsets.C_PROJECTION, 16)

            val gm = rl(il2cppBase + Offsets.STATIC_GAME_FACADE)
            if (gm == 0L) return FloatArray(0)
            val local = rl(gm + Offsets.GF_LOCAL_PLAYER)
            val list = rl(gm + Offsets.GF_PLAYER_LIST)
            val items = rl(list + Offsets.LIST_ITEMS)
            val count = ri(list + Offsets.LIST_COUNT).coerceIn(0, 128)

            val out = ArrayList<Float>(64 + count * 9)
            w2c.forEach { out.add(it) }
            proj.forEach { out.add(it) }
            camPos.forEach { out.add(it) }
            camRot.forEach { out.add(it) }
            out.add(count.toFloat())

            for (i in 0 until count) {
                val p = rl(items + Offsets.ARRAY_DATA + i * 8L)
                if (p == 0L) continue
                val t = rl(p + Offsets.P_TRANSFORM)
                if (t == 0L) continue
                if (ri(p + Offsets.P_IS_DEAD) != 0) continue
                val pos = vec3(t + Offsets.T_POSITION)
                out.add(pos[0]); out.add(pos[1]); out.add(pos[2])
                out.add(pos[0]); out.add(pos[1] + Offsets.HEAD_HEIGHT); out.add(pos[2])
                out.add(rf(p + Offsets.P_HEALTH))
                out.add(ri(p + Offsets.P_TEAM).toFloat())
                out.add(if (p == local) 1f else 0f)
            }
            out.toFloatArray()
        } catch (e: Exception) {
            FloatArray(0)
        }
    }
}
