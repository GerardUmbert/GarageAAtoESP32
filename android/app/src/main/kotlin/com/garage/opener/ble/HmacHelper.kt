package com.garage.opener.ble

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacHelper {
    fun compute(pin: String, nonce: ByteArray): ByteArray {
        val key = pin.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(nonce)
    }
}
