package com.stash.screensaver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etAddress = findViewById<EditText>(R.id.et_stash_address)
        val etPort = findViewById<EditText>(R.id.et_stash_port)
        val etRetrieveCount = findViewById<EditText>(R.id.et_retrieve_count)
        val etDisplayCount = findViewById<EditText>(R.id.et_display_count)
        val etRefreshDelay = findViewById<EditText>(R.id.et_refresh_delay)
        val etDelayVariance = findViewById<EditText>(R.id.et_delay_variance)
        val etIncludedTags = findViewById<EditText>(R.id.et_included_tags)
        val etExcludedTags = findViewById<EditText>(R.id.et_excluded_tags)
        val etIncludedStudios = findViewById<EditText>(R.id.et_included_studios)
        val cbIncludeChildStudios = findViewById<CheckBox>(R.id.cb_include_child_studios)
        
        val rgGender = findViewById<RadioGroup>(R.id.rg_gender)
        val rbFemale = findViewById<RadioButton>(R.id.rb_female)
        val rbMale = findViewById<RadioButton>(R.id.rb_male)
        
        val rgOrientation = findViewById<RadioGroup>(R.id.rg_orientation)
        val rbPortrait = findViewById<RadioButton>(R.id.rb_portrait)
        val rbLandscape = findViewById<RadioButton>(R.id.rb_landscape)
        val rbBoth = findViewById<RadioButton>(R.id.rb_both)

        val rgResolution = findViewById<RadioGroup>(R.id.rg_resolution)
        val rbResAuto = findViewById<RadioButton>(R.id.rb_res_auto)
        val rbResHigh = findViewById<RadioButton>(R.id.rb_res_high)
        val rbRes1080p = findViewById<RadioButton>(R.id.rb_res_1080p)
        val rbRes4k = findViewById<RadioButton>(R.id.rb_res_4k)

        val rgBgColor = findViewById<RadioGroup>(R.id.rg_bg_color)
        val rbBlack = findViewById<RadioButton>(R.id.rb_black)
        val rbGrey = findViewById<RadioButton>(R.id.rb_grey)
        val rbWhite = findViewById<RadioButton>(R.id.rb_white)
        val rbOther = findViewById<RadioButton>(R.id.rb_other)
        val etCustomBg = findViewById<EditText>(R.id.et_custom_bg_color)
        
        val btnSave = findViewById<Button>(R.id.btn_save)

        val prefs = getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        etAddress.setText(prefs.getString("stash_address", "192.168.1.71"))
        etPort.setText(prefs.getString("stash_port", "9999"))
        etRetrieveCount.setText(prefs.getInt("retrieve_count", 250).toString())
        etDisplayCount.setText(prefs.getInt("display_count", 4).toString())
        etRefreshDelay.setText(prefs.getInt("refresh_delay", 30).toString())
        etDelayVariance.setText(prefs.getInt("delay_variance", 20).toString())
        etIncludedTags.setText(prefs.getString("included_tags", ""))
        etExcludedTags.setText(prefs.getString("excluded_tags", ""))
        etIncludedStudios.setText(prefs.getString("included_studios", ""))
        cbIncludeChildStudios.isChecked = prefs.getBoolean("include_child_studios", true)
        
        val gender = prefs.getString("splash_gender", "female")
        if (gender == "male") rbMale.isChecked = true else rbFemale.isChecked = true
        
        val orientation = prefs.getString("image_orientation", "portrait")
        when (orientation) {
            "landscape" -> rbLandscape.isChecked = true
            "both" -> rbBoth.isChecked = true
            else -> rbPortrait.isChecked = true
        }

        val resMode = prefs.getString("res_mode", "auto")
        when (resMode) {
            "high" -> rbResHigh.isChecked = true
            "1080p" -> rbRes1080p.isChecked = true
            "4k" -> rbRes4k.isChecked = true
            else -> rbResAuto.isChecked = true
        }

        val bgColor = prefs.getString("bg_color_type", "black")
        etCustomBg.setText(prefs.getString("bg_custom_hex", "#000000"))
        when (bgColor) {
            "grey" -> rbGrey.isChecked = true
            "white" -> rbWhite.isChecked = true
            "other" -> {
                rbOther.isChecked = true
                etCustomBg.visibility = View.VISIBLE
            }
            else -> rbBlack.isChecked = true
        }

        rgBgColor.setOnCheckedChangeListener { _, checkedId ->
            etCustomBg.visibility = if (checkedId == R.id.rb_other) View.VISIBLE else View.GONE
        }

        btnSave.setOnClickListener {
            val selectedResMode = when (rgResolution.checkedRadioButtonId) {
                R.id.rb_res_high -> "high"
                R.id.rb_res_1080p -> "1080p"
                R.id.rb_res_4k -> "4k"
                else -> "auto"
            }

            val selectedBgType = when (rgBgColor.checkedRadioButtonId) {
                R.id.rb_grey -> "grey"
                R.id.rb_white -> "white"
                R.id.rb_other -> "other"
                else -> "black"
            }

            prefs.edit().apply {
                putString("stash_address", etAddress.text.toString())
                putString("stash_port", etPort.text.toString())
                putInt("retrieve_count", etRetrieveCount.text.toString().toIntOrNull() ?: 250)
                putInt("display_count", etDisplayCount.text.toString().toIntOrNull() ?: 4)
                putInt("refresh_delay", etRefreshDelay.text.toString().toIntOrNull() ?: 30)
                putInt("delay_variance", etDelayVariance.text.toString().toIntOrNull() ?: 20)
                putString("included_tags", etIncludedTags.text.toString())
                putString("excluded_tags", etExcludedTags.text.toString())
                putString("included_studios", etIncludedStudios.text.toString())
                putBoolean("include_child_studios", cbIncludeChildStudios.isChecked)
                putString("splash_gender", if (rbMale.isChecked) "male" else "female")
                putString("image_orientation", when {
                    rbLandscape.isChecked -> "landscape"
                    rbBoth.isChecked -> "both"
                    else -> "portrait"
                })
                putString("res_mode", selectedResMode)
                putString("bg_color_type", selectedBgType)
                putString("bg_custom_hex", etCustomBg.text.toString())
                apply()
            }

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
