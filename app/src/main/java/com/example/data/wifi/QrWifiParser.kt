package com.example.data.wifi

sealed interface ParsedQrPayload {
    data class WifiCredentials(
        val ssid: String,
        val securityType: String,
        val password: String?,
        val isHidden: Boolean,
        val rawValue: String
    ) : ParsedQrPayload

    data class WebUrl(
        val url: String,
        val rawValue: String
    ) : ParsedQrPayload

    data class PlainText(
        val text: String
    ) : ParsedQrPayload
}

object QrWifiParser {

    fun parse(raw: String): ParsedQrPayload {
        val trimmed = raw.trim()
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val body = trimmed.substring(5)
            val tokens = splitEscapedFields(body)
            var ssid = ""
            var security = "Open"
            var password: String? = null
            var hidden = false

            for (token in tokens) {
                if (token.isEmpty()) continue
                val colonIndex = token.indexOf(':')
                if (colonIndex <= 0) continue
                val key = token.substring(0, colonIndex).uppercase()
                val value = unescapeQrValue(token.substring(colonIndex + 1))
                when (key) {
                    "S" -> ssid = value
                    "T" -> security = normalizeSecurity(value)
                    "P" -> password = value.ifEmpty { null }
                    "H" -> hidden = value.equals("true", ignoreCase = true)
                }
            }

            if (ssid.isNotBlank()) {
                return ParsedQrPayload.WifiCredentials(
                    ssid = ssid,
                    securityType = security,
                    password = password,
                    isHidden = hidden,
                    rawValue = trimmed
                )
            }
        }

        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            return ParsedQrPayload.WebUrl(url = trimmed, rawValue = trimmed)
        }

        return ParsedQrPayload.PlainText(text = trimmed)
    }

    private fun normalizeSecurity(rawType: String): String {
        return when (rawType.uppercase()) {
            "WPA", "WPA2", "WPA/WPA2" -> "WPA2"
            "SAE", "WPA3" -> "WPA3"
            "WEP" -> "WEP"
            "NOPASS", "" -> "Open"
            else -> rawType
        }
    }

    private fun splitEscapedFields(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                current.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == ';') {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun unescapeQrValue(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                out.append(value[i + 1])
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }
}
