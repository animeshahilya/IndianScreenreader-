package com.indian.screenreader

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.indian.screenreader.core.Settings

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_IndianScreenreader)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("IndianScreenreaderPrefs", Context.MODE_PRIVATE)

        // Request runtime permissions for Voice Commands (RECORD_AUDIO) and Emergency SOS (SEND_SMS)
        val missingPermissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }

        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val etEmergencyContact = findViewById<TextInputEditText>(R.id.etEmergencyContact)
        val switchAutoTranslate = findViewById<MaterialSwitch>(R.id.switchAutoTranslate)
        val switchScreenCurtain = findViewById<MaterialSwitch>(R.id.switchScreenCurtain)
        val switchInputHelp = findViewById<MaterialSwitch>(R.id.switchInputHelp)
        val switchDeduplicate = findViewById<MaterialSwitch>(R.id.switchDeduplicate)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val tvSpeechRateLabel = findViewById<TextView>(R.id.tvSpeechRateLabel)
        val sbSpeechRate = findViewById<SeekBar>(R.id.sbSpeechRate)
        val tvSpeechPitchLabel = findViewById<TextView>(R.id.tvSpeechPitchLabel)
        val sbSpeechPitch = findViewById<SeekBar>(R.id.sbSpeechPitch)
        val spinnerTtsLanguage = findViewById<Spinner>(R.id.spinnerTtsLanguage)

        val spinnerGestureSelect = findViewById<Spinner>(R.id.spinnerGestureSelect)
        val spinnerActionSelect = findViewById<Spinner>(R.id.spinnerActionSelect)
        val btnAssignGesture = findViewById<Button>(R.id.btnAssignGesture)

        // Load existing values
        etApiKey.setText(prefs.getString("GEMINI_API_KEY", ""))
        etEmergencyContact.setText(prefs.getString("EMERGENCY_CONTACT_NUMBER", ""))
        switchAutoTranslate.isChecked = prefs.getBoolean("AUTO_TRANSLATE_ENABLED", false)
        switchScreenCurtain.isChecked = prefs.getBoolean("SCREEN_CURTAIN_ENABLED", false)
        switchInputHelp.isChecked = prefs.getBoolean("INPUT_HELP_MODE", false)
        switchDeduplicate.isChecked = prefs.getBoolean("DEDUPLICATE_SPEECH", true)

        // Math.round fixes float rounding drift bug
        val initialRate = prefs.getFloat("SPEECH_RATE", 1.0f)
        val rateProgress = Math.round((initialRate - 0.5f) / 0.1f).coerceIn(0, 20)
        sbSpeechRate.progress = rateProgress
        tvSpeechRateLabel.text = String.format("Speech Rate: %.1fx", 0.5f + rateProgress * 0.1f)

        val initialPitch = prefs.getFloat("SPEECH_PITCH", 1.0f)
        val pitchProgress = Math.round((initialPitch - 0.5f) / 0.1f).coerceIn(0, 15)
        sbSpeechPitch.progress = pitchProgress
        tvSpeechPitchLabel.text = String.format("Speech Pitch: %.1fx", 0.5f + pitchProgress * 0.1f)

        sbSpeechRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + (progress * 0.1f)
                tvSpeechRateLabel.text = String.format("Speech Rate: %.1fx", rate)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbSpeechPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress * 0.1f)
                tvSpeechPitchLabel.text = String.format("Speech Pitch: %.1fx", pitch)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // TTS Regional Language Spinner
        val langNames = Settings.INDIAN_LANGUAGES.map { it.second }
        val langAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, langNames)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTtsLanguage.adapter = langAdapter

        val savedLangCode = prefs.getString("TTS_LOCALE", "default") ?: "default"
        val langIndex = Settings.INDIAN_LANGUAGES.indexOfFirst { it.first == savedLangCode }
        if (langIndex >= 0) spinnerTtsLanguage.setSelection(langIndex)

        // Gesture Remapping Spinners
        val gestureKeys = Settings.GESTURE_NAMES.keys.toList()
        val gestureDisplayNames = gestureKeys.map { Settings.GESTURE_NAMES[it] ?: "Gesture $it" }
        val gestureAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gestureDisplayNames)
        gestureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGestureSelect.adapter = gestureAdapter

        val actionPairs = Settings.GESTURE_ACTIONS
        val actionDisplayNames = actionPairs.map { it.second }
        val actionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, actionDisplayNames)
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActionSelect.adapter = actionAdapter

        btnAssignGesture.setOnClickListener {
            val selectedGestureId = gestureKeys.getOrNull(spinnerGestureSelect.selectedItemPosition) ?: return@setOnClickListener
            val selectedActionKey = actionPairs.getOrNull(spinnerActionSelect.selectedItemPosition)?.first ?: return@setOnClickListener

            Settings.GESTURE_MAP[selectedGestureId] = selectedActionKey
            Settings.saveGestureMap(prefs)
            val gestureName = Settings.GESTURE_NAMES[selectedGestureId]
            val actionName = actionPairs.find { it.first == selectedActionKey }?.second
            Toast.makeText(this, "Mapped $gestureName to $actionName", Toast.LENGTH_SHORT).show()
        }

        val etCustomWord = findViewById<TextInputEditText>(R.id.etCustomWord)
        val etCustomReplacement = findViewById<TextInputEditText>(R.id.etCustomReplacement)
        val btnAddPronunciation = findViewById<Button>(R.id.btnAddPronunciation)

        btnAddPronunciation.setOnClickListener {
            val word = etCustomWord.text.toString().trim().lowercase()
            val replacement = etCustomReplacement.text.toString().trim()
            if (word.isNotBlank() && replacement.isNotBlank()) {
                Settings.PRONUNCIATION_DICT[word] = replacement
                Settings.savePronunciationDict(prefs)
                etCustomWord.setText("")
                etCustomReplacement.setText("")
                Toast.makeText(this, "Added custom pronunciation for '$word'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter both word and spoken pronunciation", Toast.LENGTH_SHORT).show()
            }
        }

        btnSave.setOnClickListener {
            val finalRate = Math.round((0.5f + (sbSpeechRate.progress * 0.1f)) * 10f) / 10f
            val finalPitch = Math.round((0.5f + (sbSpeechPitch.progress * 0.1f)) * 10f) / 10f
            val selectedLangCode = Settings.INDIAN_LANGUAGES.getOrNull(spinnerTtsLanguage.selectedItemPosition)?.first ?: "default"

            val editor = prefs.edit()
            editor.putString("GEMINI_API_KEY", etApiKey.text.toString().trim())
            editor.putString("EMERGENCY_CONTACT_NUMBER", etEmergencyContact.text.toString().trim())
            editor.putBoolean("AUTO_TRANSLATE_ENABLED", switchAutoTranslate.isChecked)
            editor.putBoolean("SCREEN_CURTAIN_ENABLED", switchScreenCurtain.isChecked)
            editor.putBoolean("INPUT_HELP_MODE", switchInputHelp.isChecked)
            editor.putBoolean("DEDUPLICATE_SPEECH", switchDeduplicate.isChecked)
            editor.putFloat("SPEECH_RATE", finalRate)
            editor.putFloat("SPEECH_PITCH", finalPitch)
            editor.putString("TTS_LOCALE", selectedLangCode)

            editor.apply()
            Settings.initFromAndroid(prefs)

            Toast.makeText(this, "Settings Saved. Speech Rate: ${finalRate}x", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val audioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            val phoneStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val statusMsg = when {
                audioGranted && smsGranted && phoneStateGranted -> "Microphone, SMS & Phone State permissions granted."
                audioGranted && smsGranted -> "Microphone & SMS permissions granted."
                audioGranted -> "Microphone permission granted."
                smsGranted -> "SMS permission granted."
                else -> "Permissions denied. Voice commands or SOS features may be limited."
            }
            Toast.makeText(this, statusMsg, Toast.LENGTH_LONG).show()
        }
    }
}
