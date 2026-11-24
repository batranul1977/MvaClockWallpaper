package ro.mva.clockwallpaper

import android.graphics.*
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

/** Datele afișate pe cadran (actualizate de serviciu). */
data class ClockData(
    var battery: Int = -1,
    var charging: Boolean = false,
    var steps: Int = -1,
    var tempNow: Int? = null,
    var tempMin: Int? = null,
    var tempMax: Int? = null,
    var weatherText: String = "",
    var weatherIcon: String = "",
    var city: String = "București",
    var forecast: List<String> = emptyList(),   // următoarele 3 ore, ex. "10h ☁3°"
    var rainAlert: String = "",                 // ex. "Ploaie ~10:00 · 70%"
    var sunrise: String = "",                   // ex. "07:28"
    var sunset: String = "",                    // ex. "16:42"
    var nextAlarm: String = "",                 // ex. "06:30" sau "Jo 06:30"
    var stepPermMissing: Boolean = false        // permisiunea „Activitate fizică" lipsește
)

/** Teme de culoare: principal, deschis, închis, culoare stele (r,g,b). */
object ClockThemes {
    val names = arrayOf("Albastru", "Auriu", "Verde", "Roșu", "Mov")
    val main = arrayOf("#2F7BFF", "#D4A017", "#22C55E", "#EF4444", "#8B5CF6")
    val light = arrayOf("#8FB8FF", "#F5D77A", "#86EFAC", "#FCA5A5", "#C4B5FD")
    val dark = arrayOf("#0B2A7A", "#5A4208", "#0B4D25", "#6B1111", "#3B1D7A")
    val stars = arrayOf(
        intArrayOf(120, 170, 255), intArrayOf(255, 215, 140), intArrayOf(130, 240, 170),
        intArrayOf(255, 150, 150), intArrayOf(190, 160, 255)
    )
}

class ClockRenderer {

    // culorile temei curente (numele „blue*" au rămas din versiunea inițială)
    private var blue = Color.parseColor("#2F7BFF")
    private var blueLight = Color.parseColor("#8FB8FF")
    private var blueDark = Color.parseColor("#0B2A7A")
    private var starRgb = intArrayOf(120, 170, 255)
    private var themeIdx = -1
    private val green = Color.parseColor("#22C55E")
    private val amber = Color.parseColor("#FFB74D")

    private fun applyTheme(i: Int) {
        val t = i.coerceIn(0, ClockThemes.names.size - 1)
        if (t == themeIdx) return
        themeIdx = t
        blue = Color.parseColor(ClockThemes.main[t])
        blueLight = Color.parseColor(ClockThemes.light[t])
        blueDark = Color.parseColor(ClockThemes.dark[t])
        starRgb = ClockThemes.stars[t]
    }
    private val red = Color.parseColor("#E53935")
    private val grey = Color.parseColor("#9AA4B8")

    private val zileScurt = arrayOf("", "DU", "LU", "MA", "MI", "JO", "VI", "SÂ")
    private val zileLung = arrayOf("", "Duminică", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă")
    private val luni = arrayOf(
        "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
        "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie"
    )

    private val thin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var stars: List<FloatArray> = emptyList()
    private var lastW = 0
    private var lastH = 0

    private fun ensureStars(w: Int, h: Int) {
        if (w == lastW && h == lastH && stars.isNotEmpty()) return
        lastW = w; lastH = h
        val rnd = Random(42)
        // stele concentrate spre centru (efect "galaxie" ca în original)
        stars = List(260) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val d = (rnd.nextFloat().pow(1.8f)) * 0.55f
            floatArrayOf(a, d, rnd.nextFloat() * 1.6f + 0.4f, rnd.nextFloat())
        }
    }

    fun draw(
        c: Canvas, w: Int, h: Int, data: ClockData, timeMs: Long,
        scale: Float = 1f, posY: Float = 0.56f, showDigital: Boolean = true,
        background: Bitmap? = null, dim: Float = 0.4f, plate: Boolean = true,
        stepLenCm: Int = 70, zoom: Float = 1f,
        theme: Int = 0, night: Boolean = false, goal: Int = 6000, weightKg: Int = 80,
        minimal: Boolean = false
    ) {
        applyTheme(theme)
        ensureStars(w, h)
        val phaseB = (timeMs / 5000L) % 2L == 1L   // alternare la 5 secunde
        c.drawColor(Color.BLACK)
        if (background != null) {
            // center-crop pe tot ecranul
            val sc = max(w / background.width.toFloat(), h / background.height.toFloat())
            val bw = background.width * sc; val bh = background.height * sc
            val dst = RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f)
            p.shader = null
            c.drawBitmap(background, null, dst, bmpPaint)
            if (dim > 0f) c.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 0))
        }

        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val sec = cal.get(Calendar.SECOND)
        val ms = cal.get(Calendar.MILLISECOND)

        val r = min(w, h) * 0.44f * scale
        val cx = w / 2f
        val cy = h * posY

        // ---------- Ora digitală + data deasupra cadranului ----------
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // ora digitală se estompează când cadranul e mărit (lupa), ca să nu se suprapună
        val digitalAlpha = (1f - (zoom - 1f) * 4f).coerceIn(0f, 1f)
        if (showDigital && digitalAlpha > 0.01f) {
        tp.color = Color.WHITE
        tp.alpha = (255 * digitalAlpha).toInt()
        tp.typeface = thin
        tp.textSize = r * 0.55f
        val timeY = cy - r - r * 0.42f
        c.drawText(String.format("%02d:%02d", hour, minute), cx, timeY, tp)

        tp.typeface = light
        tp.textSize = r * 0.085f
        tp.color = Color.parseColor("#DDE3EE")
        val dateStr = "${zileLung[dow]}, ${cal.get(Calendar.DAY_OF_MONTH)} ${luni[cal.get(Calendar.MONTH)]}"
        tp.alpha = (255 * digitalAlpha).toInt()
        c.drawText(dateStr, cx, timeY + r * 0.15f, tp)
        tp.alpha = 255
        }

        // ---------- LUPĂ: tot cadranul se mărește în jurul centrului ----------
        c.save()
        if (zoom != 1f) c.scale(zoom, zoom, cx, cy)

        // ---------- Placă întunecată sub cadran (lizibilitate pe fundal cu imagine) ----------
        if (plate && background != null) {
            p.shader = RadialGradient(
                cx, cy, r * 1.15f,
                intArrayOf(Color.argb(235, 0, 0, 0), Color.argb(200, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.85f, 1f), Shader.TileMode.CLAMP
            )
            p.style = Paint.Style.FILL
            c.drawCircle(cx, cy, r * 1.15f, p)
            p.shader = null
        }

        // ---------- Galaxie de stele în centru ----------
        val tw = if (night) 0.25f else (timeMs % 4000L) / 4000f
        for (s in stars) {
            val sx = cx + cos(s[0]) * s[1] * r
            val sy = cy + sin(s[0]) * s[1] * r
            val twinkle = 0.45f + 0.55f * abs(sin((tw + s[3]) * PI.toFloat()))
            p.style = Paint.Style.FILL
            p.color = Color.argb((200 * twinkle).toInt(), starRgb[0], starRgb[1], starRgb[2])
            c.drawCircle(sx, sy, s[2] * r / 300f, p)
        }

        // ---------- Gradații minute ----------
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            if (i % 5 == 0) continue
            val a = Math.toRadians(i * 6.0 - 90).toFloat()
            p.color = Color.parseColor("#C8CEDA")
            p.strokeWidth = r * 0.008f
            c.drawLine(
                cx + cos(a) * r * 0.93f, cy + sin(a) * r * 0.93f,
                cx + cos(a) * r * 0.98f, cy + sin(a) * r * 0.98f, p
            )
        }

        // ---------- Marcaje ore albastre (cu gradient) ----------
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90).toFloat()
            val x1 = cx + cos(a) * r * 0.86f; val y1 = cy + sin(a) * r * 0.86f
            val x2 = cx + cos(a) * r * 1.04f; val y2 = cy + sin(a) * r * 1.04f
            p.shader = LinearGradient(x1, y1, x2, y2, blueLight, blue, Shader.TileMode.CLAMP)
            p.strokeCap = Paint.Cap.BUTT
            p.strokeWidth = if (i % 3 == 0) r * 0.045f else r * 0.03f
            if (i % 3 == 0) {
                // marcaje duble la 12/3/6/9
                val nx = -sin(a) * r * 0.028f; val ny = cos(a) * r * 0.028f
                p.strokeWidth = r * 0.03f
                c.drawLine(x1 + nx, y1 + ny, x2 + nx, y2 + ny, p)
                c.drawLine(x1 - nx, y1 - ny, x2 - nx, y2 - ny, p)
            } else {
                c.drawLine(x1, y1, x2, y2, p)
            }
            p.shader = null
        }

        // ---------- Cifre ----------
        tp.typeface = thin
        tp.color = Color.WHITE
        tp.textSize = r * 0.17f
        for (n in 1..12) {
            val a = Math.toRadians(n * 30.0 - 90).toFloat()
            val x = cx + cos(a) * r * 0.74f
            val y = cy + sin(a) * r * 0.74f - (tp.descent() + tp.ascent()) / 2f
            c.drawText(n.toString(), x, y, tp)
        }

        // ---------- Complicații (ascunse în modul „doar ceas") ----------
        if (!minimal) {
        // Faza lunii + următoarea alarmă, între 12 și vreme
        val hasAlarm = data.nextAlarm.isNotEmpty()
        drawMoon(c, if (hasAlarm) cx - r * 0.13f else cx, cy - r * 0.585f, r * 0.035f, timeMs)
        if (hasAlarm) {
            tp.typeface = medium
            tp.textSize = r * 0.05f
            tp.color = Color.parseColor("#C8D3EA")
            c.drawText("⏰ ${data.nextAlarm}", cx + r * 0.11f, cy - r * 0.567f, tp)
        }

        // Sus: vreme
        tp.typeface = medium
        tp.textSize = r * 0.075f
        tp.color = Color.WHITE
        if (data.tempNow != null) {
            if (data.rainAlert.isNotEmpty()) {
                tp.color = amber
                c.drawText("☔ ${data.rainAlert}", cx, cy - r * 0.44f, tp)
                tp.color = Color.WHITE
            } else {
                c.drawText("${data.weatherIcon}  ${data.weatherText}", cx, cy - r * 0.44f, tp)
            }
            tp.textSize = r * 0.095f
            c.drawText("${data.tempNow}°C   ${data.tempMin}/${data.tempMax}", cx, cy - r * 0.31f, tp)
            p.style = Paint.Style.STROKE; p.color = grey; p.strokeWidth = 1.5f
            c.drawLine(cx - r * 0.22f, cy - r * 0.27f, cx + r * 0.22f, cy - r * 0.27f, p)
            tp.textSize = r * 0.06f; tp.color = grey
            // la fiecare 5 s: oraș → prognoză 3 ore → răsărit/apus
            val phase3 = ((timeMs / 5000L) % 3L).toInt()
            if (phase3 == 1 && data.forecast.isNotEmpty()) {
                tp.textSize = r * 0.05f; tp.color = Color.parseColor("#C8D3EA")
                c.drawText(data.forecast.joinToString("   "), cx, cy - r * 0.19f, tp)
            } else if (phase3 == 2 && data.sunrise.isNotEmpty()) {
                tp.textSize = r * 0.055f; tp.color = Color.parseColor("#FFD27A")
                c.drawText("☀↑ ${data.sunrise}    ☀↓ ${data.sunset}", cx, cy - r * 0.19f, tp)
            } else {
                c.drawText(data.city, cx, cy - r * 0.19f, tp)
            }
        } else {
            tp.color = grey
            c.drawText("☁  vremea se încarcă…", cx, cy - r * 0.35f, tp)
        }

        // Stânga: zi săptămână (roșu) + data completă zz.ll.aaaa
        val dateTxt = String.format(
            "%02d.%02d.%04d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
        )
        tp.typeface = medium
        tp.textSize = r * 0.10f * 0.62f          // aceeași mărime ca în drawBox
        val dw = tp.measureText(dateTxt) + r * 0.04f
        val dRight = cx - r * 0.15f               // marginea dreaptă a casetei cu data
        drawBox(c, dRight - dw / 2f, cy, dw, r * 0.10f, dateTxt, blueDark, Color.WHITE, r)
        drawBox(c, dRight - dw - r * 0.02f - r * 0.05f, cy, r * 0.10f, r * 0.10f, zileScurt[dow], red, Color.WHITE, r)

        // Dreapta: baterie + secunde digitale
        val rx = cx + r * 0.36f
        tp.typeface = medium
        tp.textSize = r * 0.07f
        tp.color = Color.WHITE
        val batTxt = if (data.battery >= 0) "Baterie ${data.battery}%${if (data.charging) " ⚡" else ""}" else "Baterie --"
        c.drawText(batTxt, rx, cy - r * 0.12f, tp)
        drawBox(c, rx - r * 0.06f, cy, r * 0.22f, r * 0.10f, String.format("%02d:%02d", hour, minute), blueDark, Color.WHITE, r)
        drawBox(c, rx + r * 0.14f, cy, r * 0.10f, r * 0.10f, String.format("%02d", sec), blueDark, Color.WHITE, r)

        // Bara de baterie sub dreapta
        if (data.battery >= 0) {
            val bw = r * 0.32f
            val bx = rx - bw / 2; val by = cy + r * 0.11f
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#1B2235")
            c.drawRoundRect(bx, by, bx + bw, by + r * 0.025f, 6f, 6f, p)
            p.color = if (data.battery <= 15) red else blue
            val fillW = bw * data.battery / 100f
            val bh = r * 0.025f
            c.drawRoundRect(bx, by, bx + fillW, by + bh, 6f, 6f, p)
            if (data.charging && !night && fillW > 0f) {
                // reflexie care alunecă pe bară + pulsare ușoară
                val t = (timeMs % 1400L) / 1400f
                val sx = bx + fillW * t
                p.shader = LinearGradient(
                    sx - r * 0.07f, 0f, sx + r * 0.07f, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(210, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                c.save()
                c.clipRect(bx, by, bx + fillW, by + bh)
                c.drawRect(bx, by, bx + fillW, by + bh, p)
                c.restore()
                p.shader = null
                val pulse = (0.5f + 0.5f * sin(timeMs / 300.0).toFloat())
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.006f
                p.color = Color.argb((90 + 120 * pulse).toInt(), Color.red(green), Color.green(green), Color.blue(green))
                c.drawRoundRect(bx - r * 0.006f, by - r * 0.006f, bx + bw + r * 0.006f, by + bh + r * 0.006f, 8f, 8f, p)
                p.style = Paint.Style.FILL
            }
        }

        // Jos: pași
        tp.typeface = medium
        tp.textSize = r * 0.075f
        tp.color = blueLight
        c.drawText("🏃", cx, cy + r * 0.36f, tp)
        tp.color = Color.WHITE
        tp.textSize = r * 0.09f
        c.drawText(if (data.steps >= 0) data.steps.toString() else "--", cx, cy + r * 0.48f, tp)
        tp.textSize = r * 0.055f; tp.color = grey
        if (phaseB && data.steps >= 0) {
            val kcal = (data.steps * weightKg * 0.00057).roundToInt()
            tp.color = amber
            c.drawText("🔥 $kcal kcal", cx, cy + r * 0.56f, tp)
        } else if (data.stepPermMissing) {
            tp.color = amber
            c.drawText("fără permisiune", cx, cy + r * 0.56f, tp)
        } else {
            c.drawText("pași azi", cx, cy + r * 0.56f, tp)
        }

        // Bara obiectivului zilnic de pași (verde când e atins)
        if (data.steps >= 0 && goal > 0) {
            val prog = (data.steps / goal.toFloat()).coerceIn(0f, 1f)
            val gw = r * 0.44f; val gx = cx - gw / 2; val gy = cy + r * 0.605f; val gh = r * 0.022f
            p.shader = null; p.style = Paint.Style.FILL
            p.color = Color.parseColor("#1B2235")
            c.drawRoundRect(gx, gy, gx + gw, gy + gh, gh / 2, gh / 2, p)
            p.color = if (prog >= 1f) green else blue
            if (prog > 0f) c.drawRoundRect(gx, gy, gx + max(gh, gw * prog), gy + gh, gh / 2, gh / 2, p)
        }

        // Distanță: stânga KM, dreapta METRI (pași × lungimea pasului)
        val meters = if (data.steps >= 0) data.steps * stepLenCm / 100.0 else -1.0
        val sideX = r * 0.27f
        tp.typeface = medium
        tp.color = blueLight
        tp.textSize = r * 0.072f
        c.drawText(if (meters >= 0) String.format("%.2f", meters / 1000.0) else "--", cx - sideX, cy + r * 0.48f, tp)
        c.drawText(if (meters >= 0) meters.roundToInt().toString() else "--", cx + sideX, cy + r * 0.48f, tp)
        tp.textSize = r * 0.05f; tp.color = grey
        c.drawText("km", cx - sideX, cy + r * 0.56f, tp)
        c.drawText("metri", cx + sideX, cy + r * 0.56f, tp)
        // separatori fini între grupuri
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.004f
        p.color = Color.parseColor("#3A4560")
        c.drawLine(cx - r * 0.145f, cy + r * 0.41f, cx - r * 0.145f, cy + r * 0.56f, p)
        c.drawLine(cx + r * 0.145f, cy + r * 0.41f, cx + r * 0.145f, cy + r * 0.56f, p)
        } // sfârșit complicații

        // ---------- Ace ----------
        // La lupă acele dispar lin, ca să nu acopere valorile afișate
        val handsAlpha = (1f - (zoom - 1f) * 5f).coerceIn(0f, 1f)
        if (handsAlpha > 0.01f) {
        val handsLayer = if (handsAlpha < 0.99f) c.saveLayerAlpha(null, (255 * handsAlpha).toInt()) else -1
        val secF = if (night) sec.toFloat() else sec + ms / 1000f
        val minF = minute + secF / 60f
        val hourF = (hour % 12) + minF / 60f

        drawSwordHand(c, cx, cy, hourF * 30f, r * 0.52f, r * 0.055f)
        drawSwordHand(c, cx, cy, minF * 6f, r * 0.80f, r * 0.045f)

        // secundar (ascuns în modul de noapte)
        if (!night) {
        val sa = Math.toRadians(secF * 6.0 - 90).toFloat()
        p.style = Paint.Style.STROKE
        p.shader = null
        p.color = Color.parseColor("#F2F4F8")
        p.strokeWidth = r * 0.010f
        p.strokeCap = Paint.Cap.ROUND
        c.drawLine(
            cx - cos(sa) * r * 0.15f, cy - sin(sa) * r * 0.15f,
            cx + cos(sa) * r * 0.92f, cy + sin(sa) * r * 0.92f, p
        )
        }

        // pivot central
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#E0E4EC")
        c.drawCircle(cx, cy, r * 0.035f, p)
        p.color = Color.parseColor("#6A7387")
        c.drawCircle(cx, cy, r * 0.016f, p)
        if (handsLayer >= 0) c.restoreToCount(handsLayer)
        } // sfârșit ace

        c.restore() // sfârșit lupă

        // mod de noapte: totul mai întunecat
        if (night) c.drawColor(Color.argb(110, 0, 0, 0))
    }

    /** Faza lunii desenată din dată (lună nouă de referință: 6 ian 2000, 18:14 UTC). */
    private fun drawMoon(c: Canvas, mx: Float, my: Float, rad: Float, timeMs: Long) {
        val syn = 29.530588853
        var f = ((timeMs - 947182440000L) / 86400000.0 / syn) % 1.0
        if (f < 0) f += 1.0
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#2A3245")
        c.drawCircle(mx, my, rad, p)
        val k = cos(2.0 * PI * f).toFloat()       // 1 = lună nouă, -1 = lună plină
        val circle = RectF(mx - rad, my - rad, mx + rad, my + rad)
        val ell = RectF(mx - abs(k) * rad, my - rad, mx + abs(k) * rad, my + rad)
        val path = Path()
        path.moveTo(mx, my - rad)
        if (f < 0.5) {             // crește: partea dreaptă luminată
            path.arcTo(circle, -90f, 180f)
            path.arcTo(ell, 90f, if (k > 0) -180f else 180f)
        } else {                   // descrește: partea stângă luminată
            path.arcTo(circle, -90f, -180f)
            path.arcTo(ell, 90f, if (k > 0) 180f else -180f)
        }
        path.close()
        p.color = Color.parseColor("#F2EBC8")
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = rad * 0.08f
        p.color = Color.parseColor("#55F2EBC8")
        c.drawCircle(mx, my, rad, p)
        p.style = Paint.Style.FILL
    }

    /** Ac tip "sabie" — contur alb, interior albastru, ca în cadranul original. */
    private fun drawSwordHand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, wid: Float) {
        c.save()
        c.rotate(deg, cx, cy)
        val path = Path().apply {
            moveTo(cx, cy - len)              // vârf
            lineTo(cx + wid, cy - len * 0.55f)
            lineTo(cx + wid * 0.35f, cy - len * 0.05f)
            lineTo(cx, cy + len * 0.08f)
            lineTo(cx - wid * 0.35f, cy - len * 0.05f)
            lineTo(cx - wid, cy - len * 0.55f)
            close()
        }
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#EEF1F6")
        c.drawPath(path, p)

        val inner = Path().apply {
            moveTo(cx, cy - len * 0.93f)
            lineTo(cx + wid * 0.6f, cy - len * 0.55f)
            lineTo(cx, cy - len * 0.22f)
            lineTo(cx - wid * 0.6f, cy - len * 0.55f)
            close()
        }
        p.shader = LinearGradient(cx, cy - len, cx, cy - len * 0.2f, blueLight, blue, Shader.TileMode.CLAMP)
        c.drawPath(inner, p)
        p.shader = null
        c.restore()
    }

    private fun drawBox(
        c: Canvas, centerX: Float, centerY: Float, bw: Float, bh: Float,
        text: String, bg: Int, fg: Int, r: Float
    ) {
        val rect = RectF(centerX - bw / 2, centerY - bh / 2, centerX + bw / 2, centerY + bh / 2)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = bg
        c.drawRoundRect(rect, r * 0.012f, r * 0.012f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.006f
        p.color = Color.parseColor("#C8D3EA")
        c.drawRoundRect(rect, r * 0.012f, r * 0.012f, p)
        tp.typeface = medium
        tp.color = fg
        tp.textSize = bh * 0.62f
        c.drawText(text, centerX, centerY - (tp.descent() + tp.ascent()) / 2f, tp)
    }
}
