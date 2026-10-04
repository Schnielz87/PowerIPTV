package com.poweriptv.app.data

import android.util.Base64

// Plattform-Helfer: Die Windows-App (Ordner Windows/) bringt eine eigene Variante mit, damit XtreamSource geteilt werden kann.
internal fun decodeBase64Text(s: String): String = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
