package com.example.wifisignalmapper

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Button
import android.widget.Toast

class ResultsActivity : AppCompatActivity() {
    private lateinit var database: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var clearButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        database = AppDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.resultsRecyclerView)
        clearButton = findViewById(R.id.clearDatabaseButton)
        recyclerView.layoutManager = LinearLayoutManager(this)

        clearButton.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                database.wiFiDao().clearAll()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ResultsActivity, "Database cleared!", Toast.LENGTH_SHORT).show()
                    refreshData()
                }
            }
        }

        loadData()
    }

    private fun loadData() {
        CoroutineScope(Dispatchers.IO).launch {
            val uniqueLocations = database.wiFiDao().getAllLocations().distinct()
            val locationData = mutableListOf<Pair<String, List<WiFiData>>>()

            if (uniqueLocations.isNotEmpty()) {
                uniqueLocations.forEach { location ->
                    val data = database.wiFiDao().getDataByLocation(location)
                    if (data.isNotEmpty()) {
                        locationData.add(Pair(location, data))
                    }
                }
            } else {
                locationData.add(Pair("", emptyList())) // Placeholder for "No data available"
            }

            withContext(Dispatchers.Main) {
                recyclerView.adapter = ResultsAdapter(locationData)
            }
        }
    }

    private fun refreshData() {
        loadData()
    }
}

class ResultsAdapter(private val locationData: List<Pair<String, List<WiFiData>>>) :
    RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val locationText: android.widget.TextView = itemView.findViewById(R.id.locationText)
        val apContainer: android.widget.LinearLayout = itemView.findViewById(R.id.apContainer)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (location, data) = locationData[position]

        if (data.isEmpty()) {
            holder.locationText.text = "No data available"
            holder.apContainer.removeAllViews()
        } else {
            val lat = location.split(",")[0].split("=")[1].toDoubleOrNull() ?: 0.0
            val lon = location.split(",")[1].split("=")[1].toDoubleOrNull() ?: 0.0
            val locationName = data.first().locationName ?: "Unknown Location"

            // Calculate additional metrics
            val totalAPs = data.groupBy { it.bssid }.size
            val rssiValues = data.map { it.rssi }
            val avgRssi = rssiValues.average()
            val minRssi = rssiValues.minOrNull() ?: 0
            val maxRssi = rssiValues.maxOrNull() ?: 0

            // Update locationText with all details
            holder.locationText.text = "$locationName\n" +
                    "Lat: ${String.format("%.3f", lat)}, Lon: ${String.format("%.3f", lon)}\n" +
                    "Total APs: $totalAPs\n" +
                    "Avg RSSI: ${String.format("%.1f", avgRssi)} dBm\n" +
                    "RSSI Range: $minRssi to $maxRssi dBm"

            holder.apContainer.removeAllViews()
            data.groupBy { it.bssid }.forEach { (bssid, apData) ->
                val apRssiValues = apData.map { it.rssi }
                val apAvgRssi = apRssiValues.average()
                val apMinRssi = apRssiValues.minOrNull() ?: 0
                val apMaxRssi = apRssiValues.maxOrNull() ?: 0

                val apView = android.view.LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_ap_detail, holder.apContainer, false)
                apView.findViewById<android.widget.TextView>(R.id.apNameText).text = "AP: ${apData.first().apName ?: bssid} ($bssid)"
                apView.findViewById<android.widget.TextView>(R.id.apDetailsText).text =
                    "Samples: ${apData.size}\nAverage RSSI: ${String.format("%.1f", apAvgRssi)} dBm\nRange: $apMinRssi to $apMaxRssi dBm"
                holder.apContainer.addView(apView)
            }
        }
    }

    override fun getItemCount(): Int = locationData.size
}