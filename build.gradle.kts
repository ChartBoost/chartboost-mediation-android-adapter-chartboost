// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath("org.jfrog.buildinfo:build-info-extractor-gradle:4.28.1")
    }
}

plugins {
    id("com.android.application") version "7.3.0" apply false
    id("com.android.library") version "7.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.7.20" apply false
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

task("ci") {
    dependsOn("clean")
    dependsOn(":ChartboostAdapter:assembleRemote")
}
