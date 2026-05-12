package com.vojtko.opnsenseadmin

import android.content.Context

data class SavedSession(
    val endpoint: String,
    val apiKey: String,
    val apiSecret: String,
    val rememberDevice: Boolean,
    val ignoreInvalidSsl: Boolean
)

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("opnsense_session", Context.MODE_PRIVATE)

    fun load(): SavedSession {
        return SavedSession(
            endpoint = preferences.getString(KEY_ENDPOINT, "https://router.example.net").orEmpty(),
            apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
            apiSecret = preferences.getString(KEY_API_SECRET, "").orEmpty(),
            rememberDevice = preferences.getBoolean(KEY_REMEMBER_DEVICE, false),
            ignoreInvalidSsl = preferences.getBoolean(KEY_IGNORE_INVALID_SSL, false)
        )
    }

    fun save(session: SavedSession) {
        preferences.edit()
            .putString(KEY_ENDPOINT, session.endpoint)
            .putString(KEY_API_KEY, session.apiKey)
            .putString(KEY_API_SECRET, session.apiSecret)
            .putBoolean(KEY_REMEMBER_DEVICE, session.rememberDevice)
            .putBoolean(KEY_IGNORE_INVALID_SSL, session.ignoreInvalidSsl)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_REMEMBER_DEVICE = "remember_device"
        private const val KEY_IGNORE_INVALID_SSL = "ignore_invalid_ssl"
    }
}
