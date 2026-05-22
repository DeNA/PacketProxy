plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

apply(from = rootProject.file("gradle/module-common.gradle.kts"))

dependencies {
  api(project(":core"))

  implementation("com.formdev:flatlaf:3.4.1")
  implementation("com.formdev:flatlaf-intellij-themes:3.4.1")
  implementation("org.jfree:jfreechart:1.5.3")
  implementation("org.ejml:ejml-all:0.41")

  testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.assertj:assertj-core:3.23.1")
  testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}

sourceSets {
  main {
    java.setSrcDirs(listOf("src/main/java/core"))
    resources.setSrcDirs(listOf("src/main/resources"))
  }
  test { kotlin.setSrcDirs(listOf("src/test/kotlin")) }
}
