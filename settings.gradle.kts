/*
 * Copyright 2022-2024 Chartboost, Inc.
 * 
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

rootProject.name = "ChartboostAdapter"
include(":ChartboostAdapter")
include(":android-helium-sdk")
include(":Helium")
