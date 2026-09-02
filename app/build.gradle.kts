// Voir le commentaire dans le build.gradle.kts racine : support Kotlin intégré à AGP 9, pas de
// plugin 'org.jetbrains.kotlin.android'.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ABIs natifs à empaqueter. Surcharge possible :
//   ./gradlew assembleDebug -PabiFilters=arm64-v8a
val nativeAbiFilters: List<String> =
    (project.findProperty("abiFilters") as String?
        ?: providers.gradleProperty("opencompanion.defaultAbiFilters").getOrElse("arm64-v8a"))
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

android {
    namespace = "com.opencompanion.app"
    compileSdk = 36
    ndkVersion = "28.0.13004108"

    defaultConfig {
        applicationId = "com.opencompanion.app"
        // minSdk 28 (Android 9) et non 26 : constaté à la compilation, le backend Vulkan de
        // llama.cpp appelle vkGetPhysicalDeviceFeatures2 (symbole "core" Vulkan 1.1, pas la
        // variante d'extension *KHR) sans garde — or libvulkan.so ne l'exporte, sur Android,
        // qu'à partir de l'API 28 (confirmé en inspectant les stubs versionnés du NDK :
        // absent des stubs API 24/26/27, présent à partir du 28). En dessous, l'édition de
        // liens échoue ("undefined symbol"), et même en contournant le lien, le chargement de
        // la bibliothèque native planterait au runtime sur un appareil réel trop ancien — ce
        // qui casserait aussi le repli CPU (tout est dans le même .so). Voir
        // docs/VULKAN_NOTES.md. Le CPU reste le repli automatique sur tout appareil API 28+.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(nativeAbiFilters)
        }

        externalNativeBuild {
            cmake {
                // GGML_VULKAN active le backend Vulkan (GPU) dans llama.cpp ; le
                // backend CPU reste toujours compilé en secours (voir cpp/CMakeLists.txt
                // et engine/InferenceEngine.kt pour le repli automatique à l'exécution).
                arguments += listOf(
                    "-DOPENCOMPANION_ENABLE_VULKAN=ON",
                    "-DANDROID_STL=c++_shared",
                    // find_package(SPIRV-Headers) et Vulkan (glslc) cherchent des paquets
                    // installés sur la machine HÔTE qui compile (pas dans le sysroot NDK) :
                    // sans ce réglage, le toolchain Android restreint find_package() au
                    // seul sysroot NDK et échoue à les trouver même s'ils sont installés
                    // (voir docs/VULKAN_NOTES.md pour le détail de l'erreur rencontrée).
                    "-DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH",
                    "-DCMAKE_FIND_ROOT_PATH_MODE_PROGRAM=BOTH",
                    // find_package(Vulkan) détecte bien la version (via vulkan.pc / le pilote),
                    // mais ggml-vulkan.cpp inclut directement <vulkan/vulkan.hpp> sans que le
                    // répertoire d'en-têtes trouvé soit propagé aux flags -I de cette cible
                    // précise. On le passe explicitement : $VULKAN_SDK/include si le Vulkan SDK
                    // LunarG est utilisé (Windows/macOS/CI dédiée), sinon /usr/include qui est
                    // l'emplacement standard de libvulkan-dev sur Debian/Ubuntu.
                    "-DVulkan_INCLUDE_DIR=" + (System.getenv("VULKAN_SDK")?.let { "$it/include" } ?: "/usr/include"),
                )
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.0+"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    packaging {
        // libc++_shared.so est fournie par plusieurs dépendances natives ; on ne garde
        // qu'une seule copie par ABI dans l'APK final.
        jniLibs.pickFirsts.add("**/libc++_shared.so")
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Avec le support Kotlin intégré à AGP 9, jvmTarget suit automatiquement
// compileOptions.targetCompatibility ci-dessus (VERSION_17) — rien à répéter ici.

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Backend "rapide" optionnel : Gemini Nano via AICore (service système Android), exposé
    // par ML Kit GenAI. Purement sur l'appareil (aucun appel réseau au moment de la
    // génération), mais nécessite Google Play Services et n'est réellement disponible que sur
    // les appareils où Gemini Nano est supporté et déjà téléchargé (Pixel récents surtout) —
    // voir docs/MODELES_ET_AICORE.md. Le backend llama.cpp (ci-dessus) reste le moteur
    // principal, garanti 100% local sur n'importe quel appareil : NanoBridge.checkAvailability()
    // détecte l'absence de ce service à l'exécution et ChatViewModel retombe automatiquement
    // sur llama.cpp.
    implementation(libs.mlkit.genai.prompt)
}
