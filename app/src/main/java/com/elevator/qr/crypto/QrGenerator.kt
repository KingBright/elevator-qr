package com.elevator.qr.crypto

import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object QrGenerator {
    const val DEFAULT_PREFIX = "UM_&E&21&0&"
    const val DEFAULT_TEMPLATE = "timestamp&7a05ca6d-4b01-11ef-a26b-08c0eb9317d4&auth&2&0&1&1&{0}&{0}$"
    const val DEFAULT_SECRET_KEY = "Remain2017150705"
    const val DEFAULT_IV_KEY = "201707Remain1505"

    fun getNowFormatDate(): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return formatter.format(Date())
    }

    fun encryptAesCbc(plainText: String, key: String, iv: String): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        val ivParameterSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    fun generateElevatorQrCode(
        prefix: String = DEFAULT_PREFIX,
        template: String = DEFAULT_TEMPLATE,
        secretKey: String = DEFAULT_SECRET_KEY,
        ivKey: String = DEFAULT_IV_KEY
    ): Pair<String, String> {
        val currentTimestamp = getNowFormatDate()
        val networkStatus = "0"
        
        var qrCodeContent = template
            .replace("timestamp", currentTimestamp)
            .replace("auth", networkStatus)

        val encryptedContent = encryptAesCbc(qrCodeContent, secretKey, ivKey)
        val finalContent = prefix + encryptedContent
        
        return Pair(finalContent, currentTimestamp)
    }
}
