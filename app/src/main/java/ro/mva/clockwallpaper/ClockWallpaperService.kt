package ro.mva.clockwallpaper

import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    inner class ClockEngine : Engine(), SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val renderer = ClockRenderer()
        private val data = ClockData()
        private var visible = false
        private var width = 0
        private var height = 0
        private var lastWeatherFetch = 0L
        private var bgBitmap: android.graphics.Bitmap? = null
        private var bgUri: String? = null
        @Volatile private var bgLoading = false

        // ---- lupă (zoom la apăsare) ----
        private var zoomCur = 1f
        private var zoomTarget = 1f
        private var lastFrameMs = 0L
        private var downX = 0f
        private var downY = 0f
        private var lastTouchMs = 0L
        private val touchSlop by lazy { ViewConfiguration.get(this@ClockWallpaperService).scaledTouchSlop * 2f }
        private val zoomOff = Runnable { zoomTarget = 1f }

        // ---- atingere dublă ----
        private var lastTapMs = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var lastSavedSteps = -1
        private var lastCleanDay = ""
        private var lastAlarmCheck = 0L

        private val prefs by lazy { getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE) }
        private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    val animating = zoomTarget > 1f || abs(zoomCur - zoomTarget) > 0.001f
                    val fps = when {
                        animating -> 60
                        isNight() -> 1   // mod economic: un cadru pe secundă
                        else -> prefs.getInt(MainActivity.KEY_FPS, 30).coerceIn(1, 60)
                    }
                    handler.postDelayed(this, (1000L / fps))
                }
            }
        }

        private val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0) data.battery = (level * 100f / scale).roundToInt()
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                data.charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            sticky?.let { batteryReceiver.onReceive(this@ClockWallpaperService, it) }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
            try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
            sensorManager.unregisterListener(this)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                registerSteps()
                maybeFetchWeather()
                handler.post(drawRunnable)
            } else {
                handler.removeCallbacks(drawRunnable)
                handler.removeCallbacks(zoomOff)
                zoomTarget = 1f; zoomCur = 1f
                saveStepHistory(force = true)
                sensorManager.unregisterListener(this)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h
            drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunnable)
        }

        // ------------------- LUPĂ -------------------
        private fun clockGeom(): Triple<Float, Float, Float> {
            val r = min(width, height) * 0.44f * (prefs.getInt(MainActivity.KEY_SCALE, 70) / 100f)
            val cy = height * (prefs.getInt(MainActivity.KEY_POSY, 45) / 100f)
            return Triple(width / 2f, cy, r)
        }

        private fun insideClock(x: Float, y: Float): Boolean {
            val (cx, cy, r) = clockGeom()
            return hypot(x - cx, y - cy) <= r * 1.08f * zoomCur
        }

        private fun zoomMax() = prefs.getInt(MainActivity.KEY_ZOOM, 150).coerceIn(110, 200) / 100f

        private fun kick() {
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.post(drawRunnable)
        }

        override fun onTouchEvent(e: MotionEvent) {
            super.onTouchEvent(e)
            lastTouchMs = System.currentTimeMillis()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                MotionEvent.ACTION_UP -> {
                    // doar atingere scurtă, fără alunecare (nu la apăsare lungă / schimbare pagină)
                    val isTap = e.eventTime - e.downTime < 350 && hypot(e.x - downX, e.y - downY) <= touchSlop
                    if (isTap) onTap(e.x, e.y)
                }
            }
        }

        // Rezervă: unele launchere trimit doar „tap", nu evenimente de atingere
        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == android.app.WallpaperManager.COMMAND_TAP &&
                System.currentTimeMillis() - lastTouchMs > 1500
            ) onTap(x.toFloat(), y.toFloat())
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun zoomIn() {
            zoomTarget = zoomMax()
            handler.removeCallbacks(zoomOff)
            val sec = prefs.getInt(MainActivity.KEY_ZOOM_SEC, 3).coerceIn(1, 15)
            handler.postDelayed(zoomOff, sec * 1000L)
            kick()
        }

        private fun zoomOutNow() {
            handler.removeCallbacks(zoomOff)
            zoomTarget = 1f
            kick()
        }

        /**
         * O atingere pe ceas → se mărește pentru durata din setări.
         * Încă o atingere cât e mărit → revine imediat la normal.
         * Două atingeri foarte rapide (sub 0,4 s) → deschide aplicația aleasă.
         */
        /** Zona vremii de pe cadran (sus, între 12 și centru) — ținta pentru deschiderea aplicației. */
        private fun inWeatherZone(x: Float, y: Float): Boolean {
            val (cx, cy, r) = clockGeom()
            val dy = y - cy
            return kotlin.math.abs(x - cx) < r * 0.34f && dy > -r * 0.62f && dy < -r * 0.14f
        }

        private fun onTap(x: Float, y: Float) {
            if (!insideClock(x, y)) return
            val now = System.currentTimeMillis()
            val launchOn = prefs.getBoolean(MainActivity.KEY_DTAP_ON, true)
            val mode = prefs.getInt(MainActivity.KEY_TAP_MODE, MainActivity.TAP_MODE_ZONE)
            // Mod 0: o singură atingere pe zona vremii (fără conflict cu „atingere dublă = stinge ecranul" de la Samsung)
            if (launchOn && mode == MainActivity.TAP_MODE_ZONE && zoomTarget <= 1f && inWeatherZone(x, y)) {
                launchChosenApp()
                return
            }
            if (launchOn && mode == MainActivity.TAP_MODE_DOUBLE &&
                now - lastTapMs < 400 && hypot(x - lastTapX, y - lastTapY) < touchSlop * 4
            ) {
                lastTapMs = 0L
                zoomOutNow()
                launchChosenApp()
                return
            }
            lastTapMs = now; lastTapX = x; lastTapY = y
            if (!prefs.getBoolean(MainActivity.KEY_ZOOM_ON, true)) return
            if (zoomTarget > 1f) zoomOutNow() else zoomIn()
        }

        /** Vremea: aplicația Samsung/Google dacă e disponibilă, altfel prognoza orașului în browser. */
        private fun weatherIntent(): Intent {
            val pkgs = listOf(
                "com.sec.android.daemonapp",            // Vremea Samsung
                "com.samsung.android.weather",
                "com.google.android.apps.weather"
            )
            for (p in pkgs) {
                packageManager.getLaunchIntentForPackage(p)?.let { return it }
                // aplicația există dar are pictograma ascunsă → căutăm orice activitate principală
                val main = Intent(Intent.ACTION_MAIN).setPackage(p)
                if (main.resolveActivity(packageManager) != null) return main
            }
            val city = prefs.getString(MainActivity.KEY_CITY, "București") ?: "București"
            return Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/search?q=" + android.net.Uri.encode("vremea $city")))
        }

        private fun launchChosenApp() {
            val pkg = prefs.getString(MainActivity.KEY_TAP_APP, "") ?: ""
            if (pkg == MainActivity.TAP_WEATHER) {
                val wi = weatherIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(wi) } catch (_: Exception) {}
                return
            }
            val intent = if (pkg.isNotEmpty()) packageManager.getLaunchIntentForPackage(pkg)
                         else Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            intent ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(intent) } catch (_: Exception) {}
        }

        // ------------------- MOD DE NOAPTE -------------------
        private fun isNight(): Boolean {
            if (!prefs.getBoolean(MainActivity.KEY_NIGHT_ON, true)) return false
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val from = prefs.getInt(MainActivity.KEY_NIGHT_FROM, 23)
            val to = prefs.getInt(MainActivity.KEY_NIGHT_TO, 7)
            return when {
                from == to -> false
                from < to -> h in from until to
                else -> h >= from || h < to
            }
        }

        private fun stepZoom() {
            val now = System.currentTimeMillis()
            val dt = if (lastFrameMs == 0L) 16L else (now - lastFrameMs).coerceIn(1L, 200L)
            lastFrameMs = now
            zoomCur += (zoomTarget - zoomCur) * (1f - exp(-dt / 70f))
            if (abs(zoomCur - zoomTarget) < 0.002f) zoomCur = zoomTarget
        }

        private fun drawFrame() {
            if (width == 0 || height == 0) return
            stepZoom()
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastAlarmCheck > 30_000L) {
                lastAlarmCheck = nowMs
                data.nextAlarm = nextAlarmText()
            }
            val holder = surfaceHolder
            val canvas = try { holder.lockHardwareCanvas() } catch (_: Exception) { null }
                ?: try { holder.lockCanvas() } catch (_: Exception) { null }
                ?: return
            try {
                data.city = prefs.getString(MainActivity.KEY_CITY, "București") ?: "București"
                checkBackground()
                renderer.draw(
                    canvas, width, height, data, System.currentTimeMillis(),
                    scale = prefs.getInt(MainActivity.KEY_SCALE, 70) / 100f,
                    posY = prefs.getInt(MainActivity.KEY_POSY, 45) / 100f,
                    showDigital = prefs.getBoolean(MainActivity.KEY_DIGITAL, true),
                    background = bgBitmap,
                    dim = prefs.getInt(MainActivity.KEY_DIM, 40) / 100f,
                    plate = prefs.getBoolean(MainActivity.KEY_PLATE, true),
                    stepLenCm = prefs.getInt(MainActivity.KEY_STEP_CM, 70),
                    zoom = zoomCur,
                    theme = prefs.getInt(MainActivity.KEY_THEME, 0),
                    night = isNight() && zoomCur <= 1.001f,   // la lupă arătăm normal
                    goal = prefs.getInt(MainActivity.KEY_GOAL, 6000),
                    weightKg = prefs.getInt(MainActivity.KEY_WEIGHT, 80),
                    minimal = prefs.getBoolean(MainActivity.KEY_MINIMAL, false)
                )
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        // ------------------- FUNDAL IMAGINE -------------------
        private fun checkBackground() {
            val uri = prefs.getString(MainActivity.KEY_BG, null)
            if (uri == bgUri || bgLoading) return
            if (uri == null) { bgUri = null; bgBitmap = null; return }
            bgLoading = true
            val w = width; val h = height
            thread {
                val bmp = try { decodeScaled(android.net.Uri.parse(uri), w, h) } catch (_: Exception) { null }
                handler.post {
                    bgBitmap = bmp
                    bgUri = uri
                    bgLoading = false
                }
            }
        }

        private fun decodeScaled(uri: android.net.Uri, reqW: Int, reqH: Int): android.graphics.Bitmap? {
            val cr = contentResolver
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (opts.outWidth / (sample * 2) >= reqW && opts.outHeight / (sample * 2) >= reqH) sample *= 2
            val o2 = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            return cr.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o2) }
        }

        // ------------------- PAȘI -------------------
        private fun registerSteps() {
            val s = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
            try {
                sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
            } catch (_: SecurityException) { /* permisiunea nu a fost acordată */ }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
            val total = event.values[0].toLong()
            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val savedDay = prefs.getString("steps_day", "")
            var base = prefs.getLong("steps_base", -1L)
            // zi nouă sau repornire telefon (contorul se resetează) => bază nouă
            if (savedDay != today || base < 0 || total < base) {
                base = if (savedDay == today && total < base) 0L else total
                prefs.edit().putString("steps_day", today).putLong("steps_base", base).apply()
            }
            data.steps = (total - base).toInt()
            saveStepHistory(force = false)
        }

        /** Istoric pași: cheia hist_yyyyMMdd = pașii acelei zile (folosit de graficul pe 7 zile). */
        private fun saveStepHistory(force: Boolean) {
            if (data.steps < 0) return
            if (!force && lastSavedSteps >= 0 && abs(data.steps - lastSavedSteps) < 10) return
            val day = prefs.getString("steps_day", null) ?: return
            val hh = SimpleDateFormat("HH", Locale.US).format(Date())
            val ed = prefs.edit()
                .putInt("hist_$day", data.steps)
                .putInt("hcum_${day}_$hh", data.steps)   // total cumulat la ora respectivă (grafic pe ore)
            if (day != lastCleanDay) {
                // curățenie o dată pe zi: orele din zilele trecute + istoric mai vechi de 60 de zile
                lastCleanDay = day
                val cut = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) }
                val cutStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(cut.time)
                for (k in prefs.all.keys) {
                    if (k.startsWith("hcum_") && !k.startsWith("hcum_${day}_")) ed.remove(k)
                    if (k.startsWith("hist_") && k.length == 13 && k.substring(5) < cutStr) ed.remove(k)
                }
            }
            ed.apply()
            lastSavedSteps = data.steps
        }

        /** Următoarea alarmă setată în telefon: "06:30" (în 24 h) sau "Jo 06:30". */
        private fun nextAlarmText(): String {
            return try {
                val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val t = am.nextAlarmClock?.triggerTime ?: return ""
                val cal = Calendar.getInstance().apply { timeInMillis = t }
                val hm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                if (t - System.currentTimeMillis() < 24 * 3600_000L) hm
                else arrayOf("", "Du", "Lu", "Ma", "Mi", "Jo", "Vi", "Sâ")[cal.get(Calendar.DAY_OF_WEEK)] + " " + hm
            } catch (_: Exception) { "" }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        // ------------------- VREME (Open-Meteo, fără cheie API) -------------------
        private fun maybeFetchWeather() {
            val now = System.currentTimeMillis()
            if (now - lastWeatherFetch < 20 * 60 * 1000L && data.tempNow != null) return
            lastWeatherFetch = now
            val lat = prefs.getString(MainActivity.KEY_LAT, "44.4268") ?: "44.4268"
            val lon = prefs.getString(MainActivity.KEY_LON, "26.1025") ?: "26.1025"
            thread {
                try {
                    val url = URL(
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                                "&current=temperature_2m,weather_code" +
                                "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation" +
                                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=2"
                    )
                    val con = url.openConnection() as HttpURLConnection
                    con.connectTimeout = 8000; con.readTimeout = 8000
                    val txt = con.inputStream.bufferedReader().use { it.readText() }
                    con.disconnect()
                    val js = JSONObject(txt)
                    val cur = js.getJSONObject("current")
                    val daily = js.getJSONObject("daily")
                    val code = cur.getInt("weather_code")
                    val (icon, desc) = weatherFor(code)

                    // ---- prognoză 3 ore + alertă precipitații în următoarea oră ----
                    val fc = mutableListOf<String>()
                    var alert = ""
                    val hourly = js.optJSONObject("hourly")
                    if (hourly != null) {
                        val times = hourly.getJSONArray("time")
                        val ht = hourly.getJSONArray("temperature_2m")
                        val hc = hourly.getJSONArray("weather_code")
                        val hp = hourly.optJSONArray("precipitation_probability")
                        val hmm = hourly.optJSONArray("precipitation")
                        val curHour = cur.getString("time").take(13)
                        var idx = -1
                        for (i in 0 until times.length()) if (times.getString(i).startsWith(curHour)) { idx = i; break }
                        if (idx >= 0) {
                            for (k in 1..3) {
                                val i = idx + k
                                if (i >= times.length()) break
                                val hh = times.getString(i).substring(11, 13).toInt()
                                fc.add("${hh}h ${weatherFor(hc.getInt(i)).first}${ht.getDouble(i).roundToInt()}°")
                            }
                            if (!isPrecip(code)) {
                                for (k in 0..1) {
                                    val i = idx + k
                                    if (i >= times.length()) break
                                    val prob = hp?.optInt(i, 0) ?: 0
                                    val mm = hmm?.optDouble(i, 0.0) ?: 0.0
                                    if (prob >= 50 || mm >= 0.3) {
                                        val what = if (isSnow(hc.getInt(i))) "Ninsoare" else "Ploaie"
                                        val whenTxt = if (k == 0) "curând" else "~" + times.getString(i).substring(11, 16)
                                        alert = "$what $whenTxt · $prob%"
                                        break
                                    }
                                }
                            }
                        }
                    }
                    handler.post {
                        data.forecast = fc
                        data.rainAlert = alert
                        data.sunrise = daily.optJSONArray("sunrise")?.optString(0, "")?.let { if (it.length >= 16) it.substring(11, 16) else "" } ?: ""
                        data.sunset = daily.optJSONArray("sunset")?.optString(0, "")?.let { if (it.length >= 16) it.substring(11, 16) else "" } ?: ""
                        data.tempNow = cur.getDouble("temperature_2m").roundToInt()
                        data.tempMax = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
                        data.tempMin = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
                        data.weatherIcon = icon
                        data.weatherText = desc
                    }
                } catch (_: Exception) {
                    lastWeatherFetch = 0L // reîncearcă la următoarea afișare
                }
            }
        }

        private fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86
        private fun isPrecip(code: Int) = code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

        private fun weatherFor(code: Int): Pair<String, String> = when (code) {
            0 -> "☀" to "Senin"
            1, 2 -> "⛅" to "Parțial noros"
            3 -> "☁" to "Înnorat"
            45, 48 -> "🌫" to "Ceață"
            51, 53, 55, 56, 57 -> "🌦" to "Burniță"
            61, 63, 65, 66, 67, 80, 81, 82 -> "🌧" to "Ploaie"
            71, 73, 75, 77, 85, 86 -> "❄" to "Ninsoare"
            95, 96, 99 -> "⛈" to "Furtună"
            else -> "☁" to "—"
        }
    }
}
