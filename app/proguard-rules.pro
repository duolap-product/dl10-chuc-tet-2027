# ==============================================================================
# TetViet ProGuard / R8 Rules for Release Optimization & Obfuscation
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Line Numbers & Stack Trace Preservations (Crashlytics De-obfuscation)
# ------------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ------------------------------------------------------------------------------
# 2. App Data Models, Entities & Local Database
# ------------------------------------------------------------------------------
# Preserve all data classes used in Room, Firestore, and serialization
-keep class online.duolap.tetviet.data.model.** { *; }
-keep class online.duolap.tetviet.data.local.** { *; }
-keep class online.duolap.tetviet.data.sync.** { *; }
-keep class online.duolap.tetviet.audio.TetSongRepository { *; }
-keep class online.duolap.tetviet.game.oanquan.** { *; }
-keep class online.duolap.tetviet.game.dapchuot.** { *; }

# Room Database
# Room tạo *_Impl bằng reflection (getDeclaredConstructor), nên phải giữ cả constructor rỗng.
# Thiếu <init>() thì R8 full mode xoá constructor của WorkDatabase_Impl (WorkManager, đi kèm AdMob/UMP)
# và app crash ngay khi khởi động.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ------------------------------------------------------------------------------
# 3. Custom Views & View Binding
# ------------------------------------------------------------------------------
-keep public class online.duolap.tetviet.views.** { *; }
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
    public *** get*();
}
-keepclassmembers class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# ------------------------------------------------------------------------------
# 4. Dagger Hilt & Dependency Injection
# ------------------------------------------------------------------------------
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.**

# ------------------------------------------------------------------------------
# 5. Firebase & Cloud Firestore
# ------------------------------------------------------------------------------
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
}
-dontwarn com.google.firebase.**

# ------------------------------------------------------------------------------
# 6. Google Mobile Ads (AdMob)
# ------------------------------------------------------------------------------
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-keep class com.google.android.gms.ads.mediation.** { *; }
-dontwarn com.google.android.gms.ads.**

# ------------------------------------------------------------------------------
# 7. Google Play In-App Billing & In-App Update
# ------------------------------------------------------------------------------
-keep class com.android.billingclient.** { *; }
-keep class com.google.android.play.core.** { *; }
-dontwarn com.android.billingclient.**
-dontwarn com.google.android.play.core.**

# ------------------------------------------------------------------------------
# 8. Glide (Image Loading & Caching)
# ------------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ------------------------------------------------------------------------------
# 9. Lottie Animation
# ------------------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ------------------------------------------------------------------------------
# 10. General AndroidX & Coroutines Optimizations
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-dontwarn okio.**
