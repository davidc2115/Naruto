// Fichier de build racine : ne fait que déclarer les plugins utilisés par les
// sous-modules (résolus une seule fois grâce à `apply false`).
// AGP 9 fournit directement le support Kotlin (voir developer.android.com/build/migrate-to-built-in-kotlin) :
// pas de plugin 'org.jetbrains.kotlin.android' à appliquer. KSP >= 2.3.1 (fixé dans
// gradle/libs.versions.toml) est requis pour être compatible avec ce mode — voir
// android.builtInKotlin=true dans gradle.properties.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
