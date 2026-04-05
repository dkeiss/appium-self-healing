package de.keiss.selfhealing.app.data

data class Connection(
    val id: String,
    val from: String,
    val to: String,
    val departure: String,
    val arrival: String,
    val durationMinutes: Int,
    val transfers: Int,
    val priceEuro: Double,
    val trainTypes: List<String>,
    val legs: List<Leg>
)

data class Leg(
    val from: String,
    val to: String,
    val departure: String,
    val arrival: String,
    val trainType: String,
    val trainNumber: String,
    val platform: String
)
