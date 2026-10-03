package com.poweriptv.app.data

import java.util.Base64

// Gegenstueck zu app/.../PlatformBase64.kt (Android) fuer die Windows-App.
internal fun decodeBase64Text(s: String): String = String(Base64.getMimeDecoder().decode(s), Charsets.UTF_8)
