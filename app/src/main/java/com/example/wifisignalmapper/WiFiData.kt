package com.example.wifisignalmapper

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_data")
data class WiFiData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bssid: String,
    val apName: String?,
    val rssi: Int,
    val timestamp: Long,
    val location: String?
)