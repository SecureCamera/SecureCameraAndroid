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
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		maven { url = uri("https://jitpack.io") }
		// TODO this is only needed for nav3 snapshots, can remove once they are stable
		//maven { url = uri("https://androidx.dev/snapshots/builds/13915848/artifacts/repository") }
	}
}

rootProject.name = "SecureCamera"
include(":app")
