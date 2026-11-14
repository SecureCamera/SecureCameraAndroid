// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
	dependencies {
		// AGP bundles an older KGP for built-in Kotlin; pin it to match the Kotlin compiler plugins.
		classpath(libs.kotlin.gradle.plugin)
	}
}

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.compose) apply false
}
