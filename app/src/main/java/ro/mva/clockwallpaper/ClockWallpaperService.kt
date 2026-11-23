package ro.mva.clockwallpaper

import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
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

        private val prefs by lazy { getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE) }
        private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    val fps = prefs.getInt(MainActivity.KEY_FPS, 30).coerceIn(1, 60)
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

        private fun drawFrame() {
            if (width == 0 || height == 0) return
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
                    plate = prefs.getBoolean(MainActivity.KEY_PLATE, true)
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
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        // ------------------- VREME (Open-Meteo, fără cheie API) -------------------
        private fun maybeFetchWeather() {
            val now = System.currentTimeMillis()
            if (now - lastWeatherFetch < 30 * 60 * 1000L && data.tempNow != null) return
            lastWeatherFetch = now
            val lat = prefs.getString(MainActivity.KEY_LAT, "44.4268") ?: "44.4268"
            val lon = prefs.getString(MainActivity.KEY_LON, "26.1025") ?: "26.1025"
            thread {
                try {
                    val url = URL(
                        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                                "&current=temperature_2m,weather_code" +
                                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
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
                    handler.post {
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
