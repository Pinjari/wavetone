package com.wavetone.app.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256Test {

    @Test
    fun sha256EmptyVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hash(ByteArray(0)).toHexString()
        )
    }

    @Test
    fun sha256AbcVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hash("abc".encodeToByteArray()).toHexString()
        )
    }

    @Test
    fun sha256LongVector() {
        val msg = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash(msg.encodeToByteArray()).toHexString()
        )
    }

    @Test
    fun hmacRfc4231Case1() {
        val key = ByteArray(20) { 0x0b }
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            Sha256.hmac(key, "Hi There".encodeToByteArray()).toHexString()
        )
    }

    @Test
    fun hmacRfc4231Case2() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            Sha256.hmac("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray()).toHexString()
        )
    }

    @Test
    fun sha256MillionAVector() {
        val data = ByteArray(1_000_000) { 'a'.code.toByte() }
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Sha256.hash(data).toHexString()
        )
    }

    @Test
    fun hmacRfc4231Case6LongKey() {
        val key = ByteArray(131) { 0xaa.toByte() }
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            Sha256.hmac(key, "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray()).toHexString()
        )
    }

    @Test
    fun crc8CheckValue() {
        assertEquals(0xF4.toByte(), Sha256.crc8("123456789".encodeToByteArray()))
    }

    @Test
    fun constantTimeEqualsWorks() {
        assertTrue(Sha256.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(Sha256.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(Sha256.constantTimeEquals(byteArrayOf(1, 2), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun hexRoundTrip() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())
        assertEquals("007f80ff", bytes.toHexString())
        assertTrue(bytes.contentEquals("007f80ff".hexToBytes()))
    }
}
