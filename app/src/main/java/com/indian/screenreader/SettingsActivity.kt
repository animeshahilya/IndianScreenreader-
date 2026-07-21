package com.indian.screenreader

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply our beautiful Navy theme
        setTheme(R.style.Theme_IndianScreenreader)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize SharedPreferences
        prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)

        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val switchAutoTranslate = findViewById<MaterialSwitch>(R.id.switchAutoTranslate)
        val switchScreenCurtain = findViewById<MaterialSwitch>(R.id.switchScreenCurtain)
        val switchInputHelp = findViewById<MaterialSwitch>(R.id.switchInputHelp)
        val switchDeduplicate = findViewById<MaterialSwitch>(R.id.switchDeduplicate)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // Load existing values
        etApiKey.setText(prefs.getString("GEMINI_API_KEY", ""))
        switchAutoTranslate.isChecked = prefs.getBoolean("AUTO_TRANSLATE_ENABLED", false)
        switchScreenCurtain.isChecked = prefs.getBoolean("SCREEN_CURTAIN_ENABLED", false)
        switchInputHelp.isChecked = prefs.getBoolean("INPUT_HELP_MODE", false)
        switchDeduplicate.isChecked = prefs.getBoolean("DEDUPLICATE_SPEECH", true)

        btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("GEMINI_API_KEY", etApiKey.text.toString().trim())
            editor.putBoolean("AUTO_TRANSLATE_ENABLED", switchAutoTranslate.isChecked)
            editor.putBoolean("SCREEN_CURTAIN_ENABLED", switchScreenCurtain.isChecked)
            editor.putBoolean("INPUT_HELP_MODE", switchInputHelp.isChecked)
            editor.putBoolean("DEDUPLICATE_SPEECH", switchDeduplicate.isChecked)
            
            // Note: Our SharedPreferences listener in IndianScreenReaderService will instantly catch this
            // and reload the native Settings.kt!
            editor.apply()
            
            Toast.makeText(this, "Settings Saved. Restart Screenreader if necessary.", Toast.LENGTH_SHORT).show()
            finish() // Close the activity elegantly
        }
    }
}
