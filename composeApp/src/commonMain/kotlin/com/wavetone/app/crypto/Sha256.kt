package com.wavetone.app.crypto

/**
 * Pure-Kotlin SHA-256 (FIPS 180-4), HMAC-SHA256 (RFC 2104) and CRC-8
 * (poly 0x07, init 0x00, no reflection — CRC-8/SMBUS). Zero native deps,
 * identical behaviour on Android / iOS / JVM tests.
 */
object Sha256 {

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun hash(data: ByteArray): ByteArray {
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val bitLen = data.size.toLong() * 8
        val paddedLen = ((data.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedLen)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        }

        val w = IntArray(64)
        var chunk = 0
        while (chunk < paddedLen) {
            for (i in 0 until 16) {
                val j = chunk + i * 4
                w[i] = ((padded[j].toInt() and 0xFF) shl 24) or
                    ((padded[j + 1].toInt() and 0xFF) shl 16) or
                    ((padded[j + 2].toInt() and 0xFF) shl 8) or
                    (padded[j + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h0; var b = h1; var c = h2; var d = h3
            var e = h4; var f = h5; var g = h6; var h = h7
            for (i in 0 until 64) {
                val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            h0 += a; h1 += b; h2 += c; h3 += d
            h4 += e; h5 += f; h6 += g; h7 += h
            chunk += 64
        }

        val out = ByteArray(32)
        val hs = intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7)
        for (i in 0 until 8) {
            out[i * 4] = (hs[i] ushr 24).toByte()
            out[i * 4 + 1] = (hs[i] ushr 16).toByte()
            out[i * 4 + 2] = (hs[i] ushr 8).toByte()
            out[i * 4 + 3] = hs[i].toByte()
        }
        return out
    }

    private fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

    /** HMAC-SHA256 per RFC 2104, 64-byte block size. */
    fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        val blockSize = 64
        val effectiveKey = if (key.size > blockSize) hash(key) else key
        val keyBlock = ByteArray(blockSize)
        effectiveKey.copyInto(keyBlock)
        val ipad = ByteArray(blockSize) { (keyBlock[it].toInt() xor 0x36).toByte() }
        val opad = ByteArray(blockSize) { (keyBlock[it].toInt() xor 0x5C).toByte() }
        return hash(opad + hash(ipad + message))
    }

    /** CRC-8/SMBUS: poly 0x07, init 0x00, refin/refout false, xorout 0x00. Check("123456789") = 0xF4. */
    fun crc8(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Byte {
        var crc = 0
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1
                crc = crc and 0xFF
            }
        }
        return crc.toByte()
    }

    /** Constant-time equality for MAC comparison. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()

fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        sb.append(HEX_CHARS[(b.toInt() ushr 4) and 0xF])
        sb.append(HEX_CHARS[b.toInt() and 0xF])
    }
    return sb.toString()
}

fun String.hexToBytes(): ByteArray {
    val clean = replace(" ", "").lowercase()
    require(clean.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(clean.length / 2) { i ->
        val hi = HEX_CHARS.indexOf(clean[i * 2])
        val lo = HEX_CHARS.indexOf(clean[i * 2 + 1])
        require(hi >= 0 && lo >= 0) { "Invalid hex character" }
        ((hi shl 4) or lo).toByte()
    }
}
