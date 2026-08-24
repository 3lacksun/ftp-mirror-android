package com.github.ftpmirror

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class PinActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etPin1: EditText
    private lateinit var etPin2: EditText
    private lateinit var btnConfirm: Button
    private var isSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        tvTitle = findViewById(R.id.tvTitle)
        etPin1 = findViewById(R.id.etPin1)
        etPin2 = findViewById(R.id.etPin2)
        btnConfirm = findViewById(R.id.btnConfirm)

        val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE)
        isSetup = !prefs.contains("pin_hash")

        if (isSetup) {
            tvTitle.text = "Create 6-Digit PIN"
            etPin2.visibility = EditText.VISIBLE
        } else {
            tvTitle.text = "Enter 6-Digit PIN"
            etPin2.visibility = EditText.GONE
        }

        val filter = InputFilter { source, _, _, _, _, _ ->
            if (source.toString().isEmpty()) "" else if (source.matches(Regex("[0-9]+"))) source else ""
        }
        etPin1.filters = arrayOf(InputFilter.LengthFilter(6), filter)
        etPin2.filters = arrayOf(InputFilter.LengthFilter(6), filter)

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
                val hash = sha256(pin1)
                prefs.edit().putString("pin_hash", hash).apply()
                Toast.makeText(this, "PIN set successfully", Toast.LENGTH_LONG).show()
                startMainActivity()
            } else {
                val storedHash = prefs.getString("pin_hash", "")
                if (sha256(pin1) == storedHash) {
                    startMainActivity()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    etPin1.text.clear()
                }
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
