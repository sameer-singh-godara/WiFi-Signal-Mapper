package com.example.wifisignalmapper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var database: AppDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isScanning = false
    private var isApDetectionActive = true
    private var isLocationFetchingActive = true
    private lateinit var job: Job
    private lateinit var locationJob: Job
    private var currentLongitude: Double = 0.0
    private var currentLatitude: Double = 0.0
    private var currentLocationName: String? = null
    private var lastLatitude: Double = 0.0
    private var lastLongitude: Double = 0.0
    private var lastLocationName: String? = null
    private var lastErrorMessage: String? = null
    private var lastButtonEnabledState: Boolean = true
    private var hasPromptedForLocationName = false
    private var lastDetectedApCount: Int = 0
    private val REQUEST_PERMISSIONS_CODE = 1
    private lateinit var cardHeaderText: TextView
    private lateinit var apRecyclerView: RecyclerView
    private lateinit var apAdapter: ApAdapter
    private val SAMPLES_PER_SCAN = 100
    private val LOCATION_REFRESH_INTERVAL = 5000L // 5 seconds
    private val AP_DETECTION_INTERVAL = 500L // 0.5 seconds
    private val ERROR_UPDATE_INTERVAL = 5000L // 5 seconds
    private val TAG = "WiFiSignalMapper"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        database = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        cardHeaderText = findViewById(R.id.cardHeaderText)
        apRecyclerView = findViewById(R.id.apRecyclerView)
        apRecyclerView.layoutManager = LinearLayoutManager(this)
        apAdapter = ApAdapter(mutableListOf())
        apRecyclerView.adapter = apAdapter

        val startStopButton = findViewById<Button>(R.id.startStopButton)
        val viewResultsButton = findViewById<Button>(R.id.viewResultsButton)

        startStopButton.setOnClickListener {
            if (!isScanning) {
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled) {
                    if (isLocationEnabled()) {
                        getLocationAndStartScanning(startStopButton)
                        startStopButton.text = "Stop Scanning"
                        isApDetectionActive = false // Stop AP detection
                        isLocationFetchingActive = false // Pause location fetching
                    } else {
                        displayError("Location services are disabled. Please enable them to scan.")
                        Toast.makeText(this, "Please enable location services to scan.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val missingPermissions = mutableListOf<String>()
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        missingPermissions.add("Location")
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                        missingPermissions.add("Wi-Fi State")
                    if (!wifiManager.isWifiEnabled) {
                        displayError("Wi-Fi is disabled. Please enable it to scan.")
                    } else {
                        Toast.makeText(this, "Missing permissions: ${missingPermissions.joinToString(", ")}. Grant them to scan.", Toast.LENGTH_LONG).show()
                    }
                    apAdapter.updateData(emptyList())
                    if (!hasRequiredPermissions())
                        requestPermissions()
                }
            } else {
                stopScanning()
                startStopButton.text = "Start Scanning"
                isApDetectionActive = true // Resume AP detection
                isLocationFetchingActive = true // Resume location fetching
                startApDetection(startStopButton)
                startLocationUpdates()
            }
        }

        viewResultsButton.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }

        checkInitialPermissions()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun displayError(message: String) {
        if (lastErrorMessage != message) {
            cardHeaderText.text = message
            cardHeaderText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            apAdapter.updateData(emptyList())
            lastErrorMessage = message
        }
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        if (lastButtonEnabledState) {
            startStopButton.isEnabled = false
            lastButtonEnabledState = false
        }
    }

    private fun clearError() {
        lastErrorMessage = null
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        if (!lastButtonEnabledState) {
            startStopButton.isEnabled = true
            lastButtonEnabledState = true
        }
    }

    private fun checkInitialPermissions() {
        if (!hasRequiredPermissions()) {
            requestPermissions()
        } else if (!wifiManager.isWifiEnabled) {
            displayError("Wi-Fi is disabled. Enable it to scan.")
        } else if (!isLocationEnabled()) {
            displayError("Location services are disabled. Please enable them to scan.")
        } else {
            Toast.makeText(this, "You can now scan for WiFi networks!", Toast.LENGTH_SHORT).show()
            updateLocation()
            startLocationUpdates()
            startApDetection(findViewById(R.id.startStopButton))
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        ), REQUEST_PERMISSIONS_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (hasRequiredPermissions() && wifiManager.isWifiEnabled && isLocationEnabled()) {
                Toast.makeText(this, "Permissions granted! You can now scan.", Toast.LENGTH_SHORT).show()
                clearError()
                updateLocation()
                startLocationUpdates()
                startApDetection(startStopButton)
            } else if (!wifiManager.isWifiEnabled) {
                displayError("Wi-Fi is disabled. Please enable it to scan.")
            } else if (!isLocationEnabled()) {
                displayError("Location services are disabled. Please enable them to scan.")
            } else {
                displayError("Location and Wi-Fi permissions denied")
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob = Job()
        CoroutineScope(Dispatchers.IO + locationJob).launch {
            while (isActive) {
                if (!isLocationFetchingActive) {
                    delay(LOCATION_REFRESH_INTERVAL)
                    continue
                }
                withContext(Dispatchers.Main) {
                    updateLocation()
                }
                delay(LOCATION_REFRESH_INTERVAL)
            }
        }
    }

    private fun startApDetection(startStopButton: Button) {
        job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            while (isActive) {
                if (!isApDetectionActive) {
                    delay(AP_DETECTION_INTERVAL)
                    continue
                }
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled && isLocationEnabled()) {
                    wifiManager.startScan()
                    val scanResults = wifiManager.scanResults
                    withContext(Dispatchers.Main) {
                        val locationName = currentLocationName ?: "Unknown Location"
                        lastDetectedApCount = scanResults.size
                        val newText = "You are at: $locationName\n" +
                                "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)}\n\n" +
                                "Detected $lastDetectedApCount APs"
                        if (cardHeaderText.text.toString() != newText) {
                            cardHeaderText.text = newText
                            cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                        }
                        apAdapter.updateData(scanResults)
                        if (scanResults.isNotEmpty()) {
                            if (!lastButtonEnabledState) {
                                startStopButton.isEnabled = true
                                lastButtonEnabledState = true
                            }
                            Log.d(TAG, "APs detected: ${scanResults.size}")
                        } else {
                            if (lastButtonEnabledState) {
                                startStopButton.isEnabled = false
                                lastButtonEnabledState = false
                            }
                            Log.w(TAG, "No APs detected")
                        }
                        clearError()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!wifiManager.isWifiEnabled) {
                            displayError("Wi-Fi is disabled. Please enable it to scan.")
                        } else if (!isLocationEnabled()) {
                            displayError("Location services are disabled. Please enable them to scan.")
                        }
                    }
                    delay(ERROR_UPDATE_INTERVAL)
                    continue
                }
                delay(AP_DETECTION_INTERVAL)
            }
        }
    }

    private fun updateLocation() {
        if (!isLocationEnabled()) {
            currentLatitude = 0.0
            currentLongitude = 0.0
            currentLocationName = null
            hasPromptedForLocationName = false
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val newLatitude = String.format("%.3f", it.latitude).toDouble()
                val newLongitude = String.format("%.3f", it.longitude).toDouble()

                if (isScanning || (Math.abs(newLatitude - lastLatitude) < 0.001 && Math.abs(newLongitude - lastLongitude) < 0.001 && currentLocationName == lastLocationName)) {
                    return@addOnSuccessListener
                }

                currentLatitude = newLatitude
                currentLongitude = newLongitude
                lastLatitude = newLatitude
                lastLongitude = newLongitude

                CoroutineScope(Dispatchers.IO).launch {
                    val locationString = "lat=$currentLatitude,lon=$currentLongitude"
                    val existingLocation = database.wiFiDao().getDataByLocation(locationString)
                    withContext(Dispatchers.Main) {
                        if (existingLocation.isNotEmpty() && existingLocation.first().locationName != null) {
                            currentLocationName = existingLocation.first().locationName
                            lastLocationName = currentLocationName
                            hasPromptedForLocationName = true
                        } else if (!hasPromptedForLocationName) {
                            promptForLocationName()
                            hasPromptedForLocationName = true
                        }
                        val newText = "You are at: ${currentLocationName ?: "Unknown Location"}\n" +
                                "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)}\n\n" +
                                "Detected $lastDetectedApCount APs"
                        if (cardHeaderText.text.toString() != newText) {
                            cardHeaderText.text = newText
                            cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                        }
                        clearError()
                    }
                }
            } ?: run {
                displayError("Unable to get location. Ensure location services are enabled.")
                currentLatitude = 0.0
                currentLongitude = 0.0
                currentLocationName = null
                lastLatitude = 0.0
                lastLongitude = 0.0
                lastLocationName = null
                hasPromptedForLocationName = false
            }
        }
    }

    private fun promptForLocationName() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Location Name")
        val input = android.widget.EditText(this)
        builder.setView(input)

        builder.setPositiveButton("OK") { _, _ ->
            currentLocationName = input.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Unknown Location"
            lastLocationName = currentLocationName
            val newText = "You are at: $currentLocationName\n" +
                    "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)}\n\n" +
                    "Detected $lastDetectedApCount APs"
            if (cardHeaderText.text.toString() != newText) {
                cardHeaderText.text = newText
                cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            }
            clearError()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            currentLocationName = "Unknown Location"
            lastLocationName = currentLocationName
            val newText = "You are at: $currentLocationName\n" +
                    "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)}\n\n" +
                    "Detected $lastDetectedApCount APs"
            if (cardHeaderText.text.toString() != newText) {
                cardHeaderText.text = newText
                cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            }
            clearError()
            dialog.cancel()
        }

        builder.show()
    }

    private fun getLocationAndStartScanning(startStopButton: Button) {
        if (!isLocationEnabled()) {
            displayError("Location services are disabled. Please enable them to scan.")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLatitude = String.format("%.3f", it.latitude).toDouble()
                currentLongitude = String.format("%.3f", it.longitude).toDouble()
                lastLatitude = currentLatitude
                lastLongitude = currentLongitude

                CoroutineScope(Dispatchers.IO).launch {
                    val locationString = "lat=$currentLatitude,lon=$currentLongitude"
                    val existingLocation = database.wiFiDao().getDataByLocation(locationString)
                    withContext(Dispatchers.Main) {
                        if (existingLocation.isNotEmpty() && existingLocation.first().locationName != null) {
                            currentLocationName = existingLocation.first().locationName
                            lastLocationName = currentLocationName
                            hasPromptedForLocationName = true
                        } else if (!hasPromptedForLocationName) {
                            promptForLocationName()
                            hasPromptedForLocationName = true
                        }
                        val newText = "You are at: ${currentLocationName ?: "Unknown Location"}\n" +
                                "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)} (Locked)\n\n" +
                                "Detected $lastDetectedApCount APs"
                        if (cardHeaderText.text.toString() != newText) {
                            cardHeaderText.text = newText
                            cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                        }
                        clearError()
                        startAreaScanning(startStopButton)
                    }
                }
            } ?: run {
                displayError("Unable to get location. Ensure location services are enabled.")
                currentLatitude = 0.0
                currentLongitude = 0.0
                currentLocationName = null
                lastLatitude = 0.0
                lastLongitude = 0.0
                lastLocationName = null
                hasPromptedForLocationName = false
            }
        }
    }

    private fun startAreaScanning(startStopButton: Button) {
        isScanning = true
        isLocationFetchingActive = false // Pause location fetching
        job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            val samplesPerAp = mutableMapOf<String, MutableList<Int>>()
            var totalSamples = 0

            while (isActive && totalSamples < SAMPLES_PER_SCAN) {
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled && isLocationEnabled()) {
                    wifiManager.startScan()
                    val scanResults = wifiManager.scanResults
                    withContext(Dispatchers.Main) {
                        val locationName = currentLocationName ?: "Unknown Location"
                        lastDetectedApCount = scanResults.size
                        val newText = "You are at: $locationName\n" +
                                "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)} (Locked)\n\n" +
                                "Detected $lastDetectedApCount APs\nSampling: $totalSamples/$SAMPLES_PER_SCAN"
                        if (cardHeaderText.text.toString() != newText) {
                            cardHeaderText.text = newText
                            cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                        }
                        apAdapter.updateData(scanResults)
                        if (scanResults.isNotEmpty()) {
                            updateDatabase(scanResults)
                        }
                        clearError()
                    }
                    if (scanResults.isNotEmpty()) {
                        scanResults.forEach { ap ->
                            val bssid = ap.BSSID
                            samplesPerAp.getOrPut(bssid) { mutableListOf() }.add(ap.level)
                        }
                        totalSamples++
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!wifiManager.isWifiEnabled) {
                            displayError("Wi-Fi is disabled. Scanning stopped.")
                        } else if (!isLocationEnabled()) {
                            displayError("Location services are disabled. Scanning stopped.")
                        }
                        lastDetectedApCount = 0
                        stopScanning()
                        startStopButton.text = "Start Scanning"
                        isApDetectionActive = true
                        isLocationFetchingActive = true
                        startApDetection(startStopButton)
                        startLocationUpdates()
                    }
                    break
                }
                delay(1000)
            }

            if (totalSamples >= SAMPLES_PER_SCAN) {
                try {
                    samplesPerAp.forEach { (bssid, rssiList) ->
                        val averageRssi = rssiList.average().toInt()
                        val data = WiFiData(
                            bssid = bssid,
                            apName = null,
                            rssi = averageRssi,
                            timestamp = System.currentTimeMillis(),
                            location = "lat=$currentLatitude,lon=$currentLongitude",
                            locationName = currentLocationName
                        )
                        database.wiFiDao().insert(data)
                        Log.d(TAG, "Saved data for BSSID: $bssid, RSSI: $averageRssi at location: $currentLatitude, $currentLongitude, name: $currentLocationName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save data to database: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    val locationName = currentLocationName ?: "Unknown Location"
                    val newText = "You are at: $locationName\n" +
                            "Lat: ${String.format("%.3f", currentLatitude)}, Lon: ${String.format("%.3f", currentLongitude)}\n\n" +
                            "Scan completed. $totalSamples samples saved."
                    if (cardHeaderText.text.toString() != newText) {
                        cardHeaderText.text = newText
                        cardHeaderText.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
                    }
                    Toast.makeText(this@MainActivity, "Scan complete. Data saved in database.", Toast.LENGTH_SHORT).show()
                    apAdapter.updateData(emptyList())
                    lastDetectedApCount = 0
                    clearError()
                }
                delay(5000L) // Wait 5 seconds before transitioning to non-scanning state
                withContext(Dispatchers.Main) {
                    stopScanning()
                    startStopButton.text = "Start Scanning"
                }
            }
        }
    }

    private fun updateDatabase(scanResults: List<android.net.wifi.ScanResult>) {
        CoroutineScope(Dispatchers.IO).launch {
            scanResults.forEach { result ->
                val data = WiFiData(
                    bssid = result.BSSID,
                    apName = result.SSID,
                    rssi = result.level,
                    timestamp = System.currentTimeMillis(),
                    location = "lat=$currentLatitude,lon=$currentLongitude",
                    locationName = currentLocationName
                )
                database.wiFiDao().insert(data)
                Log.d(TAG, "Updated database: BSSID: ${result.BSSID}, RSSI: ${result.level}, Location: $currentLocationName")
            }
        }
    }

    private fun stopScanning() {
        isScanning = false
        job.cancel()
        isApDetectionActive = true
        isLocationFetchingActive = true
        startApDetection(findViewById(R.id.startStopButton))
        startLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        locationJob.cancel()
    }

    class ApAdapter(private val scanResults: MutableList<android.net.wifi.ScanResult>) :
        RecyclerView.Adapter<ApAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ssidText: TextView = itemView.findViewById(R.id.ssidText)
            val bssidText: TextView = itemView.findViewById(R.id.bssidText)
            val rssiText: TextView = itemView.findViewById(R.id.rssiText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ap_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = scanResults[position]
            holder.ssidText.text = result.SSID.takeIf { it.isNotEmpty() } ?: result.BSSID
            holder.bssidText.text = "BSSID: ${result.BSSID}"
            holder.rssiText.text = "RSSI: ${result.level} dBm"
        }

        override fun getItemCount(): Int = scanResults.size

        fun updateData(newResults: List<android.net.wifi.ScanResult>) {
            scanResults.clear()
            scanResults.addAll(newResults)
            notifyDataSetChanged()
        }
    }
}