package com.example.wifisignalmapper

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WiFiDao {
    @Insert
    suspend fun insert(data: WiFiData)

    @Query("SELECT * FROM wifi_data WHERE bssid = :bssid")
    suspend fun getDataByBssid(bssid: String): List<WiFiData>

    @Query("UPDATE wifi_data SET apName = :newName WHERE bssid = :bssid AND location = :location")
    suspend fun updateApName(bssid: String, newName: String, location: String)

    @Query("SELECT DISTINCT location FROM wifi_data WHERE location IS NOT NULL")
    suspend fun getAllLocations(): List<String>

    @Query("SELECT * FROM wifi_data WHERE location = :location")
    suspend fun getDataByLocation(location: String): List<WiFiData>
}