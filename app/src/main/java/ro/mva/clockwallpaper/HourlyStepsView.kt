package ro.mva.clockwallpaper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pașii de azi pe fiecare oră (0–23), din cheile hcum_yyyyMMdd_HH
 * (totalul cumulat văzut ultima dată în acea oră).
 */
class HourlyStepsView(ctx: Context) : View(ctx) {

    private val prefs = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val d = resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (170 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val nowH = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val vals = IntArray(24)
        var prev = 0
        for (h in 0..23) {
            val cum = prefs.getInt(String.format(Locale.US, "hcum_%s_%02d", day, h), -1)
            if (cum >= 0) {
                vals[h] = (cum - prev).coerceAtLeast(0)
                prev = cum
            }
        }
        val theme = prefs.getInt(MainActivity.KEY_THEME, 0).coerceIn(0, ClockThemes.names.size - 1)
        val barColor = Color.parseColor(ClockThemes.main[theme])
        val barLight = Color.parseColor(ClockThemes.light[theme])
        val grey = Color.parseColor("#9AA4B8")

        // fundal
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#121829")
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 10 * d, 10 * d, p)

        val best = vals.indices.maxByOrNull { vals[it] } ?: 0
        val maxV = vals.maxOrNull()?.coerceAtLeast(1) ?: 1
        val left = 8 * d
        val right = width - 8 * d
        val top = 30 * d
        val bottom = height - 22 * d
        val slot = (right - left) / 24f
        val bw = slot * 0.65f

        // rezumat sus
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 12 * d
        tp.color = Color.WHITE
        val total = vals.sum()
        c.drawText(
            if (vals[best] > 0) String.format(Locale.getDefault(), "Cea mai activă oră: %02d:00 (%d pași)  ·  total %d", best, vals[best], total)
            else "Încă nu sunt date pentru azi",
            left, 18 * d, tp
        )
        tp.textAlign = Paint.Align.CENTER

        for (h in 0..23) {
            val x = left + slot * h + slot / 2
            val bh = (bottom - top) * vals[h] / maxV
            p.color = when {
                h == best && vals[h] > 0 -> barLight
                h > nowH -> Color.parseColor("#1B2235")
                else -> barColor
            }
            if (vals[h] > 0) c.drawRoundRect(x - bw / 2, bottom - bh, x + bw / 2, bottom, 2 * d, 2 * d, p)
            else { p.color = Color.parseColor("#1B2235"); c.drawRect(x - bw / 2, bottom - 2 * d, x + bw / 2, bottom, p) }
            if (h % 3 == 0) {
                tp.textSize = 10 * d
                tp.color = if (h <= nowH) grey else Color.parseColor("#4A556E")
                c.drawText(h.toString(), x, bottom + 14 * d, tp)
            }
        }
    }
}
