package com.chatcopilot.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chatcopilot.R
import com.chatcopilot.llm.Tone
import com.chatcopilot.service.FloatingService
import com.chatcopilot.service.PreferencesManager

class MainActivity : AppCompatActivity() {

    private val overlayPermissionCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnToggleService = findViewById<Button>(R.id.btnToggleService)
        val btnKnowledgeBase = findViewById<Button>(R.id.btnKnowledgeBase)
        val spinnerTone = findViewById<Spinner>(R.id.spinnerTone)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // API key
        etApiKey.hint = if (PreferencesManager.getApiKey(this) != null)
            "API key configured ✓" else "Enter Claude API key"

        btnSaveKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isNotBlank()) {
                PreferencesManager.saveApiKey(this, key)
                etApiKey.text.clear()
                etApiKey.hint = "API key saved ✓"
                Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            }
        }

        // Tone spinner
        val tones = Tone.values()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            tones.map { it.displayName })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTone.adapter = adapter

        val currentTone = PreferencesManager.getTone(this)
        spinnerTone.setSelection(tones.indexOf(currentTone))

        spinnerTone.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                PreferencesManager.saveTone(this@MainActivity, tones[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Service toggle
        btnToggleService.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                FloatingService.start(this)
                tvStatus.text = "Status: Active — bubble is visible"
                btnToggleService.text = "Stop Assistant"
            } else {
                requestOverlayPermission()
            }
        }

        btnKnowledgeBase.setOnClickListener {
            startActivity(Intent(this, KnowledgeBaseActivity::class.java))
        }

        updateStatus(tvStatus, btnToggleService)
    }

    private fun updateStatus(tvStatus: TextView, btn: Button) {
        if (Settings.canDrawOverlays(this)) {
            tvStatus.text = "Status: Overlay permission granted"
            btn.text = "Start Assistant"
        } else {
            tvStatus.text = "Status: Overlay permission needed"
            btn.text = "Grant Permission & Start"
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, overlayPermissionCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionCode) {
            if (Settings.canDrawOverlays(this)) {
                FloatingService.start(this)
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }
}
