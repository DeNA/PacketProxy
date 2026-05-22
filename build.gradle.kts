import org.gradle.plugins.ide.eclipse.model.AbstractClasspathEntry
import org.gradle.plugins.ide.eclipse.model.AccessRule
import org.gradle.plugins.ide.eclipse.model.Classpath

plugins {
  eclipse
  id("com.diffplug.spotless") version "7.1.0"
}

repositories { mavenCentral() }

fun spotlessTarget(pattern: String): FileTree =
  fileTree(".") {
    include(pattern)
    exclude("bin/**", "build/**", ".gradle/**", "denaN/**", "denaL/**")
  }

spotless {
  java {
    target(spotlessTarget("**/*.java"))
    cleanthat()
    googleJavaFormat()
    eclipse().configFile("eclipse-format-settings.xml")
    formatAnnotations()
  }
  kotlin {
    target(spotlessTarget("**/*.kt"), spotlessTarget("**/*.gradle.kts"))
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }
  groovy {
    target(
      spotlessTarget("dena/**/*.gradle"),
      spotlessTarget("denaN/**/*.gradle"),
      spotlessTarget("denaL/**/*.gradle"),
    )
    greclipse()
    leadingTabsToSpaces(2)
    trimTrailingWhitespace()
    endWithNewline()
  }
  flexmark {
    target(spotlessTarget("**/*.md"))
    flexmark()
  }
}

eclipse {
  classpath {
    file {
      whenMerged {
        val classpath = this as Classpath
        val jre =
          classpath.entries.filterIsInstance<AbstractClasspathEntry>().find {
            it.path.contains("org.eclipse.jdt.launching.JRE_CONTAINER")
          }
        jre?.accessRules?.add(AccessRule("accessible", "com/**"))
        jre?.accessRules?.add(AccessRule("accessible", "sun/**"))
      }
    }
  }
}

tasks.register("test") { dependsOn(subprojects.map { it.tasks.named("test") }) }

tasks.register("run") { dependsOn(":app:run") }

listOf("release", "createMacRelease", "createWinRelease", "createLinuxRelease").forEach { taskName
  ->
  tasks.register(taskName) { dependsOn(":app:$taskName") }
}
