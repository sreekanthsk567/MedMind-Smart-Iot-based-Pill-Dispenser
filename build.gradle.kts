// Top-level build.gradle.kts

buildscript {
    repositories {
        google() // Google's repository for Android dependencies
        mavenCentral() // Maven Central for other dependencies
    }
    dependencies {
        // Android Gradle plugin
        classpath("com.android.tools.build:gradle:8.0.0")
        // Kotlin plugin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    }
}

allprojects {
    repositories {
        google() // Google's repository for Android dependencies
        mavenCentral() // Maven Central for other dependencies
    }
}