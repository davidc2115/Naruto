pluginManagement {
    repositories {
        google()
        // Fallback mirror (si repo.maven.apache.org renvoie 403)
        maven(url = "https://repo1.maven.org/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Fallback mirror (si repo.maven.apache.org renvoie 403)
        maven(url = "https://repo1.maven.org/maven2")
        mavenCentral()
    }
}

rootProject.name = "Naruto AI Chat"
include(":app")
