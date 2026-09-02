cmake_minimum_required(VERSION 3.22)
project(opencompanion_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------------------------------------------------------------------------
# Backends
#
# Le backend CPU de ggml est TOUJOURS compilé : c'est le filet de sécurité
# utilisé par InferenceEngine.kt quand Vulkan est indisponible, désactivé par
# l'utilisateur, ou que le pilote du GPU plante à l'exécution (voir
# docs/VULKAN_NOTES.md). Le backend Vulkan est compilé en plus quand
# OPENCOMPANION_ENABLE_VULKAN=ON (réglage par défaut, voir app/build.gradle.kts).
# ---------------------------------------------------------------------------
option(OPENCOMPANION_ENABLE_VULKAN "Compiler le backend Vulkan de ggml" ON)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/llama.cpp")

if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp introuvable dans ${LLAMA_DIR}.\n"
        "Le sous-module git n'est pas initialisé : lancez\n"
        "  git submodule update --init --recursive\n"
        "à la racine du dépôt avant de builder.")
endif()

# --- Options de build de llama.cpp / ggml -----------------------------------
# On garde le build de llama.cpp au strict minimum : uniquement la lib, sans
# les exemples/tests/serveur en C++ qui ne servent à rien dans l'app mobile
# et alourdissent inutilement le temps de compilation.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

if(OPENCOMPANION_ENABLE_VULKAN)
    set(GGML_VULKAN ON CACHE BOOL "" FORCE)
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${LLAMA_DIR} llama_cpp_build)

if(OPENCOMPANION_ENABLE_VULKAN AND TARGET ggml-vulkan)
    # find_package(Vulkan) (appelé dans llama.cpp/ggml/src/ggml-vulkan/CMakeLists.txt)
    # détecte correctement Vulkan_INCLUDE_DIR (passé depuis app/build.gradle.kts, voir
    # -DVulkan_INCLUDE_DIR) : la variable est bien renseignée et Vulkan_FOUND=TRUE. Mais
    # lors de la cross-compilation vers Android, ce répertoire ne se retrouve jamais comme
    # flag -I effectif au moment de compiler ggml-vulkan.cpp (qui inclut directement
    # <vulkan/vulkan.hpp>) : la cible échoue avec "vulkan/vulkan.hpp file not found".
    #
    # Root cause identifiée en inspectant CMakeCache.txt, le fichier build.ninja généré ET
    # la sortie `clang++ -E -v` du compilateur NDK : la liste de recherche *implicite* du
    # compilateur pour ce triplet cible se termine par ".../sysroot/usr/include" (dernier
    # composant : "usr/include"). CMake calcule CMAKE_CXX_IMPLICIT_INCLUDE_DIRECTORIES pour
    # ce compilateur et l'utilise pour filtrer, comme redondants, les répertoires ajoutés
    # via target_include_directories() — et il le fait avec une comparaison qui traite
    # notre /usr/include (chemin HÔTE) comme un doublon du usr/include du SYSROOT cible,
    # alors que ce sont deux répertoires différents sur le disque (confirmé : aucun n'est
    # un lien symbolique vers l'autre). Résultat : target_include_directories(ggml-vulkan
    # PRIVATE /usr/include) modifie bien la propriété INCLUDE_DIRECTORIES de la cible
    # (vérifié via get_target_property), mais CMake supprime silencieusement ce répertoire
    # de la ligne de commande -I générée dans build.ninja.
    #
    # Contournement, étape 1 : passer le répertoire comme flag de compilation BRUT
    # (target_compile_options avec -isystem) plutôt que comme propriété d'includes — les
    # flags de compilation ne passent pas par cette déduplication et se retrouvent tels
    # quels sur la ligne de commande.
    #
    # Contournement, étape 2 : sur Linux, "$ENV{VULKAN_SDK}/include" est absent et le seul
    # répertoire trouvé est /usr/include lui-même (racine des en-têtes système Debian/Ubuntu,
    # pas un répertoire dédié à Vulkan) : y ajouter -isystem expose TOUS les en-têtes glibc de
    # la machine hôte au compilateur cross (NDK/bionic), ce qui casse la compilation
    # différemment ("bits/wordsize.h file not found", des en-têtes glibc multiarch
    # introuvables hors de leur propre arborescence). Les en-têtes Vulkan-Hpp
    # (vulkan.hpp, vulkan_*.hpp) sont eux-mêmes autonomes (aucune dépendance glibc, seulement
    # la STL C++ déjà fournie par le NDK) : on copie donc uniquement le sous-répertoire
    # vulkan/ dans un dossier isolé du build, pour n'exposer que ces en-têtes-là et non le
    # reste de /usr/include. Voir docs/VULKAN_NOTES.md pour le détail complet de cette
    # investigation.
    find_path(OPENCOMPANION_VULKAN_HPP_DIR
        NAMES vulkan/vulkan.hpp
        PATHS
            "$ENV{VULKAN_SDK}/include"
            /usr/include
        NO_CMAKE_FIND_ROOT_PATH
    )
    if(OPENCOMPANION_VULKAN_HPP_DIR)
        set(OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR "${CMAKE_BINARY_DIR}/opencompanion_vulkan_headers")
        file(COPY "${OPENCOMPANION_VULKAN_HPP_DIR}/vulkan" DESTINATION "${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")

        # ggml-vulkan.cpp inclut aussi <spirv/unified1/spirv.hpp> (paquet spirv-headers,
        # même problème : uniquement présent sous la racine hôte /usr/include). Même
        # traitement — copie isolée du seul sous-répertoire spirv/, self-contained lui
        # aussi (pas de dépendance glibc).
        find_path(OPENCOMPANION_SPIRV_HPP_DIR
            NAMES spirv/unified1/spirv.hpp
            PATHS
                "$ENV{VULKAN_SDK}/include"
                /usr/include
            NO_CMAKE_FIND_ROOT_PATH
        )
        if(OPENCOMPANION_SPIRV_HPP_DIR)
            file(COPY "${OPENCOMPANION_SPIRV_HPP_DIR}/spirv" DESTINATION "${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")
        else()
            message(WARNING
                "spirv/unified1/spirv.hpp introuvable : la compilation du backend Vulkan va "
                "probablement échouer. Installez le paquet spirv-headers (Debian/Ubuntu) ou "
                "le Vulkan SDK LunarG. Voir docs/VULKAN_NOTES.md.")
        endif()

        target_compile_options(ggml-vulkan PRIVATE "-isystem${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")
    else()
        message(WARNING
            "vulkan/vulkan.hpp introuvable (ni $ENV{VULKAN_SDK}/include ni /usr/include) : "
            "la compilation du backend Vulkan va probablement échouer. Installez le "
            "paquet fournissant les en-têtes C++ de Vulkan (ex. libvulkan-dev sur "
            "Debian/Ubuntu) ou le Vulkan SDK LunarG. Voir docs/VULKAN_NOTES.md.")
    endif()
endif()

# --- Pont JNI ----------------------------------------------------------------
add_library(opencompanion_bridge SHARED
    opencompanion_bridge.cpp
)

target_include_directories(opencompanion_bridge PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(opencompanion_bridge PRIVATE
    llama
    android
    log
)

target_compile_definitions(opencompanion_bridge PRIVATE
    $<$<BOOL:${OPENCOMPANION_ENABLE_VULKAN}>:OPENCOMPANION_HAS_VULKAN>
)
