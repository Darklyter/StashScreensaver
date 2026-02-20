package com.stash.screensaver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

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
        
        val rgGender = findViewById<RadioGroup>(R.id.rg_gender)
        val rbFemale = findViewById<RadioButton>(R.id.rb_female)
        val rbMale = findViewById<RadioButton>(R.id.rb_male)
        
        val rgOrientation = findViewById<RadioGroup>(R.id.rg_orientation)
        val rbPortrait = findViewById<RadioButton>(R.id.rb_portrait)
        val rbLandscape = findViewById<RadioButton>(R.id.rb_landscape)
        val rbBoth = findViewById<RadioButton>(R.id.rb_both)
        
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
        
        val gender = prefs.getString("splash_gender", "female")
        if (gender == "male") {
            rbMale.isChecked = true
        } else {
            rbFemale.isChecked = true
        }
        
        val orientation = prefs.getString("image_orientation", "portrait")
        when (orientation) {
            "landscape" -> rbLandscape.isChecked = true
            "both" -> rbBoth.isChecked = true
            else -> rbPortrait.isChecked = true
        }

        btnSave.setOnClickListener {
            val address = etAddress.text.toString()
            val port = etPort.text.toString()
            val retrieveCount = etRetrieveCount.text.toString().toIntOrNull() ?: 250
            val displayCount = etDisplayCount.text.toString().toIntOrNull() ?: 4
            val refreshDelay = etRefreshDelay.text.toString().toIntOrNull() ?: 30
            val delayVariance = etDelayVariance.text.toString().toIntOrNull() ?: 20
            val includedTags = etIncludedTags.text.toString()
            val excludedTags = etExcludedTags.text.toString()
            val selectedGender = if (rbMale.isChecked) "male" else "female"
            
            val selectedOrientation = when {
                rbLandscape.isChecked -> "landscape"
                rbBoth.isChecked -> "both"
                else -> "portrait"
            }

            if (address.isBlank() || port.isBlank()) {
                Toast.makeText(this, "Please enter address and port", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().apply {
                putString("stash_address", address)
                putString("stash_port", port)
                putInt("retrieve_count", retrieveCount)
                putInt("display_count", displayCount)
                putInt("refresh_delay", refreshDelay)
                putInt("delay_variance", delayVariance)
                putString("included_tags", includedTags)
                putString("excluded_tags", excludedTags)
                putString("splash_gender", selectedGender)
                putString("image_orientation", selectedOrientation)
                apply()
            }

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
