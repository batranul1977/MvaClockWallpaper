package ro.mva.clockwallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/** Grafic cu bare: pașii din ultimele 7 zile (din cheile hist_yyyyMMdd salvate de wallpaper). */
class StepHistoryView(ctx: Context) : View(ctx) {

    private val prefs = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val zile = arrayOf("", "Du", "Lu", "Ma", "Mi", "Jo", "Vi", "Sâ")
    private val d = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (200 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        val vals = IntArray(7)
        val labels = Array(7) { "" }
        for (i in 6 downTo 0) {               // index 6 = azi
            vals[i] = prefs.getInt("hist_" + fmt.format(cal.time), 0)
            labels[i] = if (i == 6) "Azi" else zile[cal.get(Calendar.DAY_OF_WEEK)]
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val goal = prefs.getInt(MainActivity.KEY_GOAL, 6000)
        val stepCm = prefs.getInt(MainActivity.KEY_STEP_CM, 70)
        val theme = prefs.getInt(MainActivity.KEY_THEME, 0).coerceIn(0, ClockThemes.names.size - 1)
        val barColor = Color.parseColor(ClockThemes.main[theme])
        val green = Color.parseColor("#22C55E")
        val grey = Color.parseColor("#9AA4B8")

        val maxV = max(goal, vals.maxOrNull() ?: 0).coerceAtLeast(1)
        val top = 24 * d
        val bottom = height - 34 * d
        val chartH = bottom - top
        val slot = width / 7f
        val bw = slot * 0.55f

        // fundal
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#121829")
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 10 * d, 10 * d, p)

        // linia obiectivului
        val gy = bottom - chartH * goal / maxV
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = Color.parseColor("#66FFB74D")
        p.pathEffect = DashPathEffect(floatArrayOf(6 * d, 4 * d), 0f)
        c.drawLine(4 * d, gy, width - 4 * d, gy, p)
        p.pathEffect = null
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 9 * d
        tp.color = Color.parseColor("#FFB74D")
        c.drawText("obiectiv $goal", 6 * d, gy - 3 * d, tp)
        tp.textAlign = Paint.Align.CENTER

        p.style = Paint.Style.FILL
        for (i in 0 until 7) {
            val x = slot * i + slot / 2
            val bh = chartH * vals[i] / maxV
            p.color = if (vals[i] >= goal) green else barColor
            if (vals[i] > 0) c.drawRoundRect(x - bw / 2, bottom - bh, x + bw / 2, bottom, 4 * d, 4 * d, p)

            tp.textSize = 11 * d
            tp.color = Color.WHITE
            c.drawText(vals[i].toString(), x, bottom - bh - 5 * d, tp)
            tp.color = if (i == 6) Color.WHITE else grey
            c.drawText(labels[i], x, bottom + 14 * d, tp)
            tp.textSize = 9 * d
            tp.color = grey
            c.drawText(String.format(Locale.getDefault(), "%.1f km", vals[i] * stepCm / 100000.0), x, bottom + 27 * d, tp)
        }
    }
}
