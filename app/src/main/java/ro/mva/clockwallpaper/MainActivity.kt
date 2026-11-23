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
        const val REQ_IMAGE = 42
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

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

        root.addView(TextView(this).apply {
            text = "MVA Clock Wallpaper"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        })

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

        root.addView(button("Salvează setările") {
            prefs.edit()
                .putString(KEY_CITY, city.text.toString().trim())
                .putString(KEY_LAT, lat.text.toString().trim().replace(',', '.'))
                .putString(KEY_LON, lon.text.toString().trim().replace(',', '.'))
                .putInt(KEY_FPS, fps.progress + 1)
                .apply()
            Toast.makeText(this, "Salvat", Toast.LENGTH_SHORT).show()
        })

        root.addView(button("Permite numărarea pașilor") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 1)
            } else {
                Toast.makeText(this, "Permisiunea e deja acordată", Toast.LENGTH_SHORT).show()
            }
        })

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
