package com.example

import com.example.data.local.OfflineStorageManager
import com.example.data.wifi.ParsedQrPayload
import com.example.data.wifi.QrWifiParser
import com.example.data.wifi.WifiScannerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WiFiGoUnitTests {

    @Test
    fun qrParser_wpa2StandardPayload_parsesCredentials() {
        val parsed = QrWifiParser.parse("WIFI:T:WPA;S:SuccessLife_5G;P:MySecret123;H:false;;")
        assertTrue(parsed is ParsedQrPayload.WifiCredentials)
        val creds = parsed as ParsedQrPayload.WifiCredentials
        assertEquals("SuccessLife_5G", creds.ssid)
        assertEquals("WPA2", creds.securityType)
        assertEquals("MySecret123", creds.password)
        assertFalse(creds.isHidden)
    }

    @Test
    fun qrParser_wpa3HiddenWithEscapedChars_unescapesFieldsCorrectly() {
        val parsed = QrWifiParser.parse("""WIFI:T:SAE;S:Cafe\;WiFi\:Main;P:pass\\word\;1;H:true;;""")
        assertTrue(parsed is ParsedQrPayload.WifiCredentials)
        val creds = parsed as ParsedQrPayload.WifiCredentials
        assertEquals("Cafe;WiFi:Main", creds.ssid)
        assertEquals("WPA3", creds.securityType)
        assertEquals("""pass\word;1""", creds.password)
        assertTrue(creds.isHidden)
    }

    @Test
    fun qrParser_openNetwork_setsNullPassword() {
        val parsed = QrWifiParser.parse("WIFI:T:nopass;S:AirportGuest;;")
        assertTrue(parsed is ParsedQrPayload.WifiCredentials)
        val creds = parsed as ParsedQrPayload.WifiCredentials
        assertEquals("AirportGuest", creds.ssid)
        assertEquals("Open", creds.securityType)
        assertNull(creds.password)
    }

    @Test
    fun qrParser_webUrlAndPlainText_routesAppropriately() {
        val urlPayload = QrWifiParser.parse("  https://www.successlifeit.com/  ")
        assertTrue(urlPayload is ParsedQrPayload.WebUrl)
        assertEquals("https://www.successlifeit.com/", (urlPayload as ParsedQrPayload.WebUrl).url)

        val textPayload = QrWifiParser.parse("Just a voucher code: ABC-123")
        assertTrue(textPayload is ParsedQrPayload.PlainText)
        assertEquals("Just a voucher code: ABC-123", (textPayload as ParsedQrPayload.PlainText).text)
    }

    @Test
    fun wifiHelpers_channelBandSecurity_mapAccurately() {
        assertEquals(1, WifiScannerManager.frequencyToChannel(2412))
        assertEquals(6, WifiScannerManager.frequencyToChannel(2437))
        assertEquals(14, WifiScannerManager.frequencyToChannel(2484))
        assertEquals(36, WifiScannerManager.frequencyToChannel(5180))
        assertEquals(149, WifiScannerManager.frequencyToChannel(5745))
        assertEquals(1, WifiScannerManager.frequencyToChannel(5955))

        assertEquals("2.4 GHz", WifiScannerManager.frequencyToBandLabel(2437))
        assertEquals("5 GHz", WifiScannerManager.frequencyToBandLabel(5180))
        assertEquals("6 GHz", WifiScannerManager.frequencyToBandLabel(5955))

        assertEquals("WPA3", WifiScannerManager.parseSecurityType("[WPA2-SAE-CCMP][ESS]"))
        assertEquals("WPA2", WifiScannerManager.parseSecurityType("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WPA", WifiScannerManager.parseSecurityType("[WPA-PSK-TKIP][ESS]"))
        assertEquals("WEP", WifiScannerManager.parseSecurityType("[WEP][ESS]"))
        assertEquals("OWE (Enhanced Open)", WifiScannerManager.parseSecurityType("[OWE][ESS]"))
        assertEquals("Open", WifiScannerManager.parseSecurityType("[ESS]"))
    }

    @Test
    fun storageHelpers_categorizationSanitizationAndByteFormatting_workCorrectly() {
        assertEquals("Images", OfflineStorageManager.categorizeFile("photo.jpg", "image/jpeg"))
        assertEquals("Videos", OfflineStorageManager.categorizeFile("clip.mp4", "video/mp4"))
        assertEquals("Audio", OfflineStorageManager.categorizeFile("track.mp3", "audio/mpeg"))
        assertEquals("Documents", OfflineStorageManager.categorizeFile("guide.pdf", "application/pdf"))
        assertEquals("Documents", OfflineStorageManager.categorizeFile("page.mhtml", "application/octet-stream"))
        assertEquals("Other", OfflineStorageManager.categorizeFile("archive.zip", "application/zip"))

        assertEquals("my_report_final_.pdf", OfflineStorageManager.sanitizeFileName("  my/report:final?.pdf  "))
        assertEquals("512 B", OfflineStorageManager.formatBytes(512))
        assertEquals("1.5 KB", OfflineStorageManager.formatBytes(1536))
        assertEquals("2.0 MB", OfflineStorageManager.formatBytes(2L * 1024L * 1024L))
    }
}
