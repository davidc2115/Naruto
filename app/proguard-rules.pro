# Les méthodes appelées depuis le code natif (JNI) doivent garder leur nom exact.
-keepclasseswithmembernames class com.opencompanion.app.engine.LlamaBridge {
    native <methods>;
}
-keep class com.opencompanion.app.engine.LlamaBridge$TokenCallback { *; }
-keepclassmembers class * implements com.opencompanion.app.engine.LlamaBridge$TokenCallback {
    public boolean onToken(byte[]);
}

# kotlinx.serialization génère des sérialiseurs par réflexion sur les classes @Serializable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.opencompanion.app.**$$serializer { *; }
-keepclassmembers class com.opencompanion.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.opencompanion.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room génère du code au moment de la compilation (KSP) : rien de spécifique à garder ici,
# mais on protège les entités par prudence si un jour la réflexion est utilisée dessus.
-keep class com.opencompanion.app.data.*Entity { *; }
