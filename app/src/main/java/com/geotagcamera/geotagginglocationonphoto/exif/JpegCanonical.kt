package com.geotagcamera.geotagginglocationonphoto.exif

import java.security.MessageDigest

/**
 * The canonical hash basis for tamper-evidence.
 *
 * The problem this solves: our integrity proof (hash + signature + public
 * key) is embedded back INTO the photo, in EXIF UserComment and an XMP APP1
 * segment. If the hash covered the whole file, embedding the proof would
 * change the bytes and the hash would no longer match itself — a chicken and
 * egg. So the hash is computed over a *canonical* view of the JPEG that
 * deliberately excludes every APPn (0xFFE0–0xFFEF) metadata segment — exactly
 * where EXIF and XMP (and therefore the proof) live. Adding, removing, or
 * rewriting any metadata leaves this digest unchanged; changing a single byte
 * of the actual image (quant/Huffman tables, frame header, or the compressed
 * scan) changes it.
 *
 * Pure JVM, no Android dependency, so it is unit-testable directly and both
 * the signer ([com.geotagcamera.geotagginglocationonphoto.security.PhotoIntegrity])
 * and the portable verifier
 * ([com.geotagcamera.geotagginglocationonphoto.security.ProofVerifier]) share
 * this one definition.
 */
object JpegCanonical {

    /** SHA-256 over the APPn-stripped canonical stream (see class docs). */
    fun canonicalDigest(jpeg: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        val n = jpeg.size

        // Not a JPEG we recognise (no SOI) — fall back to hashing the whole
        // thing verbatim. Better a stricter-than-needed hash than a silent skip.
        if (n < 2 || u(jpeg[0]) != 0xFF || u(jpeg[1]) != 0xD8) {
            md.update(jpeg)
            return md.digest()
        }

        md.update(jpeg, 0, 2) // SOI
        var i = 2
        while (i + 1 < n) {
            if (u(jpeg[i]) != 0xFF) { // desync — hash the remainder and stop
                md.update(jpeg, i, n - i)
                break
            }
            when (val marker = u(jpeg[i + 1])) {
                0xD9 -> { // EOI — include it plus any trailing bytes (appended-data tamper)
                    md.update(jpeg, i, n - i)
                    return md.digest()
                }
                0xDA -> { // SOS — entropy-coded scan runs to the end; hash it all
                    md.update(jpeg, i, n - i)
                    return md.digest()
                }
                0x01, in 0xD0..0xD7 -> { // TEM / RSTn — standalone, no length
                    md.update(jpeg, i, 2)
                    i += 2
                }
                in 0xE0..0xEF -> { // APPn — SKIP (EXIF / XMP / the proof itself)
                    if (i + 3 >= n) break
                    i += 2 + segmentLength(jpeg, i)
                }
                else -> { // DQT/DHT/SOF/COM/etc — real structure, INCLUDE
                    if (i + 3 >= n) break
                    val seg = 2 + segmentLength(jpeg, i)
                    if (i + seg > n) { // truncated segment — hash what's left, stop
                        md.update(jpeg, i, n - i)
                        break
                    }
                    md.update(jpeg, i, seg)
                    i += seg
                }
            }
        }
        return md.digest()
    }

    fun sha256Hex(jpeg: ByteArray): String =
        canonicalDigest(jpeg).joinToString("") { "%02x".format(it) }

    /** Length of a length-prefixed segment's payload, from its 2-byte big-endian field (includes those 2 bytes). */
    private fun segmentLength(jpeg: ByteArray, markerIndex: Int): Int =
        (u(jpeg[markerIndex + 2]) shl 8) or u(jpeg[markerIndex + 3])

    private fun u(b: Byte): Int = b.toInt() and 0xFF
}
