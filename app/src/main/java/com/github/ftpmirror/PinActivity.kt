package com.github.ftpmirror

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.util.concurrent.Executor

class PinActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etPin1: EditText
    private lateinit var etPin2: EditText
    private lateinit var btnConfirm: Button
    private lateinit var btnBiometric: Button
    private var isSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        tvTitle = findViewById(R.id.tvTitle)
        etPin1 = findViewById(R.id.etPin1)
        etPin2 = findViewById(R.id.etPin2)
        btnConfirm = findViewById(R.id.btnConfirm)

        // Biometric button (added programmatically if layout does not have it yet)
        btnBiometric = Button(this).apply {
            text = getString(R.string.use_biometric)
            visibility = View.GONE
        }
        // Try to find if we already placed one; otherwise keep programmatic for simplicity
        try {
            val existing = findViewById<Button>(R.id.btnBiometric)
            if (existing != null) btnBiometric = existing
        } catch (_: Exception) {}

        val prefs = securePrefs()
        isSetup = !prefs.contains("pin_hash")

        if (isSetup) {
            tvTitle.text = "Create 6-Digit PIN"
            etPin2.visibility = View.VISIBLE
            btnBiometric.visibility = View.GONE
        } else {
            tvTitle.text = "Enter 6-Digit PIN"
            etPin2.visibility = View.GONE
            maybeShowBiometric()
        }

        val digitsOnly = InputFilter { source, _, _, _, _, _ ->
            if (source == null || source.isEmpty()) source
            else if (source.toString().matches(Regex("[0-9]+"))) source
            else ""
        }
        etPin1.filters = arrayOf(InputFilter.LengthFilter(6), digitsOnly)
        etPin2.filters = arrayOf(InputFilter.LengthFilter(6), digitsOnly)

        btnConfirm.setOnClickListener {
            val pin1 = etPin1.text.toString().trim()
            if (pin1.length != 6) {
                Toast.makeText(this, "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isSetup) {
                val pin2 = etPin2.text.toString().trim()
                if (pin1 != pin2) {
                    Toast.makeText(this, "PINs do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("pin_hash", sha256(pin1)).apply()
                Toast.makeText(this, "PIN set successfully", Toast.LENGTH_LONG).show()
                startMain()
            } else {
                if (sha256(pin1) == prefs.getString("pin_hash", "")) {
                    startMain()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    etPin1.text.clear()
                }
            }
        }

        btnBiometric.setOnClickListener { showBiometricPrompt() }
    }

    private fun securePrefs() = try {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this, "ftp_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        getSharedPreferences("ftp_prefs", MODE_PRIVATE)
    }

    private fun maybeShowBiometric() {
        val can = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can == BiometricManager.BIOMETRIC_SUCCESS) {
            btnBiometric.visibility = View.VISIBLE
            // Auto-prompt for convenience
            showBiometricPrompt()
        }
    }

    private fun showBiometricPrompt() {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                startMain()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Fall back to PIN — do nothing
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
