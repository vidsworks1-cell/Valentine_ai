import java.io.File

// Automatically generate .env file from environment variables if it doesn't exist
val envFile = File(rootDir, ".env")
if (!envFile.exists()) {
    val geminiKey = System.getenv("GEMINI_API_KEY") ?: ""
    if (geminiKey.isNotEmpty()) {
        envFile.writeText("GEMINI_API_KEY=$geminiKey\n")
    }
}

pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Lumina"

include(":app")
