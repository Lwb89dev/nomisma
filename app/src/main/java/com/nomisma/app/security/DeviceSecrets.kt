package com.nomisma.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed key for data that stays on the device. The SQLCipher
 * passphrase is a random value wrapped by that key, not a user password,
 * so opening the ledger does not add a prompt.
 */
internal object DeviceSecrets {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "nomisma.atrest.v1"
    private const val PASSPHRASE_FILE = "nomisma-db.pass"
    private const val PASSPHRASE_BYTES = 32

    fun databasePassphrase(context: Context): ByteArray {
        val key = secretKey()
        val file = passphraseFile(context)
        if (file.exists()) {
            val wrapped = file.readBytes()
            return AesGcm.decrypt(key, wrapped)
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val wrapped = AesGcm.encrypt(key, passphrase)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        if (temporary.exists()) temporary.delete()
        temporary.writeBytes(wrapped)
        check(temporary.renameTo(file)) { "Could not store the database key" }
        return passphrase
    }

    fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun passphraseFile(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, PASSPHRASE_FILE)
}
