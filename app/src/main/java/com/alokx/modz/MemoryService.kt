package com.alokx.modz

import java.io.File
import java.io.RandomAccessFile

class MemoryService : IMemoryService.Stub() {

    private var gamePid = -1
    private var mem: RandomAccessFile? = null
    private var il2cppBase = 0L
    private var spreadAddr = 0L
    private var spreadOrig: Float? = null
    private val log = StringBuilder()

    private fun hex(v: Long) = "0x" + java.lang.Long.toHexString(v)

    override fun status(): String = log.toString()

    override fun attach(pkg: String): Int {
        log.setLength(0)
        log.append("attach(").append(pkg).append(")\n")

        val p = findPid(pkg)
        if (p == null || p < 0) {
            log.append("pid: NOT FOUND (is FF running?)\n")
            return -1
        }
        gamePid = p
        log.append("pid: ").append(p).append('\n')

        try {
            mem = RandomAccessFile("/proc/$p/mem", "rw")
            log.append("mem: opened OK\n")
        } catch (e: Throwable) {
            log.append("mem OPEN FAILED: ").append(e.message).append('\n')
            try {
                mem = RandomAccessFile("/proc/$p/mem", "r")
                log.append("mem: opened read-only\n")
            } catch (e2: Throwable) {
                log.append("mem read-only also failed: ").append(e2.message).append('\n')
                mem = null
                return p
            }
        }

        il2cppBase = findModuleBase(p, Offsets.IL2CPP)
        log.append("il2cpp: ").append(hex(il2cppBase)).append('\n')
        return p
    }

    override fun getPid(): Int = gamePid

    override fun getModuleBase(name: String): Long = findModuleBase(gamePid, name)

    private fun findPid(pkg: String): Int? {
        val procs = File("/proc").listFiles() ?: return null
        var scanned = 0
        for (d in procs) {
            val first = d.name.firstOrNull() ?: continue
            if (!first.isDigit()) continue
            scanned++
            val cmd = try {
                File(d, "cmdline").readText().trim('\u0000')
            } catch (e: Exception) {
                ""
            }
            if (cmd == pkg) {
                log.append("scanned ").append(scanned).append(" procs\n")
                return d.name.toInt()
            }
        }
        log.append("scanned ").append(scanned).append(" procs, no match\n")
        return null
    }

    private fun findModuleBase(p: Int, name: String): Long {
        if (p < 0) return 0L
        try {
            val lines = File("/proc/$p/maps").readLines()
            log.append("maps lines: ").append(lines.size).append('\n')
            for (line in lines) {
                if (line.endsWith("/$name")) {
                    log.append("found ").append(name).append('\n')
                    return line.substringBefore('-').toLong(16)
                }
            }
            log.append(name).append(" NOT in maps\n")
        } catch (e: Throwable) {
            log.append("maps read failed: ").append(e.message).append('\n')
        }
        return 0L
    }

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
        if (gamePid < 0 || il2cppBase == 0L || mem == null) return 0L
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

    override fun snapshot(): FloatArray {
        log.setLength(0)
        if (gamePid < 0) { log.append("no pid\n"); return FloatArray(0) }
        if (mem == null) { log.append("no mem handle\n"); return FloatArray(0) }
        if (il2cppBase == 0L) { log.append("no il2cpp base\n"); return FloatArray(0) }

        return try {
            val cam = rl(il2cppBase + Offsets.STATIC_CAMERA)
            log.append("cam: ").append(hex(cam)).append('\n')
            if (cam == 0L) return FloatArray(0)

            val camT = rl(cam + Offsets.C_TRANSFORM)
            log.append("camT: ").append(hex(camT)).append('\n')
            if (camT == 0L) return FloatArray(0)

            val camPos = vec3(camT + Offsets.T_POSITION)
            val camRot = quat(camT + Offsets.T_ROTATION)
            val w2c = floats(cam + Offsets.C_WORLD_TO_CAMERA, 16)
            val proj = floats(cam + Offsets.C_PROJECTION, 16)

            val gm = rl(il2cppBase + Offsets.STATIC_GAME_FACADE)
            log.append("gm: ").append(hex(gm)).append('\n')
            if (gm == 0L) return FloatArray(0)

            val local = rl(gm + Offsets.GF_LOCAL_PLAYER)
            val list = rl(gm + Offsets.GF_PLAYER_LIST)
            val items = rl(list + Offsets.LIST_ITEMS)
            val count = ri(list + Offsets.LIST_COUNT).coerceIn(0, 128)

            log.append("local: ").append(hex(local)).append('\n')
            log.append("list: ").append(hex(list)).append('\n')
            log.append("items: ").append(hex(items)).append('\n')
            log.append("count: ").append(count).append('\n')

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
                if (i == 0) {
                    log.append("p0 pos: ").append(pos[0]).append(",")
                        .append(pos[1]).append(",").append(pos[2]).append('\n')
                }
                out.add(pos[0]); out.add(pos[1]); out.add(pos[2])
                out.add(pos[0]); out.add(pos[1] + Offsets.HEAD_HEIGHT); out.add(pos[2])
                out.add(rf(p + Offsets.P_HEALTH))
                out.add(ri(p + Offsets.P_TEAM).toFloat())
                out.add(if (p == local) 1f else 0f)
            }
            out.toFloatArray()
        } catch (e: Throwable) {
            log.append("SNAPSHOT ERR: ").append(e.message).append('\n')
            FloatArray(0)
        }
    }
}
