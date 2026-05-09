package com.example.nozokima.data.manager

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 256
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 16 // 128 bits

    fun encryptData(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Java's GCM implementation appends the tag to the ciphertext.
        // encryptedBytes = [Ciphertext (n bytes)] + [Tag (16 bytes)]
        
        val tag = encryptedBytes.takeLast(TAG_SIZE).toByteArray()
        val ciphertextOnly = encryptedBytes.dropLast(TAG_SIZE).toByteArray()

        // Output format: [Salt (16B)] + [IV (12B)] + [Auth Tag (16B)] + [Ciphertext]
        val combined = salt + iv + tag + ciphertextOnly
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun decryptData(encryptedData: String, password: String): String {
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        if (combined.size < SALT_SIZE + IV_SIZE + TAG_SIZE) {
            throw IllegalArgumentException("Invalid encrypted data format")
        }

        val salt = combined.sliceArray(0 until SALT_SIZE)
        val iv = combined.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
        val tag = combined.sliceArray(SALT_SIZE + IV_SIZE until SALT_SIZE + IV_SIZE + TAG_SIZE)
        val ciphertextOnly = combined.sliceArray(SALT_SIZE + IV_SIZE + TAG_SIZE until combined.size)

        // Reconstruct [Ciphertext] + [Tag] as expected by Java Cipher
        val encryptedWithTag = ciphertextOnly + tag

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_SIZE * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(encryptedWithTag)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
