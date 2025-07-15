/*
 * Copyright 2022-2025 Chartboost, Inc.
 * 
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath("org.jfrog.buildinfo:build-info-extractor-gradle:4.28.1")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false

    kotlin("plugin.serialization") version "2.1.0"
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

task("ci") {
    dependsOn("clean")
    dependsOn(":ChartboostAdapter:assembleRemote")
}
