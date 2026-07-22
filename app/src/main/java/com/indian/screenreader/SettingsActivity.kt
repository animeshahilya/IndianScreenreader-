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

        val tvSpeechRateLabel = findViewById<android.widget.TextView>(R.id.tvSpeechRateLabel)
        val sbSpeechRate = findViewById<android.widget.SeekBar>(R.id.sbSpeechRate)
        val tvSpeechPitchLabel = findViewById<android.widget.TextView>(R.id.tvSpeechPitchLabel)
        val sbSpeechPitch = findViewById<android.widget.SeekBar>(R.id.sbSpeechPitch)

        // Load existing values
        etApiKey.setText(prefs.getString("GEMINI_API_KEY", ""))
        switchAutoTranslate.isChecked = prefs.getBoolean("AUTO_TRANSLATE_ENABLED", false)
        switchScreenCurtain.isChecked = prefs.getBoolean("SCREEN_CURTAIN_ENABLED", false)
        switchInputHelp.isChecked = prefs.getBoolean("INPUT_HELP_MODE", false)
        switchDeduplicate.isChecked = prefs.getBoolean("DEDUPLICATE_SPEECH", true)

        val initialRate = prefs.getFloat("SPEECH_RATE", 1.0f)
        val rateProgress = ((initialRate - 0.5f) / 0.1f).toInt().coerceIn(0, 20)
        sbSpeechRate.progress = rateProgress
        tvSpeechRateLabel.text = String.format("Speech Rate: %.1fx", initialRate)

        val initialPitch = prefs.getFloat("SPEECH_PITCH", 1.0f)
        val pitchProgress = ((initialPitch - 0.5f) / 0.1f).toInt().coerceIn(0, 15)
        sbSpeechPitch.progress = pitchProgress
        tvSpeechPitchLabel.text = String.format("Speech Pitch: %.1fx", initialPitch)

        sbSpeechRate.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress * 0.1f)
                tvSpeechRateLabel.text = String.format("Speech Rate: %.1fx", rate)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        sbSpeechPitch.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress * 0.1f)
                tvSpeechPitchLabel.text = String.format("Speech Pitch: %.1fx", pitch)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        btnSave.setOnClickListener {
            val finalRate = 0.5f + (sbSpeechRate.progress * 0.1f)
            val finalPitch = 0.5f + (sbSpeechPitch.progress * 0.1f)

            val editor = prefs.edit()
            editor.putString("GEMINI_API_KEY", etApiKey.text.toString().trim())
            editor.putBoolean("AUTO_TRANSLATE_ENABLED", switchAutoTranslate.isChecked)
            editor.putBoolean("SCREEN_CURTAIN_ENABLED", switchScreenCurtain.isChecked)
            editor.putBoolean("INPUT_HELP_MODE", switchInputHelp.isChecked)
            editor.putBoolean("BOOLEAN_DEDUPLICATE", switchDeduplicate.isChecked)
            editor.putFloat("SPEECH_RATE", finalRate)
            editor.putFloat("SPEECH_PITCH", finalPitch)
            
            editor.apply()
            
            Toast.makeText(this, "Settings Saved. Rate: ${String.format("%.1f", finalRate)}x", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
