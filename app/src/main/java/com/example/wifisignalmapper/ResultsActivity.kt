package com.example.wifisignalmapper

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsActivity : AppCompatActivity() {
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        database = AppDatabase.getDatabase(this)
        val listView = findViewById<ListView>(R.id.resultsList)

        CoroutineScope(Dispatchers.IO).launch {
            val results = mutableListOf<String>()
            val uniqueLocations = database.wiFiDao().getAllLocations().distinct()

            if (uniqueLocations.isNotEmpty()) {
                uniqueLocations.forEach { location ->
                    val data = database.wiFiDao().getDataByLocation(location)
                    if (data.isNotEmpty()) {
                        val lat = location.split(",")[0].split("=")[1].toDoubleOrNull() ?: 0.0
                        val lon = location.split(",")[1].split("=")[1].toDoubleOrNull() ?: 0.0
                        results.add("Location: Lat: ${String.format("%.6f", lat)}, Lon: ${String.format("%.6f", lon)}")
                        data.groupBy { it.bssid }.forEach { (bssid, apData) ->
                            val rssiValues = apData.map { it.rssi }
                            val avgRssi = rssiValues.average()
                            val minRssi = rssiValues.minOrNull() ?: 0
                            val maxRssi = rssiValues.maxOrNull() ?: 0
                            results.add(
                                "  AP: ${apData.first().apName ?: bssid} ($bssid):\n" +
                                        "    Samples: ${apData.size}\n" +
                                        "    Average RSSI: ${String.format("%.1f", avgRssi)} dBm\n" +
                                        "    Range: $minRssi to $maxRssi dBm"
                            )
                        }
                    }
                }
            } else {
                results.add("No data available")
            }

            withContext(Dispatchers.Main) {
                listView.adapter = ArrayAdapter(this@ResultsActivity, android.R.layout.simple_list_item_1, results)
            }
        }
    }
}