package com.geotagcamera.geotagginglocationonphoto

/**
 * Builds a tiny but structurally valid JPEG for byte-level tests:
 * SOI, APP0 (JFIF), a DQT-shaped segment, SOS + a short entropy-coded scan,
 * EOI. Enough for the segment walkers in JpegCanonical / XmpWriter to parse;
 * not a decodable image (no real Huffman/quant data), which these tests
 * never need.
 */
object TestJpeg {

    fun minimal(scan: ByteArray = byteArrayOf(0x11, 0x22, 0x33, 0x44)): ByteArray {
        val out = ArrayList<Int>()
        fun segment(marker: Int, payload: IntArray) {
            out.add(0xFF); out.add(marker)
            val len = payload.size + 2
            out.add((len ushr 8) and 0xFF); out.add(len and 0xFF)
            payload.forEach { out.add(it and 0xFF) }
        }
        out.add(0xFF); out.add(0xD8) // SOI
        segment(0xE0, intArrayOf(0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00)) // APP0 JFIF
        segment(0xDB, intArrayOf(0x00, 0x01)) // DQT-shaped
        segment(0xDA, intArrayOf(0x01, 0x01, 0x00, 0x00, 0x3F, 0x00)) // SOS header
        scan.forEach { out.add(it.toInt() and 0xFF) } // entropy-coded scan
        out.add(0xFF); out.add(0xD9) // EOI
        return ByteArray(out.size) { out[it].toByte() }
    }
}
