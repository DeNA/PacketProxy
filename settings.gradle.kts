pluginManagement {
  plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("me.champeau.jmh") version "0.7.3"
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
}

rootProject.name = "PacketProxy"

include("core", "ui", "gulp", "app")
