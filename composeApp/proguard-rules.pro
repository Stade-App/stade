# =============================================================================
# Stade – Release ProGuard / R8 Kuralları
# =============================================================================

# --------------------------------------------------------------------------
# 1. Android / AndroidX temel keep'ler
# --------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --------------------------------------------------------------------------
# 2. Kotlin
# --------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { <methods>; }
-dontwarn kotlin.**
-dontnote kotlin.**

# --------------------------------------------------------------------------
# 3. Kotlin Coroutines
# --------------------------------------------------------------------------
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.** { *; }
-dontwarn kotlinx.coroutines.**

# --------------------------------------------------------------------------
# 4. Kotlin Serialization
# --------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.stade.**$$serializer { *; }
-keepclassmembers class app.stade.** {
    *** Companion;
}
-keepclasseswithmembers class app.stade.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-dontwarn kotlinx.serialization.**

# --------------------------------------------------------------------------
# 5. Jetpack Compose
# --------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**
# Compose compiler metadata
-keep @androidx.compose.runtime.Composable class *
-keepattributes RuntimeVisibleAnnotations

# --------------------------------------------------------------------------
# 6. BouncyCastle (Şifreleme – hiçbir şeyi obfuscate etme)
# --------------------------------------------------------------------------
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontnote org.bouncycastle.**
# JCA / JCE provider kaydı için reflection gerekli
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.pqc.** { *; }
-keep class org.bouncycastle.crypto.** { *; }

# --------------------------------------------------------------------------
# 7. SQLDelight
# --------------------------------------------------------------------------
-keep class app.stade.db.** { *; }
-keep class app.cash.sqldelight.** { *; }
-keepclassmembers class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
-dontnote app.cash.sqldelight.**

# --------------------------------------------------------------------------
# 8. Ktor Network
# --------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontnote io.ktor.**

# --------------------------------------------------------------------------
# 9. ZXing (QR kodlar)
# --------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-keepclassmembers class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --------------------------------------------------------------------------
# 10. Kotlinx DateTime
# --------------------------------------------------------------------------
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**

# --------------------------------------------------------------------------
# 11. SLF4J (sadece bağımlılık, uyarıları bastır)
# --------------------------------------------------------------------------
-dontwarn org.slf4j.**
-dontnote org.slf4j.**

# --------------------------------------------------------------------------
# 12. Uygulamaya özel keep'ler
#     Vault / SecretStore şifreleme altyapısı: reflection/JCA üzerinden
#     erişildiği için tam korunmalı.
# --------------------------------------------------------------------------
-keep class app.stade.AppContainer { *; }
-keep class app.stade.BootContext { *; }
-keep class app.stade.security.** { *; }
-keep class app.stade.crypto.** { *; }
-keep class app.stade.identity.** { *; }
-keep class app.stade.transport.** { *; }

# Entry point sınıfları
-keep class app.stade.MainActivity { *; }
-keep class app.stade.StadeApplication { *; }
-keep class app.stade.service.StadeService { *; }

# --------------------------------------------------------------------------
# 13. Genel güvenli varsayılanlar
# --------------------------------------------------------------------------
# Stack trace'lerin okunabilir kalması için satır numaralarını koru
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Yansıma (reflection) ile erişilen sınıflar için genel kural
-keepattributes Signature
-keepattributes Exceptions

# JNI metotları
-keepclasseswithmembernames class * {
    native <methods>;
}

# --------------------------------------------------------------------------
# 14. Proguard'ın kendi uyarı bastırmaları
# --------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn sun.misc.**
-dontwarn java.lang.instrument.**
-dontwarn edu.umd.cs.findbugs.**

# java.lang.ProcessHandle Java 9+ API'sidir; Android'de bulunmaz.
# EmbeddedTorManager masaüstü (JVM) kodunda kullanır; Android'de çağrılmaz.
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.ProcessHandle$Info

