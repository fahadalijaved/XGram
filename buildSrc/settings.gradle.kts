@file:Suppress("UnstableApiUsage", "AvoidApplyPluginMethod")

apply(from = "${rootDir.parentFile}/properties.gradle.kts")

// XGram: Kotlin's own artifacts from Maven Central first; the plugin portal alone at times serves a broken POM of them
pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    if (extra["huawei"] == true) {
      maven(url = "https://developer.huawei.com/repo/")
    }
  }

	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}
