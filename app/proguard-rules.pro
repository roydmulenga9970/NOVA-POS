# Room
-keep class androidx.room.concurrent.TableStates { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * {
    @androidx.room.Database *;
    @androidx.room.Dao *;
    @androidx.room.Entity *;
    @androidx.room.Fts3 *;
    @androidx.room.Fts4 *;
    @androidx.room.ColumnInfo *;
    @androidx.room.PrimaryKey *;
    @androidx.room.Embedded *;
    @androidx.room.Ignore *;
    @androidx.room.Relation *;
    @androidx.room.Transaction *;
}

# POS Data Models and Exceptions
-keep class com.example.pos.data.** { *; }

# Disable heavy optimizations to save memory
-dontoptimize

# Guava
-dontwarn com.google.common.**
-keep class com.google.common.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
