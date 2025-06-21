package com.example.terravue.data.local.entities

import androidx.room.*
import java.util.Date

/**
 * Room entity for caching service data locally
 *
 * This entity represents the database table structure for storing
 * environmental impact information about installed apps.
 */
@Entity(
    tableName = "services",
    indices = [
        Index(value = ["packageName"], unique = true),
        Index(value = ["impactLevel"]),
        Index(value = ["cachedAt"]),
        Index(value = ["lastUsed"])
    ]
)
data class ServiceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "packageName")
    val packageName: String,

    @ColumnInfo(name = "impactLevel")
    val impactLevel: String, // HIGH, MEDIUM, LOW

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "energyConsumption")
    val energyConsumption: String? = null,

    @ColumnInfo(name = "co2Emissions")
    val co2Emissions: String? = null,

    @ColumnInfo(name = "sourceUrl")
    val sourceUrl: String? = null,

    @ColumnInfo(name = "cachedAt")
    val cachedAt: Date,

    @ColumnInfo(name = "lastUsed")
    val lastUsed: Date? = null,

    @ColumnInfo(name = "usageMinutes")
    val usageMinutes: Int = 0,

    @ColumnInfo(name = "dailyCO2Grams")
    val dailyCO2Grams: Double = 0.0,

    @ColumnInfo(name = "dailyEnergyWh")
    val dailyEnergyWh: Double = 0.0,

    @ColumnInfo(name = "isSystemApp")
    val isSystemApp: Boolean = false,

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "userClassification")
    val userClassification: String? = null // User can override impact level
)