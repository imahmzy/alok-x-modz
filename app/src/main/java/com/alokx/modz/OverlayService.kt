package com.alokx.modz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

class OverlayService : Service() {

    companion object {
        const val PKG = "com.dts.freefireth"
        const val CH = "ffmod"
        const val AIM_BLEND = 0.8f
        const val AIM_FOV_DEG = 45f
    }

    private lateinit var wm: WindowManager
    private lateinit var engine: MemoryEngine
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var espView: EspView? = null

    private val handler = Handler(Looper.getMainLooper())

    private var espOn = true
    private var espBox = true
    private var espHealth = true
    private var noSpread = false
    private var aimlock = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startFg()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        engine = MemoryEngine(this) { }
        engine.bind()

        addEsp()
        addBubble()
        tick()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        espView?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        engine.setNoSpread(false)
        engine.unbind()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // ESP layer: full screen, NEVER receives touch
    // ------------------------------------------------------------------

    private fun addEsp() {
        val v = EspView(this)
        v.isClickable = false
        v.isFocusable = false
        v.isFocusableInTouchMode = false

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START

        wm.addView(v, lp)
        espView = v
    }

    // ------------------------------------------------------------------
    // Bubble: small, touchable, draggable
    // ------------------------------------------------------------------

    private fun addBubble() {
        val tv = TextView(this).apply {
            text = "AX"
            setTextColor(Color.parseColor("#0B0B12"))
            setBackgroundColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            setPadding(34, 24, 34, 24)
            typeface = Typeface.DEFAULT_BOLD
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 24
        lp.y = 320

        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        var moved = false

        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX.toInt()
                    downY = e.rawY.toInt()
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX.toInt() - downX
                    val dy = e.rawY.toInt() - downY
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    runCatching { wm.updateViewLayout(tv, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> false
            }
        }

        wm.addView(tv, lp)
        bubble = tv
    }

    // ------------------------------------------------------------------
    // Panel: touchable menu
    // ------------------------------------------------------------------

    private fun togglePanel() {
        panel?.let {
            runCatching { wm.removeView(it) }
            panel = null
            return
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E60E0E14"))
            setPadding(30, 26, 30, 26)
        }

        col.addView(TextView(this).apply {
            text = "ALOK X MODZ v1"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 14f
            setPadding(0, 0, 0, 14)
        })

        fun sw(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            col.addView(Switch(this).apply {
                text = label
                setTextColor(Color.WHITE)
                isChecked = get()
                setOnCheckedChangeListener { _, v -> set(v) }
            })
        }

        sw("ESP master", { espOn }, { espOn = it })
        sw("ESP Box", { espBox }, { espBox = it })
        sw("ESP Health only", { espHealth }, { espHealth = it })
        sw("No bullet spread", { noSpread }, { noSpread = it; engine.setNoSpread(it) })
        sw("Aimlock 80% head", { aimlock }, { aimlock = it })

        col.addView(Button(this).apply {
            text = "CLOSE MOD"
            setTextColor(Color.parseColor("#FF3B30"))
            setOnClickListener {
                runCatching { stopSelf() }
            }
        })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 24
        lp.y = 320

        wm.addView(col, lp)
        panel = col
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    private fun tick() {
        handler.postDelayed({
            engine.tryAttach(PKG)

            espView?.visibility = if (espOn) View.VISIBLE else View.GONE

            val snap = engine.snapshot()
            espView?.let {
                it.data = snap
                it.drawBox = espBox
                it.drawHealth = espHealth
                it.invalidate()
            }
            if (aimlock) doAim(snap)
            tick()
        }, 33)
    }

    private fun doAim(d: FloatArray) {
        if (d.size < 40) return
        val count = d[39].toInt()
        if (count <= 0) return

        val cx = d[32]; val cy = d[33]; val cz = d[34]
        val cur = floatArrayOf(d[35], d[36], d[37], d[38])
        val fwd = Vec.forward(cur)

        var best = -1
        var bestDot = cos(Math.toRadians(AIM_FOV_DEG.toDouble())).toFloat()

        for (i in 0 until count) {
            val o = 40 + i * 9
            if (o + 8 >= d.size) break
            if (d[o + 8] > 0.5f) continue
            val dx = d[o + 3] - cx
            val dy = d[o + 4] - cy
            val dz = d[o + 5] - cz
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 0.01f) continue
            val dot = (dx / len) * fwd[0] + (dy / len) * fwd[1] + (dz / len) * fwd[2]
            if (dot > bestDot) { bestDot = dot; best = o }
        }
        if (best < 0) return

        val tgt = Vec.lookQuat(d[best + 3] - cx, d[best + 4] - cy, d[best + 5] - cz)
        val mixed = Vec.nlerp(cur, tgt, AIM_BLEND)
        engine.setAim(mixed[0], mixed[1], mixed[2], mixed[3])
    }

    private fun startFg() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "FF Mod", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, CH)
            .setContentTitle("ALOK X MODZ running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }
}

// ======================================================================
// ESP drawing layer
// ======================================================================

class EspView(c: Context) : View(c) {

    var data: FloatArray = FloatArray(0)
    var drawBox = true
    var drawHealth = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val d = data
        if (d.size < 40) return
        val count = d[39].toInt()
        if (count <= 0) return

        val vp = Vec.mul(d.copyOfRange(16, 32), d.copyOfRange(0, 16))
        val W = width
        val H = height

        var myTeam = -99
        for (i in 0 until count) {
            val o = 40 + i * 9
            if (o + 8 >= d.size) break
            if (d[o + 8] > 0.5f) myTeam = d[o + 7].toInt()
        }

        for (i in 0 until count) {
            val o = 40 + i * 9
            if (o + 8 >= d.size) break
            if (d[o + 8] > 0.5f) continue

            val feet = Vec.w2s(vp, d[o], d[o + 1], d[o + 2], W, H) ?: continue
            val head = Vec.w2s(vp, d[o + 3], d[o + 4], d[o + 5], W, H) ?: continue

            val bh = abs(feet[1] - head[1])
            if (bh < 4f) continue
            val bw = bh * 0.55f
            val top = min(feet[1], head[1])
            val rect = RectF(feet[0] - bw / 2f, top, feet[0] + bw / 2f, top + bh)
            val enemy = d[o + 7].toInt() != myTeam

            if (drawBox) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = if (enemy) 0xFFFF3B30.toInt() else 0xFF3DDC84.toInt()
                canvas.drawRect(rect, p)
            }

            if (drawHealth) {
                val hp = (d[o + 6] / 100f).coerceIn(0f, 1f)
                val barH = 6f
                val t = rect.top - barH - 4f
                p.style = Paint.Style.FILL
                p.color = 0xAA000000.toInt()
                canvas.drawRect(rect.left, t, rect.right, t + barH, p)
                p.color = 0xFF32D74B.toInt()
                canvas.drawRect(rect.left, t, rect.left + rect.width() * hp, t + barH, p)
            }
        }
    }
}

// ======================================================================
// Math
// ======================================================================

object Vec {

    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) for (row in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + row] * b[col * 4 + k]
            r[col * 4 + row] = s
        }
        return r
    }

    fun w2s(vp: FloatArray, x: Float, y: Float, z: Float, w: Int, h: Int): FloatArray? {
        val cx = vp[0] * x + vp[4] * y + vp[8] * z + vp[12]
        val cy = vp[1] * x + vp[5] * y + vp[9] * z + vp[13]
        val cw = vp[3] * x + vp[7] * y + vp[11] * z + vp[15]
        if (cw < 0.01f) return null
        val nx = cx / cw
        val ny = cy / cw
        return floatArrayOf((nx * 0.5f + 0.5f) * w, (1f - (ny * 0.5f + 0.5f)) * h)
    }

    fun forward(q: FloatArray): FloatArray {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        return floatArrayOf(
            2f * (x * z + w * y),
            2f * (y * z - w * x),
            1f - 2f * (x * x + y * y)
        )
    }

    fun lookQuat(dx: Float, dy: Float, dz: Float): FloatArray {
        var fx = dx; var fy = dy; var fz = dz
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        if (fl < 1e-6f) return floatArrayOf(0f, 0f, 0f, 1f)
        fx /= fl; fy /= fl; fz /= fl

        var rx = fz
        var rz = -fx
        val rl = sqrt(rx * rx + rz * rz)
        if (rl < 1e-6f) { rx = 1f; rz = 0f } else { rx /= rl; rz /= rl }

        val ux = fy * rz
        val uy = fz * rx - fx * rz
        val uz = -fy * rx

        val m00 = rx; val m01 = ux; val m02 = fx
        val m10 = 0f; val m11 = uy; val m12 = fy
        val m20 = rz; val m21 = uz; val m22 = fz

        val tr = m00 + m11 + m22
        return if (tr > 0f) {
            val s = sqrt(tr + 1f) * 2f
            floatArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
        } else if (m00 > m11 && m00 > m22) {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            floatArrayOf(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
        } else if (m11 > m22) {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            floatArrayOf((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
        } else {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            floatArrayOf((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
        }
    }

    fun nlerp(a: FloatArray, b0: FloatArray, t: Float): FloatArray {
        val dot = a[0] * b0[0] + a[1] * b0[1] + a[2] * b0[2] + a[3] * b0[3]
        val s = if (dot < 0f) -1f else 1f
        val x = a[0] + (b0[0] * s - a[0]) * t
        val y = a[1] + (b0[1] * s - a[1]) * t
        val z = a[2] + (b0[2] * s - a[2]) * t
        val w = a[3] + (b0[3] * s - a[3]) * t
        val l = sqrt(x * x + y * y + z * z + w * w)
        return floatArrayOf(x / l, y / l, z / l, w / l)
    }
}
