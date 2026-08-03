# POS Project Setup & Maintenance Guide

## 1. Project Overview
*   **Project Name:** POS (Point of Sale)
*   **Purpose:** A retail Point of Sale system featuring offline-first local storage, real-time cloud synchronization, barcode scanning, and multi-user access (Admin/Cashier).
*   **Application ID:** `com.example.pos`
*   **Package Name:** `com.example.pos`

---

## 2. Technical Stack
| Component | Technology | Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | 2.0.21 (via Gradle 8.13) |
| **Build System** | Gradle | 8.13 |
| **Android SDK** | Compile/Target: 35 | Min: 23 |
| **UI Framework** | Jetpack Compose / ViewBinding | BOM 2024.12.01 |
| **Database** | Room | 2.7.0-alpha13 |
| **Cloud/Auth** | Firebase (Auth, Firestore, Config) | BOM 33.7.0 |
| **Scanning** | MLKit Barcode Scanning | Latest |
| **Dependency Injection**| KSP (Kotlin Symbol Processing) | Enabled |
| **Async/Sync** | Coroutines / WorkManager | 2.10.0 |

---

## 3. Architecture & Folder Structure
The project follows an offline-first architecture using Room as the source of truth, with `SyncWorker` reconciling data with Firebase Firestore.

### Folder Structure
*   `app/src/main/java/com/example/pos/`
    *   `data/`: Database entities (`Product`, `Sale`), DAOs, and `AppDatabase`.
    *   `sync/`: `SyncWorker` for background/immediate data synchronization.
    *   `barcode/`: MLKit camera and barcode scanning implementations.
    *   `ui/`: UI-related components (Activity/Compose).
    *   `MainActivity.kt`: Entry point after auth.
    *   `LoginActivity.kt`: Google and Email authentication logic.
    *   `CounterActivity.kt`: Core POS checkout and inventory deduction logic.

---

## 4. Database Schema & Migrations
*   **Database Name:** `pos_database`
*   **Current Version:** 6

### Entities
*   **Product:** `id`, `name`, `barcode` (Unique), `description`, `sellingPrice`, `costPrice`, `currentInventory`, `isSynced`, `lastUpdated`.
*   **Sale:** `id`, `timestamp`, `totalAmount`, `amountReceived`, `changeGiven`, `isVoided`, `isSynced`.
*   **SaleItem:** `id`, `saleId` (FK), `productId`, `productName`, `quantity`, `unitPrice`, `subtotal`.

### Migration History
| Version | Change | Implementation Detail |
| :--- | :--- | :--- |
| 5 -> 6 | Added `isSynced`, `lastUpdated` to Products; added sync/payment fields to Sales. | **Safe Migration:** Uses `PRAGMA table_info` to check for existing columns before adding them to prevent "duplicate column" errors. |

---

## 5. Cloud & Authentication
### Firebase Configuration
*   **Project ID:** `pos-80b0432d`
*   **Configuration File:** `app/google-services.json`
*   **Auth Providers:** Email/Password, Google Sign-In.

### Google Sign-In Details
*   **Web Client ID:** `750655070755-qg0gh1sh1527djns6t921qo12r72oln1.apps.googleusercontent.com` (Stored in `strings.xml` as `default_web_client_id`).
*   **OAuth Client ID (Android):** Defined in `google-services.json`.

---

## 6. Build & Signing Configuration
### Signing Keys
*   **Release Key:** `/home/elonmusk/AndroidStudioProjects/releasekey.jks`
*   **Alias:** `appkey`
*   **Credentials:** Managed via `gradle.properties` (`KEYSTORE_PASSWORD`, `KEY_PASSWORD`).

### Fingerprints (Registered in Firebase)
*   **SHA-1 (Release):** `6D:AC:C7:C2:50:4C:89:8E:B3:AC:17:6A:13:91:D2:97:A0:18:00:F5`
*   **SHA-1 (Debug):** `06:9F:D6:86:47:3A:77:03:7D:81:3F:51:EF:5B:37:5F:28:5B:5A:D9`

---

## 7. Synchronization Logic (WorkManager)
*   **Periodic Sync:** Runs every 15 minutes (if network available).
*   **Immediate Sync:** Triggered after sales or product updates.
*   **Logic:**
    1.  Push local unsynced products to Firestore.
    2.  Push local unsynced sales (and items) to Firestore.
    3.  Push local settings (Pins, Admin name).
    4.  Pull remote changes and reconcile based on `lastUpdated` timestamps.

---

## 8. Troubleshooting Guide
### Google Sign-In: Developer Error (10)
*   **Root Cause:** The SHA-1 of the signing certificate is not registered in the Firebase Console.
*   **Solution:** Run `./gradlew signingReport`, copy the SHA-1 for the target variant, add it to Firebase Project Settings, and update `google-services.json`.

### Room: Duplicate Column Name
*   **Root Cause:** Attempting to `ALTER TABLE` to add a column that already exists (often due to out-of-sync dev environments).
*   **Solution:** Use the `Safe Migration` pattern in `AppDatabase.kt` that checks `columnExists()` before executing SQL.

### Gradle: Unresolved reference `isLintVitalAnalyze`
*   **Root Cause:** Property removed in AGP 8.x.
*   **Solution:** Remove the line from `build.gradle.kts`. Use `checkReleaseBuilds = false` in the `lint {}` block instead.

---

## 9. Lessons Learned
| Date | Issue | Root Cause | Solution | Files Modified |
| :--- | :--- | :--- | :--- | :--- |
| 2024-XX | Sync Error | `isLintVitalAnalyze` removed in AGP 8.7. | Deleted property from build script. | `app/build.gradle.kts` |
| 2024-XX | Crash on Migration | `MIGRATION_5_6` tried to add `isSynced` twice. | Implemented `columnExists` check. | `AppDatabase.kt` |
| 2024-XX | Login Error 10 | Missing Debug SHA-1 in Firebase. | Added fingerprint and updated JSON. | `google-services.json` |

---

## 10. Development Workflow
*   **New Migration:** 
    1. Update `@Database(version = X)`. 
    2. Create `MIGRATION_(X-1)_X`.
    3. Use `columnExists` safety checks.
*   **New Dependencies:** Add to `libs.versions.toml` or `build.gradle.kts`. Run `Gradle Sync`.
*   **Barcode Testing:** Use a physical device; MLKit requires a camera and cannot be fully tested on standard emulators.
