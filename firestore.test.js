const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write users or hotspots", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("hotspots").where("isPublic", "==", true).get());
  await assertFails(
    unauthDb.collection("hotspots").doc("hs1").set({
      ownerId: ALICE_UID,
      isPublic: true,
      name: "Cafe Spot",
      venueType: "cafe",
      address: "123 Main St",
      city: "Dhaka",
      latitude: 23.8103,
      longitude: 90.4125,
      ssid: "CafeWiFi",
      securityType: "WPA2",
      isFree: true,
    })
  );
});

test("User profile: owner can create and update own profile, cross-user read fails, shadow field fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredLanguage: "en",
      darkModeEnabled: false,
      notificationsEnabled: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Cross-user PII read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow field injection rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Valid partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "bn",
      updatedAt: serverTimestamp(),
    })
  );
});

test("User favorites: owner CRUD works, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("favorites")
      .doc("fav1")
      .set({
        userId: ALICE_UID,
        name: "OfficeNet",
        bssid: "aa:bb:cc:dd:ee:ff",
        securityType: "WPA2",
        frequencyMhz: 5180,
        itemType: "wifi_network",
        notes: "Fast 5GHz",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("favorites").get()
  );
});

test("Public hotspots: unfiltered query fails, filtered public query succeeds, non-owner edit fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("hotspots").doc("hs_1").set({
      ownerId: ALICE_UID,
      isPublic: true,
      name: "Central Library WiFi",
      venueType: "library",
      address: "100 Library Rd",
      city: "Dhaka",
      latitude: 23.73,
      longitude: 90.39,
      ssid: "Library_Public",
      securityType: "Open",
      isFree: true,
      notes: "Quiet reading room",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Unfiltered list fails (rules are not filters)
  await assertFails(bobDb.collection("hotspots").get());

  // Filtered public list succeeds
  await assertSucceeds(
    bobDb.collection("hotspots").where("isPublic", "==", true).get()
  );

  // Non-owner update fails
  await assertFails(
    bobDb.collection("hotspots").doc("hs_1").update({
      name: "Hacked Name",
      updatedAt: serverTimestamp(),
    })
  );

  // Owner update succeeds
  await assertSucceeds(
    aliceDb.collection("hotspots").doc("hs_1").update({
      notes: "Updated hours 9am-8pm",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Hotspot reviews and reports: require existing hotspot and enforce ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("hotspots").doc("hs_valid").set({
      ownerId: ALICE_UID,
      isPublic: true,
      name: "Tech Hub",
      venueType: "coworking",
      address: "42 Innovation Way",
      city: "Chattogram",
      latitude: 22.35,
      longitude: 91.78,
      ssid: "TechHub_5G",
      securityType: "WPA3",
      isFree: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Review on non-existent hotspot fails
  await assertFails(
    bobDb.collection("hotspot_reviews").doc("rev_orphan").set({
      userId: BOB_UID,
      authorName: "Bob",
      hotspotId: "nonexistent",
      isPublic: true,
      rating: 5,
      comment: "Great!",
      createdAt: serverTimestamp(),
    })
  );

  // Review on existing hotspot succeeds
  await assertSucceeds(
    bobDb.collection("hotspot_reviews").doc("rev_1").set({
      userId: BOB_UID,
      authorName: "Bob",
      hotspotId: "hs_valid",
      isPublic: true,
      rating: 5,
      comment: "Strong signal and stable latency.",
      createdAt: serverTimestamp(),
    })
  );

  // Report creation succeeds for Bob, Alice cannot read Bob's report
  await assertSucceeds(
    bobDb.collection("hotspot_reports").doc("rep_1").set({
      userId: BOB_UID,
      hotspotId: "hs_valid",
      reason: "wrong_ssid",
      details: "SSID changed to TechHub_Guest",
      status: "open",
      createdAt: serverTimestamp(),
    })
  );

  await assertFails(aliceDb.collection("hotspot_reports").doc("rep_1").get());
  await assertSucceeds(
    bobDb.collection("hotspot_reports").where("userId", "==", BOB_UID).get()
  );
});
