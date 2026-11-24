package ro.mva.clockwallpaper

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {

    companion object {
        const val PREFS = "mva_clock"
        const val KEY_CITY = "city"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_FPS = "fps"
        const val KEY_SCALE = "scale"
        const val KEY_POSY = "posy"
        const val KEY_DIGITAL = "digital"
        const val KEY_BG = "bg_uri"
        const val KEY_DIM = "bg_dim"
        const val KEY_PLATE = "bg_plate"
        const val KEY_STEP_CM = "step_cm"
        const val KEY_ZOOM = "zoom_pct"
        const val KEY_ZOOM_ON = "zoom_on"
        const val KEY_ZOOM_SEC = "zoom_sec"
        const val KEY_GOAL = "step_goal"
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_THEME = "theme"
        const val KEY_NIGHT_ON = "night_on"
        const val KEY_NIGHT_FROM = "night_from"
        const val KEY_NIGHT_TO = "night_to"
        const val KEY_DTAP_ON = "dtap_on"
        const val KEY_TAP_APP = "tap_app"
        const val TAP_WEATHER = "__weather__"
        const val KEY_TAP_MODE = "tap_mode"
        const val TAP_MODE_ZONE = 0     // o atingere pe zona vremii
        const val TAP_MODE_DOUBLE = 1   // atingere dublă oriunde pe ceas
        const val REQ_IMAGE = 42
        const val REQ_EXPORT = 43
        const val REQ_IMPORT = 44
        const val KEY_MINIMAL = "minimal"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_EXPORT && resultCode == RESULT_OK) {
            data?.data?.let { exportPrefs(it) }
            return
        }
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK) {
            data?.data?.let { importPrefs(it) }
            return
        }
        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BG, uri.toString()).apply()
            Toast.makeText(this, "Imagine setată ca fundal", Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    // ------------------- PERMISIUNE PAȘI -------------------
    private var permAtStart = true
    private var autoAskedThisSession = false

    private fun stepPermOk(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Deschide direct pagina aplicației din Setări (Permisiuni → Activitate fizică). */
    private fun openAppPermissionSettings() {
        Toast.makeText(this, "Apasă Permisiuni → Activitate fizică → Permite", Toast.LENGTH_LONG).show()
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Cere permisiunea cu fereastra Android. Dacă a fost refuzată definitiv
     * (fereastra nu mai apare), duce direct la setările aplicației.
     */
    private fun requestStepPermission() {
        if (stepPermOk()) {
            Toast.makeText(this, "Permisiunea e deja acordată", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val askedBefore = prefs.getBoolean("perm_asked", false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
            openAppPermissionSettings()
        } else {
            prefs.edit().putBoolean("perm_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permisiune acordată — pașii se vor afișa pe ceas", Toast.LENGTH_SHORT).show()
            permAtStart = true
            recreate()
        } else if (grantResults.isNotEmpty()) {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                openAppPermissionSettings()   // refuz definitiv → direct în Setări
            } else {
                Toast.makeText(this, "Fără permisiune pașii nu pot fi numărați.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Copia internă cu ultimele setări salvate (folosită de „Încarcă setările salvate"). */
    private val snapshotFile get() = java.io.File(filesDir, "setari_salvate.json")

    /** Setările + istoricul pe zile ca JSON (fără fundal și contoare interne). */
    private fun prefsToJson(): org.json.JSONObject {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val js = org.json.JSONObject()
        for ((k, v) in prefs.all) {
            if (k == KEY_BG || k.startsWith("steps_") || k.startsWith("hcum_")) continue
            val o = org.json.JSONObject()
            when (v) {
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is Boolean -> o.put("t", "b").put("v", v)
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            js.put(k, o)
        }
        return js
    }

    private fun applyJson(txt: String) {
        val js = org.json.JSONObject(txt)
        val ed = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (k in js.keys()) {
            val o = js.getJSONObject(k)
            when (o.getString("t")) {
                "i" -> ed.putInt(k, o.getInt("v"))
                "l" -> ed.putLong(k, o.getLong("v"))
                "f" -> ed.putFloat(k, o.getDouble("v").toFloat())
                "b" -> ed.putBoolean(k, o.getBoolean("v"))
                "s" -> ed.putString(k, o.getString("v"))
            }
        }
        ed.commit()
    }

    /** Salvează o copie internă a setărilor curente. */
    private fun saveSnapshot(): Boolean = try {
        snapshotFile.writeText(prefsToJson().toString(2)); true
    } catch (_: Exception) { false }

    /** Încarcă ultimele setări salvate; dacă nu există copie internă, deschide alegerea unui fișier. */
    private fun loadSnapshot() {
        if (!snapshotFile.exists()) {
            Toast.makeText(this, "Nu există setări salvate în aplicație — alege un fișier", Toast.LENGTH_LONG).show()
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQ_IMPORT)
            return
        }
        try {
            applyJson(snapshotFile.readText())
            val whenTxt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US)
                .format(java.util.Date(snapshotFile.lastModified()))
            Toast.makeText(this, "Setări încărcate (salvate la $whenTxt)", Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare la încărcare: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Salvează setările + istoricul pe zile într-un fișier JSON ales de utilizator. */
    private fun exportPrefs(uri: android.net.Uri) {
        try {
            val js = prefsToJson()
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(js.toString(2).toByteArray()) }
            saveSnapshot()
            Toast.makeText(this, "Setări salvate (${js.length()} valori)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare la salvare: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importPrefs(uri: android.net.Uri) {
        try {
            val txt = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: return
            applyJson(txt)
            saveSnapshot()
            Toast.makeText(this, "Setări restaurate", Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "Fișier invalid: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private var histView: StepHistoryView? = null
    private var hourView: HourlyStepsView? = null

    override fun onResume() {
        super.onResume()
        // revenit din Setări cu permisiunea acordată → reîmprospătăm ecranul
        if (!permAtStart && stepPermOk()) { permAtStart = true; recreate(); return }
        // la deschiderea aplicației fără permisiune, o cerem automat (o dată pe sesiune)
        if (!stepPermOk() && !autoAskedThisSession) {
            autoAskedThisSession = true
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("perm_asked", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                requestStepPermission()
            }
        }
        histView?.invalidate()   // graficele se actualizează la revenirea în aplicație
        hourView?.invalidate()
    }

    private fun appName(pkg: String): String {
        if (pkg.isEmpty()) return "Alarme (implicit)"
        if (pkg == TAP_WEATHER) return "Vremea"
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0E1A"))
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        fun label(t: String, size: Float = 14f, color: String = "#9AA4B8") = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.parseColor(color))
            setPadding(0, dp(14), 0, dp(4))
        }

        fun field(value: String, numeric: Boolean) = EditText(this).apply {
            setText(value)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1B2235"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = if (numeric)
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            else InputType.TYPE_CLASS_TEXT
        }

        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2F7BFF"))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) }
            setOnClickListener { onClick() }
        }

        fun section(t: String) = label(t, 15f, "#8FB8FF")

        /** Slider salvat imediat în preferințe. */
        fun slider(key: String, def: Int, lo: Int, hi: Int, step: Int = 1, text: (Int) -> String) {
            val lb = label(text(prefs.getInt(key, def)))
            root.addView(lb)
            root.addView(SeekBar(this).apply {
                max = (hi - lo) / step
                progress = (prefs.getInt(key, def) - lo) / step
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                        val v = lo + p * step
                        lb.text = text(v)
                        prefs.edit().putInt(key, v).apply()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            })
        }

        fun check(t: String, key: String, def: Boolean) = CheckBox(this).apply {
            text = t
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean(key, b).apply() }
        }

        root.addView(TextView(this).apply {
            text = "MVA Clock Wallpaper"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        root.addView(section("ISTORIC PAȘI — ULTIMELE 7 ZILE"))
        histView = StepHistoryView(this)
        root.addView(histView)

        root.addView(section("PAȘI PE ORĂ — AZI"))
        hourView = HourlyStepsView(this)
        root.addView(hourView)

        root.addView(section("AFIȘARE"))
        root.addView(check("Mod „doar ceas” (ascunde vremea, pașii, bateria, luna, alarma)", KEY_MINIMAL, false))

        root.addView(label("Oraș (afișat sub vreme)"))
        val city = field(prefs.getString(KEY_CITY, "București")!!, false); root.addView(city)
        root.addView(label("Latitudine"))
        val lat = field(prefs.getString(KEY_LAT, "44.4268")!!, true); root.addView(lat)
        root.addView(label("Longitudine"))
        val lon = field(prefs.getString(KEY_LON, "26.1025")!!, true); root.addView(lon)

        val fpsLabel = label("Fluiditate secundar: ${prefs.getInt(KEY_FPS, 30)} cadre/s")
        root.addView(fpsLabel)
        val fps = SeekBar(this).apply {
            max = 59
            progress = prefs.getInt(KEY_FPS, 30) - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    fpsLabel.text = "Fluiditate secundar: ${p + 1} cadre/s  (mai puțin = consum mai mic)"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(fps)

        // ---- Mărime ceas (40–100%) ----
        val scaleLabel = label("Mărime ceas: ${prefs.getInt(KEY_SCALE, 70)}%")
        root.addView(scaleLabel)
        val scale = SeekBar(this).apply {
            max = 60
            progress = prefs.getInt(KEY_SCALE, 70) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    scaleLabel.text = "Mărime ceas: ${p + 40}%"
                    prefs.edit().putInt(KEY_SCALE, p + 40).apply()   // se vede imediat
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(scale)

        // ---- Poziție verticală (20–80% din înălțime) ----
        val posLabel = label("Poziție verticală centru: ${prefs.getInt(KEY_POSY, 45)}% (sus → jos)")
        root.addView(posLabel)
        val pos = SeekBar(this).apply {
            max = 60
            progress = prefs.getInt(KEY_POSY, 45) - 20
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    posLabel.text = "Poziție verticală centru: ${p + 20}% (sus → jos)"
                    prefs.edit().putInt(KEY_POSY, p + 20).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(pos)

        val digital = CheckBox(this).apply {
            text = "Arată ora digitală și data deasupra cadranului"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(KEY_DIGITAL, true)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean(KEY_DIGITAL, b).apply() }
        }
        root.addView(digital)

        // ---- Fundal ----
        val bgLabel = label(
            if (prefs.getString(KEY_BG, null) != null) "Fundal: imagine aleasă" else "Fundal: negru"
        )
        root.addView(bgLabel)
        root.addView(button("Alege imagine de fundal din galerie") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }, REQ_IMAGE)
        })
        root.addView(button("Fundal negru (fără imagine)") {
            prefs.edit().remove(KEY_BG).apply()
            bgLabel.text = "Fundal: negru"
        })

        val dimLabel = label("Întunecare imagine: ${prefs.getInt(KEY_DIM, 40)}%")
        root.addView(dimLabel)
        root.addView(SeekBar(this).apply {
            max = 90
            progress = prefs.getInt(KEY_DIM, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    dimLabel.text = "Întunecare imagine: $p%"
                    prefs.edit().putInt(KEY_DIM, p).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(CheckBox(this).apply {
            text = "Cerc întunecat sub cadran (citire mai ușoară)"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(KEY_PLATE, true)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean(KEY_PLATE, b).apply() }
        })

        // ---- Pași & distanță ----
        root.addView(label("PAȘI & DISTANȚĂ", 15f, "#8FB8FF"))
        run {
            val sm = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val hasSensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
            val permOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            val ok = hasSensor && permOk
            root.addView(label(
                "Senzor pași: " + (if (hasSensor) "există ✔" else "NU există ✖") +
                        "   ·   Permisiune: " + (if (permOk) "acordată ✔" else "NEACORDATĂ ✖"),
                13f, if (ok) "#86EFAC" else "#FFB74D"
            ))
            permAtStart = permOk
            if (!permOk) {
                root.addView(button("Acordă permisiunea pentru pași") { requestStepPermission() })
                root.addView(button("Deschide setările de permisiuni") { openAppPermissionSettings() })
            }
        }
        val stepLabel = label("Lungime pas: ${prefs.getInt(KEY_STEP_CM, 70)} cm  (km stânga / metri dreapta)")
        root.addView(stepLabel)
        root.addView(SeekBar(this).apply {
            max = 60 // 40–100 cm
            progress = prefs.getInt(KEY_STEP_CM, 70) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    stepLabel.text = "Lungime pas: ${p + 40} cm  (km stânga / metri dreapta)"
                    prefs.edit().putInt(KEY_STEP_CM, p + 40).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        root.addView(label("Sfat: înălțime × 0,415 ≈ lungimea pasului (ex. 175 cm → 73 cm)", 12f))

        // ---- Lupă ----
        root.addView(label("LUPĂ", 15f, "#8FB8FF"))
        root.addView(CheckBox(this).apply {
            text = "O atingere pe ceas îl mărește; încă o atingere îl readuce la normal"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(KEY_ZOOM_ON, true)
            setOnCheckedChangeListener { _, b -> prefs.edit().putBoolean(KEY_ZOOM_ON, b).apply() }
        })
        val zoomLabel = label("Mărire lupă: ${prefs.getInt(KEY_ZOOM, 150)}%")
        root.addView(zoomLabel)
        root.addView(SeekBar(this).apply {
            max = 90 // 110–200 %
            progress = prefs.getInt(KEY_ZOOM, 150) - 110
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    zoomLabel.text = "Mărire lupă: ${p + 110}%"
                    prefs.edit().putInt(KEY_ZOOM, p + 110).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        slider(KEY_ZOOM_SEC, 3, 1, 15) { "Cât rămâne mărit: $it secunde" }

        // ---- Obiectiv + calorii ----
        root.addView(section("OBIECTIV & CALORII"))
        slider(KEY_GOAL, 6000, 1000, 20000, 500) { "Obiectiv zilnic: $it pași (bara se face verde la atingere)" }
        slider(KEY_WEIGHT, 80, 40, 150) { "Greutate: $it kg (pentru calcul kcal)" }

        // ---- Temă culoare ----
        root.addView(section("TEMĂ CULOARE"))
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val cur = prefs.getInt(KEY_THEME, 0)
            ClockThemes.names.forEachIndexed { i, n ->
                addView(RadioButton(this@MainActivity).apply {
                    id = 1000 + i
                    text = n
                    setTextColor(Color.parseColor(ClockThemes.light[i]))
                    buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(ClockThemes.main[i]))
                    isChecked = i == cur
                })
            }
            setOnCheckedChangeListener { _, id -> prefs.edit().putInt(KEY_THEME, id - 1000).apply() }
        })

        // ---- Mod de noapte ----
        root.addView(section("MOD ECONOMIC DE NOAPTE"))
        root.addView(check("Noaptea: fără secundar, culori estompate, 1 cadru/s", KEY_NIGHT_ON, true))
        slider(KEY_NIGHT_FROM, 23, 0, 23) { "Începe la ora: $it:00" }
        slider(KEY_NIGHT_TO, 7, 0, 23) { "Se termină la ora: $it:00" }

        // ---- Atingere dublă ----
        root.addView(section("DESCHIDE O APLICAȚIE DIN CEAS"))
        root.addView(check("Activat", KEY_DTAP_ON, true))
        root.addView(RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            val cur = prefs.getInt(KEY_TAP_MODE, TAP_MODE_ZONE)
            listOf(
                TAP_MODE_ZONE to "O atingere pe zona vremii (recomandat pe Samsung)",
                TAP_MODE_DOUBLE to "Atingere dublă oriunde pe ceas"
            ).forEach { (m, t) ->
                addView(RadioButton(this@MainActivity).apply {
                    id = 2000 + m
                    text = t
                    setTextColor(Color.WHITE)
                    isChecked = m == cur
                })
            }
            setOnCheckedChangeListener { _, id -> prefs.edit().putInt(KEY_TAP_MODE, id - 2000).apply() }
        })
        root.addView(label(
            "Pentru atingere dublă dezactivează în telefon: Setări → Funcții avansate → " +
                    "Mișcări și gesturi → „Atingeți de 2 ori pentru a opri ecranul”.", 12f
        ))
        val tapLabel = label("Aplicație: ${appName(prefs.getString(KEY_TAP_APP, "") ?: "")}")
        root.addView(tapLabel)
        root.addView(button("Alege aplicația") {
            val pm = packageManager
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
            val names = arrayOf("⏰ Alarme (implicit)", "🌤 Vremea") + apps.map { it.first }
            android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Aplicație la atingere dublă")
                .setItems(names) { _, w ->
                    val pkg = when (w) {
                        0 -> ""
                        1 -> TAP_WEATHER
                        else -> apps[w - 2].second
                    }
                    prefs.edit().putString(KEY_TAP_APP, pkg).apply()
                    tapLabel.text = "Aplicație: ${appName(pkg)}"
                }
                .show()
        })

        // ---- Salvare / restaurare ----
        root.addView(section("SALVARE / RESTAURARE SETĂRI"))
        root.addView(label("„Salvează setările” (jos) păstrează o copie în aplicație; „Încarcă setările salvate” o readuce. " +
                "Fișierul de mai jos e pentru reinstalare sau alt telefon.", 12f))
        root.addView(button("Salvează setările într-un fișier") {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "MvaClock_setari_$stamp.json")
            }, REQ_EXPORT)
        })
        root.addView(button("Restaurează setările din fișier") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQ_IMPORT)
        })
        root.addView(label("Imaginea de fundal trebuie aleasă din nou după reinstalare.", 12f))

        root.addView(button("Salvează setările") {
            prefs.edit()
                .putString(KEY_CITY, city.text.toString().trim())
                .putString(KEY_LAT, lat.text.toString().trim().replace(',', '.'))
                .putString(KEY_LON, lon.text.toString().trim().replace(',', '.'))
                .putInt(KEY_FPS, fps.progress + 1)
                .commit()
            saveSnapshot()
            Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show()
        })

        root.addView(button("Încarcă setările salvate") { loadSnapshot() })

        root.addView(button("Permite numărarea pașilor") { requestStepPermission() })

        root.addView(button("Setează ca wallpaper") {
            try {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, ClockWallpaperService::class.java)
                    )
                })
            } catch (_: Exception) {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        })

        root.addView(label(
            "După setare, telefonul poate întreba: Ecran principal / Ecran de blocare / Ambele. " +
                    "Dacă nu apare opțiunea pentru ecranul de blocare, One UI nu permite live wallpaper acolo.",
            12f
        ))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0E1A"))
            addView(root)
        })
    }
}
