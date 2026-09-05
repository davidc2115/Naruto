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

    find_path(OPENCOMPANION_VULKAN_HPP_DIR
        NAMES vulkan/vulkan.hpp
        PATHS
            "$ENV{VULKAN_SDK}/include"
            /usr/include
            /usr/local/include
        NO_CMAKE_FIND_ROOT_PATH
    )
    if(OPENCOMPANION_VULKAN_HPP_DIR)
        set(OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR "${CMAKE_BINARY_DIR}/opencompanion_vulkan_headers")
        file(COPY "${OPENCOMPANION_VULKAN_HPP_DIR}/vulkan" DESTINATION "${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")

        # vulkan_core.h inclut aussi <vk_video/vulkan_video_codec_*.h> (extensions vidéo, un
        # sous-répertoire séparé du même paquet Vulkan-Headers que vulkan/) — sans lui, la
        # compilation échoue avec "vk_video/vulkan_video_codec_av1std.h file not found" dès que
        # vulkan_core.h est inclus indirectement (ce qui arrive toujours via vulkan.hpp). Même
        # traitement que vulkan/ et spirv/ ci-dessous : copie isolée, pas d'exposition du reste
        # de /usr/include.
        if(EXISTS "${OPENCOMPANION_VULKAN_HPP_DIR}/vk_video")
            file(COPY "${OPENCOMPANION_VULKAN_HPP_DIR}/vk_video" DESTINATION "${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")
        else()
            message(WARNING
                "vk_video/ introuvable à côté de vulkan/ (${OPENCOMPANION_VULKAN_HPP_DIR}) : la "
                "compilation du backend Vulkan va probablement échouer sur "
                "'vk_video/vulkan_video_codec_av1std.h file not found'. Voir docs/VULKAN_NOTES.md.")
        endif()

        find_path(OPENCOMPANION_SPIRV_HPP_DIR
            NAMES spirv/unified1/spirv.hpp
            PATHS
                "$ENV{VULKAN_SDK}/include"
                /usr/include
                /usr/local/include
                /usr/include/spirv-headers
            NO_CMAKE_FIND_ROOT_PATH
        )
        if(OPENCOMPANION_SPIRV_HPP_DIR)
            file(COPY "${OPENCOMPANION_SPIRV_HPP_DIR}/spirv" DESTINATION "${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")
        endif()

        add_compile_options("-isystem${OPENCOMPANION_VULKAN_HPP_ISOLATED_DIR}")
    endif()
else()
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${LLAMA_DIR} llama_cpp_build)

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
