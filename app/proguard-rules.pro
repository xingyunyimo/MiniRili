# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Keep Room entities
-keep class * extends androidx.room.Entity
-keep @androidx.room.Entity class *

# Keep Room DAOs
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.Query <methods>;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep utility classes
-keep class com.minirili.app.utils.** { *; }