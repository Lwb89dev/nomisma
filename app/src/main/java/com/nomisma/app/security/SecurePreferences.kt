package com.nomisma.app.security

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences whose values are AES-GCM ciphertext. Existing plaintext
 * entries are rewritten on first open and the clear values are removed.
 */
internal class SecurePreferences(context: Context, name: String) {
    private val raw = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val key = DeviceSecrets.secretKey()

    init {
        migratePlaintext()
    }

    fun getString(keyName: String, defaultValue: String?): String? {
        val stored = raw.getString(storedKey(keyName), null) ?: return defaultValue
        return decryptOrNull(stored) ?: defaultValue
    }

    fun getBoolean(keyName: String, defaultValue: Boolean): Boolean {
        val stored = raw.getString(storedKey(keyName), null) ?: return defaultValue
        return when (decryptOrNull(stored)) {
            "1" -> true
            "0" -> false
            else -> defaultValue
        }
    }

    fun edit(): Editor = Editor()

    private fun migratePlaintext() {
        val snapshot = raw.all
        if (snapshot.none { !it.key.startsWith(PREFIX) }) return
        val editor = raw.edit()
        snapshot.forEach { (name, value) ->
            if (name.startsWith(PREFIX)) return@forEach
            val plain = when (value) {
                is String -> value
                is Boolean -> if (value) "1" else "0"
                else -> return@forEach
            }
            editor.putString(storedKey(name), encode(plain))
            editor.remove(name)
        }
        editor.commit()
    }

    private fun encode(value: String): String =
        android.util.Base64.encodeToString(
            AesGcm.encrypt(key, value.toByteArray(Charsets.UTF_8)),
            android.util.Base64.NO_WRAP
        )

    private fun decryptOrNull(stored: String): String? = runCatching {
        val payload = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        AesGcm.decrypt(key, payload).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun storedKey(name: String) = PREFIX + name

    inner class Editor {
        private val editor: SharedPreferences.Editor = raw.edit()

        fun putString(keyName: String, value: String?): Editor {
            editor.remove(keyName)
            if (value == null) editor.remove(storedKey(keyName))
            else editor.putString(storedKey(keyName), encode(value))
            return this
        }

        fun putBoolean(keyName: String, value: Boolean): Editor {
            editor.remove(keyName)
            editor.putString(storedKey(keyName), encode(if (value) "1" else "0"))
            return this
        }

        fun remove(keyName: String): Editor {
            editor.remove(keyName)
            editor.remove(storedKey(keyName))
            return this
        }

        fun apply() {
            editor.apply()
        }
    }

    private companion object {
        const val PREFIX = "n1_"
    }
}

internal fun Context.securePreferences(name: String): SecurePreferences = SecurePreferences(this, name)
