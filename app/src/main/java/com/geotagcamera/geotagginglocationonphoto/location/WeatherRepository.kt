package com.geotagcamera.geotagginglocationonphoto.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/** One current-weather reading, ready to become a stamp chip like "31°C CLEAR". */
data class WeatherReading(val temperatureC: Double, val weatherCode: Int) {
    val chipText: String
        get() = "${temperatureC.roundToInt()}°C ${describe(weatherCode)}"
}

/**
 * Current weather for the capture point, via Open-Meteo.
 *
 * Open-Meteo is free, keyless, and not Google — same OSM-not-Google posture as
 * the map tiles. Plain [HttpURLConnection] (no new HTTP-client dependency).
 * Strictly opt-in: only called when `StampFields.showWeather` is on, which
 * defaults false. Any failure (offline, timeout, malformed response) returns
 * null and the weather chip simply doesn't render — never a blocked capture.
 * Requires INTERNET (see AndroidManifest).
 */
class WeatherRepository {

    suspend fun current(latitude: Double, longitude: Double): WeatherReading? =
        withContext(Dispatchers.IO) {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${"%.5f".format(Locale.US, latitude)}" +
                "&longitude=${"%.5f".format(Locale.US, longitude)}" +
                "&current=temperature_2m,weather_code"
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 5_000
                    setRequestProperty("User-Agent", "GeoTagCamera/1.1.0")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
                    val current = JSONObject(body).getJSONObject("current")
                    WeatherReading(
                        temperatureC = current.getDouble("temperature_2m"),
                        weatherCode = current.getInt("weather_code")
                    )
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
}

/** WMO weather-interpretation code → short uppercase label for the stamp chip. */
private fun describe(code: Int): String = when (code) {
    0 -> "CLEAR"
    1, 2 -> "PARTLY CLOUDY"
    3 -> "OVERCAST"
    45, 48 -> "FOG"
    51, 53, 55, 56, 57 -> "DRIZZLE"
    61, 63, 65, 66, 67 -> "RAIN"
    71, 73, 75, 77 -> "SNOW"
    80, 81, 82 -> "SHOWERS"
    85, 86 -> "SNOW SHOWERS"
    95 -> "THUNDERSTORM"
    96, 99 -> "THUNDERSTORM"
    else -> "—"
}
