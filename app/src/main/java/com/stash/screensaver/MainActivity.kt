package com.stash.screensaver

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tv_status)
        val btnTest = findViewById<Button>(R.id.btn_test_connection)
        val btnSettings = findViewById<Button>(R.id.btn_open_settings)

        btnTest.setOnClickListener {
            tvStatus.text = "Status: Testing connection..."
            scope.launch {
                val result = testStashConnection()
                tvStatus.text = "Status: $result"
            }
        }

        btnSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e2: Exception) {
                    tvStatus.text = "Status: Could not open settings"
                }
            }
        }
    }

    private suspend fun testStashConnection(): String = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        val address = prefs.getString("stash_address", "192.168.1.71")
        val port = prefs.getString("stash_port", "9999")
        val testUrl = "http://$address:$port"

        try {
            val request = Request.Builder()
                .url(testUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "Success! Connected to $address"
                } else {
                    "Server reached at $address but error: ${response.code}"
                }
            }
        } catch (e: Exception) {
            "Error connecting to $address: ${e.localizedMessage ?: "Connection Failed"}"
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
