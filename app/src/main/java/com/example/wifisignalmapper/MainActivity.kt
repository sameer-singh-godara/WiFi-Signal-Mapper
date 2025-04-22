package com.example.wifisignalmapper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
    private lateinit var job: Job
    private var currentLongitude: Double = 0.0
    private var currentLatitude: Double = 0.0
    private val REQUEST_PERMISSIONS_CODE = 1
    private lateinit var statusText: TextView
    private lateinit var locationText: TextView
    private val SAMPLES_PER_SCAN = 100
    private val TAG = "WiFiSignalMapper"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        database = AppDatabase.getDatabase(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        statusText = findViewById(R.id.statusText)
        locationText = findViewById(R.id.locationText)
        val sampleCountText = findViewById<TextView>(R.id.sampleCountText)
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        val viewResultsButton = findViewById<Button>(R.id.viewResultsButton)

        startStopButton.setOnClickListener {
            if (!isScanning) {
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled) {
                    getLocationAndStartScanning(sampleCountText, startStopButton)
                    startStopButton.text = "Stop Scanning"
                    isApDetectionActive = false
                } else {
                    val missingPermissions = mutableListOf<String>()
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                        missingPermissions.add("Location")
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                        missingPermissions.add("Wi-Fi State")
                    if (!wifiManager.isWifiEnabled)
                        statusText.text = "Wi-Fi is disabled. Please enable it to scan."
                    else
                        Toast.makeText(this, "Missing permissions: ${missingPermissions.joinToString(", ")}. Grant them to scan.", Toast.LENGTH_LONG).show()
                    if (!hasRequiredPermissions())
                        requestPermissions()
                }
            } else {
                stopScanning()
                startStopButton.text = "Start Scanning"
                isApDetectionActive = true
            }
        }

        viewResultsButton.setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }

        checkInitialPermissions()
    }

    private fun checkInitialPermissions() {
        if (!hasRequiredPermissions()) {
            requestPermissions()
        } else if (!wifiManager.isWifiEnabled) {
            val sampleCountText = findViewById<TextView>(R.id.sampleCountText)
            val startStopButton = findViewById<Button>(R.id.startStopButton)
            sampleCountText.text = "Samples scanned: 0"
            startStopButton.isEnabled = false
            statusText.text = "Wi-Fi is disabled. Enable it to detect APs"
        } else {
            Toast.makeText(this, "You can now scan for WiFi networks!", Toast.LENGTH_SHORT).show()
            updateLocation()
            startApDetection(findViewById(R.id.sampleCountText), findViewById(R.id.startStopButton))
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
        val sampleCountText = findViewById<TextView>(R.id.sampleCountText)
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (hasRequiredPermissions() && wifiManager.isWifiEnabled) {
                Toast.makeText(this, "Permissions granted! You can now scan.", Toast.LENGTH_SHORT).show()
                updateLocation()
                startApDetection(sampleCountText, startStopButton)
            } else if (!wifiManager.isWifiEnabled) {
                statusText.text = "Wi-Fi is disabled. Please enable it to scan."
                sampleCountText.text = "Samples scanned: 0"
                startStopButton.isEnabled = false
            } else {
                statusText.text = "Location and Wi-Fi permissions denied"
                sampleCountText.text = "Samples scanned: 0"
                startStopButton.isEnabled = false
            }
        }
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLatitude = it.latitude
                currentLongitude = it.longitude
                locationText.text = "Location: Lat: ${String.format("%.6f", currentLatitude)}, Lon: ${String.format("%.6f", currentLongitude)}"
            } ?: run {
                locationText.text = "Location: Unable to get location"
            }
        }
    }

    private fun startApDetection(sampleCountText: TextView, startStopButton: Button) {
        job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            while (isActive) {
                if (!isApDetectionActive) {
                    delay(1000)
                    continue
                }
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled) {
                    wifiManager.startScan()
                    val scanResults = wifiManager.scanResults
                    withContext(Dispatchers.Main) {
                        if (scanResults.isNotEmpty()) {
                            val apList = scanResults.map { "${it.SSID} (${it.BSSID}) - ${it.level} dBm" }
                            val statusTextContent = "Detected ${scanResults.size} APs\n" + apList.joinToString("\n")
                            statusText.text = statusTextContent
                            sampleCountText.text = "Samples: 0"
                            startStopButton.isEnabled = true
                            Log.d(TAG, "APs detected: ${scanResults.size}")
                            updateDatabase(scanResults)
                        } else {
                            statusText.text = "No APs detected"
                            sampleCountText.text = "Samples: 0"
                            startStopButton.isEnabled = false
                            Log.w(TAG, "No APs detected")
                        }
                    }
                } else if (!wifiManager.isWifiEnabled) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Wi-Fi is disabled. Please enable it to scan."
                        sampleCountText.text = "Samples: 0"
                        startStopButton.isEnabled = false
                    }
                }
                delay(5000)
            }
        }
    }

    private fun getLocationAndStartScanning(sampleCountText: TextView, startStopButton: Button) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                currentLatitude = it.latitude
                currentLongitude = it.longitude
                locationText.text = "Location: Lat: ${String.format("%.6f", currentLatitude)}, Lon: ${String.format("%.6f", currentLongitude)}"
                startAreaScanning(sampleCountText, startStopButton)
            } ?: run {
                statusText.text = "Unable to get location. Ensure location services are enabled."
            }
        }
    }

    private fun startAreaScanning(sampleCountText: TextView, startStopButton: Button) {
        isScanning = true
        job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            val samplesPerAp = mutableMapOf<String, MutableList<Int>>()
            var totalSamples = 0

            while (isActive && totalSamples < SAMPLES_PER_SCAN) {
                if (hasRequiredPermissions() && wifiManager.isWifiEnabled) {
                    wifiManager.startScan()
                    val scanResults = wifiManager.scanResults
                    withContext(Dispatchers.Main) {
                        if (scanResults.isNotEmpty()) {
                            val apList = scanResults.map { "${it.SSID} (${it.BSSID}) - ${it.level} dBm" }
                            val statusTextContent = "Detected ${scanResults.size} APs, Scanning: $totalSamples/$SAMPLES_PER_SCAN samples\n" + apList.joinToString("\n")
                            statusText.text = statusTextContent
                            sampleCountText.text = "Samples: $totalSamples"
                            updateDatabase(scanResults)
                        } else {
                            statusText.text = "No APs detected in current scan"
                        }
                    }
                    if (scanResults.isNotEmpty()) {
                        scanResults.forEach { ap ->
                            val bssid = ap.BSSID
                            samplesPerAp.getOrPut(bssid) { mutableListOf() }.add(ap.level)
                        }
                        totalSamples++
                    }
                } else if (!wifiManager.isWifiEnabled) {
                    withContext(Dispatchers.Main) {
                        statusText.text = "Wi-Fi is disabled. Scanning stopped."
                        stopScanning()
                        startStopButton.text = "Start Scanning"
                        isApDetectionActive = true
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
                            location = "lat=$currentLatitude,lon=$currentLongitude"
                        )
                        database.wiFiDao().insert(data)
                        Log.d(TAG, "Saved data for BSSID: $bssid, RSSI: $averageRssi at location: $currentLatitude, $currentLongitude")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save data to database: ${e.message}")
                }
                withContext(Dispatchers.Main) {
                    statusText.text = "Scan completed. $totalSamples samples saved."
                    startStopButton.text = "Start Scanning"
                    isApDetectionActive = true
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
                    location = "lat=$currentLatitude,lon=$currentLongitude"
                )
                database.wiFiDao().insert(data)
                Log.d(TAG, "Updated database: BSSID: ${result.BSSID}, RSSI: ${result.level}")
            }
        }
    }

    private fun stopScanning() {
        isScanning = false
        job.cancel()
        isApDetectionActive = true
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}