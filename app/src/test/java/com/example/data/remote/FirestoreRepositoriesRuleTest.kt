package com.example.data.remote

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreRepositoriesRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfile_upsertAndObserve_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreUserRepository(firestore, auth)

        val res = withTimeout(TIMEOUT_MS) {
            repo.upsertUserProfile(
                displayName = "Alice Tester",
                email = ALICE_EMAIL,
                preferredLanguage = "en",
                darkModeEnabled = false,
                notificationsEnabled = true
            )
        }
        assertTrue(res.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals("Alice Tester", profile?.displayName)
        assertEquals(uid, profile?.userId)
    }

    @Test
    fun publicHotspots_createAndObserve_succeedsForAuthenticatedUsers() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = FirestoreHotspotRepository(firestore, auth)

        val uniqueName = "CoffeeNet-${UUID.randomUUID().toString().take(8)}"
        val createRes = withTimeout(TIMEOUT_MS) {
            repo.createHotspot(
                name = uniqueName,
                venueType = "cafe",
                address = "101 Tech Park",
                city = "Dhaka",
                latitude = 23.8103,
                longitude = 90.4125,
                ssid = "CoffeeNet_5G",
                securityType = "WPA2",
                isFree = true,
                notes = "Verified fast WiFi"
            )
        }
        assertTrue(createRes.isSuccess)
        val hotspotId = createRes.getOrThrow()

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreHotspotRepository(firestore, auth)
        val hotspots = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePublicHotspots().first { list -> list.any { it.id == hotspotId } }
        }
        assertTrue(hotspots.any { it.id == hotspotId && it.name == uniqueName })

        val reviewRes = withTimeout(TIMEOUT_MS) {
            bobRepo.addReview(
                hotspotId = hotspotId,
                authorName = "Bob",
                rating = 5,
                comment = "Great connection"
            )
        }
        assertTrue(reviewRes.isSuccess)

        val reportRes = withTimeout(TIMEOUT_MS) {
            bobRepo.submitReport(
                hotspotId = hotspotId,
                reason = "wrong_ssid",
                details = "Updated to CoffeeNet_Guest"
            )
        }
        assertTrue(reportRes.isSuccess)
    }

    @Test
    fun userFavorites_crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreUserRepository(firestore, auth)
        val favId = "fav_${UUID.randomUUID().toString().take(8)}"

        val saveRes = withTimeout(TIMEOUT_MS) {
            aliceRepo.saveFavorite(
                favoriteId = favId,
                name = "HomeFiber",
                bssid = "aa:bb:cc:11:22:33",
                securityType = "WPA3",
                frequencyMhz = 5180L,
                itemType = "wifi_network",
                notes = "Primary router"
            )
        }
        assertTrue(saveRes.isSuccess)

        signInTestUser(BOB_EMAIL)
        var denied = false
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("favorites").document(favId)
                    .get().await()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
            denied = true
        }
        assertTrue("Expected cross-user read to fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun userFavorites_unauthenticatedObserve_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreUserRepository(firestore, auth)

        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeFavorites("alice_uid").first()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
            denied = true
        }
        assertTrue("Expected unauthenticated observe to fail with PERMISSION_DENIED", denied)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.wifigo@test.com"
        const val BOB_EMAIL = "bob.wifigo@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
