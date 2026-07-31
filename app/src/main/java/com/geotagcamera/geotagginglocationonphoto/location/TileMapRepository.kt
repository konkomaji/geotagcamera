package com.geotagcamera.geotagginglocationonphoto.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.geotagcamera.geotagginglocationonphoto.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Builds the small square map thumbnail the stamp draws ([StampSpec.mapTile]).
 *
 * Deliberately NOT an embedded osmdroid `MapView`: this is just Web-Mercator
 * slippy-tile math plus a fetch-and-composite. It stitches only as many
 * Stadia Maps raster tiles as are needed to cover a square centered on the
 * capture point, crops that square out, and drops a pin at dead center. One
 * bitmap out, no view, no lifecycle.
 *
 * Every fetched tile is cached on disk (its own `map_tiles/` domain, separate
 * from the geocode cache), so the same coordinate+zoom returns byte-identical
 * pixels every time — that determinism is what lets Capture fetch the tile
 * once for the live viewfinder and thread the exact same bitmap into the final
 * burn-in instead of re-fetching at save time.
 *
 * Tiles come from Stadia Maps (OSM data, embedding explicitly licensed for
 * apps). The key is a build-time secret ([BuildConfig.STADIA_API_KEY] fed from
 * gitignored `local.properties`); if it's blank, or any tile fails to fetch,
 * the whole call returns null — the stamp simply renders without a map, never
 * a blocked capture. Requires INTERNET (see AndroidManifest).
 */
class TileMapRepository(context: Context) {

    private val cacheDir: File = File(context.cacheDir, "map_tiles").apply { mkdirs() }
    private val apiKey: String = BuildConfig.STADIA_API_KEY

    /**
     * @param outPx side length of the returned square bitmap, in pixels.
     * @param zoom slippy zoom level (higher = closer). 16 ≈ street level.
     * @return a square map thumbnail centered on (lat, lng) with a center pin,
     *   or null if the API key is missing or no tile could be fetched.
     */
    suspend fun mapThumbnail(
        latitude: Double,
        longitude: Double,
        zoom: Int = 16,
        outPx: Int = 512
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        val n = 1 shl zoom
        val latRad = Math.toRadians(latitude)
        // Fractional tile coordinates (Web Mercator / OSM slippy map).
        val xTile = (longitude + 180.0) / 360.0 * n
        val yTile = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n

        // Work in retina-tile pixel space (each @2x tile is 512px on screen).
        val centerX = xTile * TILE_PX
        val centerY = yTile * TILE_PX
        val half = outPx / 2.0
        val minX = centerX - half
        val minY = centerY - half

        val minTileX = floor(minX / TILE_PX).toInt()
        val minTileY = floor(minY / TILE_PX).toInt()
        val maxTileX = floor((centerX + half) / TILE_PX).toInt()
        val maxTileY = floor((centerY + half) / TILE_PX).toInt()

        val result = Bitmap.createBitmap(outPx, outPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        var drewAny = false

        for (ty in minTileY..maxTileY) {
            if (ty < 0 || ty >= n) continue // above the north / below the south edge — no tile exists
            for (tx in minTileX..maxTileX) {
                val wrappedX = ((tx % n) + n) % n // wrap across the antimeridian
                val tile = fetchTile(zoom, wrappedX, ty) ?: continue
                val destLeft = (tx * TILE_PX - minX).toFloat()
                val destTop = (ty * TILE_PX - minY).toFloat()
                canvas.drawBitmap(tile, destLeft, destTop, paint)
                tile.recycle()
                drewAny = true
            }
        }

        if (!drewAny) {
            result.recycle()
            return@withContext null
        }

        drawPin(canvas, outPx / 2f, outPx / 2f)
        result
    }

    private fun fetchTile(z: Int, x: Int, y: Int): Bitmap? {
        val cacheFile = File(cacheDir, "${STYLE}_${z}_${x}_${y}.png")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it }
        }
        val url = "https://tiles.stadiamaps.com/tiles/$STYLE/$z/$x/$y@2x.png?api_key=$apiKey"
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("User-Agent", "GeoTagCamera/1.1.0")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val bytes = conn.inputStream.use { it.readBytes() }
                runCatching { cacheFile.writeBytes(bytes) } // best-effort cache; ignore write failures
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun drawPin(canvas: Canvas, cx: Float, cy: Float) {
        val r = canvas.width * 0.035f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT_VERIFIED
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, r * 1.6f, ring)
        canvas.drawCircle(cx, cy, r, dot)
    }

    private companion object {
        const val TILE_PX = 512.0 // @2x (retina) tile side in pixels
        const val STYLE = "alidade_smooth" // clean, legible OSM style, good at thumbnail size
        // accent/verified from the design system (oklch(0.76 0.13 162)) in sRGB
        val ACCENT_VERIFIED = Color.rgb(0x14, 0xC3, 0x8C)
    }
}
