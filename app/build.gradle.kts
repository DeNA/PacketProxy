import com.bmuschko.gradle.izpack.CreateInstallerTask
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
  java
  application
  id("com.bmuschko.izpack") version "3.2"
  id("com.github.jk1.dependency-license-report") version "2.5"
  id("de.undercouch.download") version "5.6.0"
}

apply(from = rootProject.file("gradle/module-common.gradle.kts"))

dependencies {
  implementation(project(":core"))
  implementation(project(":ui"))
  implementation(project(":gulp"))

  izpack("org.codehaus.izpack:izpack-dist:5.1.3")
}

application { mainClass.set("packetproxy.PacketProxy") }

sourceSets { main { java.setSrcDirs(listOf("src/main/java/core")) } }

licenseReport {
  outputDir = layout.buildDirectory.dir("reports/licenses").get().asFile.path
  configurations = arrayOf("runtimeClasspath")
}

tasks.named<Jar>("jar") {
  dependsOn(":core:jar", ":ui:jar", ":gulp:jar")
  archiveBaseName.set("PacketProxy")
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
  doFirst {
    val serviceDir = layout.buildDirectory.dir("META-INF/services").get().asFile
    serviceDir.deleteRecursively()
    serviceDir.mkdirs()
    configurations.runtimeClasspath.get().forEach { artifact ->
      zipTree(artifact)
        .matching { include("META-INF/services/*") }
        .forEach { file ->
          File(serviceDir, file.name).appendText(file.readText(Charsets.UTF_8) + "\n")
        }
    }
  }
  manifest {
    attributes(
      "Main-Class" to "packetproxy.PacketProxy",
      "Class-Path" to configurations.runtimeClasspath.get().joinToString(" ") { it.name },
    )
  }
  from({
    configurations.runtimeClasspath.get().map { dependency ->
      if (dependency.isDirectory) {
        dependency
      } else {
        zipTree(dependency).matching { exclude("META-INF/**") }
      }
    }
  })
  from({ fileTree(layout.buildDirectory).matching { include("META-INF/services/*") } })
}

fun runShell(command: String) {
  ProcessBuilder("sh", "-c", command).inheritIO().start().waitFor()
}

fun runCommand(command: String): String =
  ProcessBuilder("sh", "-c", command).start().inputStream.bufferedReader().readText().trim()

val guiResourcesDir = rootProject.file("ui/src/main/resources/gui")

val gitVersion = runCommand("git describe --tags --abbrev=0")

File("${rootProject.projectDir}/core/src/main/resources/version").writeText(gitVersion)

val nameInstaller = "PacketProxy-${gitVersion}-Installer.jar"
val nameMacInstallApp = "PacketProxy-${gitVersion}-Installer-Mac.app"
val nameMacRelease = "PacketProxy-${gitVersion}-Installer-Mac.zip"
val nameMacSignedReleaseJPackage = "PacketProxy-${gitVersion}.dmg"
val nameMacSignedRelease = "PacketProxy-${gitVersion}-Installer-Mac-Signed.dmg"
val nameWin32Release = "PacketProxy-${gitVersion}-Installer-Win32.jar"
val nameWin64Release = "PacketProxy-${gitVersion}-Installer-Win64.jar"
val nameLinuxReleaseDir = "PacketProxy-${gitVersion}-Linux"
val nameLinuxRelease = "PacketProxy-${gitVersion}-Installer-Linux.jar"

val pathWorkDir = "${layout.buildDirectory.get()}/distributions"
val pathMacWorkDir = "${layout.buildDirectory.get()}/distributions/mac"
val pathMacTarget = "${pathMacWorkDir}/target"
val pathMacSignedReleaseJPackagePath = "${pathMacWorkDir}/${nameMacSignedReleaseJPackage}"
val pathMacSignedReleasePath = "${pathMacWorkDir}/${nameMacSignedRelease}"
val pathWin64WorkDir = "${layout.buildDirectory.get()}/distributions/win64"
val pathWin64Target = "${pathWin64WorkDir}/target"
val pathWin64Installer = "${pathWin64WorkDir}/${nameInstaller}"
val pathWin64Release = "${pathWin64WorkDir}/${nameWin64Release}"
val pathLinuxWorkDir = "${layout.buildDirectory.get()}/distributions/Linux"
val pathLinuxTarget = "${pathLinuxWorkDir}/target"
val pathLinuxInstaller = "${pathLinuxWorkDir}/${nameInstaller}"
val pathLinuxReleaseDir = "${pathLinuxWorkDir}/${nameLinuxReleaseDir}"
val pathLinuxRelease = "${pathLinuxWorkDir}/${nameLinuxRelease}"

extra["appleId"] = "test@example.com"

extra["applePasswd"] = "xxxx-xxxx-xxxx-xxxx"

extra["signedKey"] = "Developer ID Application: YYYYYYYY (ZZZZZZZZ)"

extra["teamId"] = "ZZZZZZZZ"

tasks.register("prepareJPackage") {
  dependsOn("jar")
  onlyIf { Os.isFamily(Os.FAMILY_MAC) }
  doLast {
    val workDir = pathMacWorkDir
    delete(workDir)
    File(workDir).mkdirs()
    val jdkUrl =
      "https://corretto.aws/downloads/resources/17.0.15.6.1/amazon-corretto-17.0.15.6.1-macosx-x64.tar.gz"
    val destFile = file("${workDir}/OpenJDK17.tar.gz")
    if (!destFile.exists()) {
      ant.withGroovyBuilder { "get"("src" to jdkUrl, "dest" to destFile, "verbose" to true) }
    }
    copy {
      from(tarTree(resources.gzip("${workDir}/OpenJDK17.tar.gz")))
      into(file("${workDir}/OpenJDK17"))
    }
    copy {
      from("${guiResourcesDir}/icon.png")
      into("${workDir}/icon.iconset/")
    }
    runShell("mv ${workDir}/icon.iconset/icon.png ${workDir}/icon.iconset/icon_256x256.png")
    runShell("iconutil -c icns --output ${workDir}/icon.icns ${workDir}/icon.iconset/")
  }
}

abstract class CreateWinTarget : DefaultTask() {
  @get:Input var workDir: String = ""

  @get:Input var targetDir: String = ""

  @get:Input var jdkUrl: String = ""

  @TaskAction
  fun start() {
    val guiDir = project.rootProject.file("ui/src/main/resources/gui")
    project.delete { delete(workDir) }
    File(workDir).mkdirs()
    File(targetDir).mkdirs()
    project.copy {
      from("${project.layout.buildDirectory.get()}/reports/licenses/index.html")
      into("${targetDir}/licenses")
    }
    project.copy {
      from("${guiDir}/icon.ico")
      into(targetDir)
      rename("icon.ico", "PacketProxy.ico")
    }
    project.copy {
      from("${project.layout.buildDirectory.get()}/libs/PacketProxy.jar")
      into(targetDir)
    }
    val destFile = project.file("${workDir}/OpenJDK17.zip")
    if (!destFile.exists()) {
      project.ant.withGroovyBuilder {
        "get"("src" to jdkUrl, "dest" to destFile, "verbose" to true)
      }
    }
    project.copy {
      from(project.zipTree("${workDir}/OpenJDK17.zip"))
      into(project.file("${workDir}/OpenJDK17"))
    }
    project.copy {
      from(project.file("${workDir}/OpenJDK17/jdk17.0.15_6"))
      into(project.file("${targetDir}/OpenJDK17"))
    }
  }
}

tasks.register<CreateWinTarget>("createWin64Target") {
  dependsOn("jar", "generateLicenseReport")
  workDir = pathWin64WorkDir
  targetDir = pathWin64Target
  jdkUrl =
    "https://corretto.aws/downloads/resources/17.0.15.6.1/amazon-corretto-17.0.15.6.1-windows-x64-jdk.zip"
}

tasks.register("createLinuxTarget") {
  dependsOn("jar", "generateLicenseReport")
  doLast {
    delete(pathLinuxWorkDir)
    File(pathLinuxWorkDir).mkdirs()
    File(pathLinuxTarget).mkdirs()
    copy {
      from("${layout.buildDirectory.get()}/reports/licenses/index.html")
      from("${rootProject.projectDir}/LICENSES")
      into("${pathLinuxTarget}/share/packetproxy/licenses")
    }
    copy {
      from("${guiResourcesDir}/icon.ico")
      into("${pathLinuxTarget}/share/packetproxy")
      rename("icon.ico", "PacketProxy.ico")
    }
    copy {
      from("${layout.buildDirectory.get()}/libs/PacketProxy.jar")
      into("${pathLinuxTarget}/share/packetproxy")
    }
    val destFile = file("${pathLinuxWorkDir}/OpenJDK17.tar.gz")
    val jdkUrl =
      "https://corretto.aws/downloads/resources/17.0.15.6.1/amazon-corretto-17.0.15.6.1-linux-x64.tar.gz"
    if (!destFile.exists()) {
      ant.withGroovyBuilder { "get"("src" to jdkUrl, "dest" to destFile, "verbose" to true) }
    }
    copy {
      from(tarTree(resources.gzip("${pathLinuxWorkDir}/OpenJDK17.tar.gz")))
      into(file("${pathLinuxWorkDir}/OpenJDK17"))
    }
    copy {
      from(file("${pathLinuxWorkDir}/OpenJDK17/amazon-corretto-17.0.15.6.1-linux-x64"))
      into(file("${pathLinuxTarget}/share/packetproxy/OpenJDK17"))
    }
    copy {
      from("${rootProject.projectDir}/installer/izpack/packetproxy")
      into("${pathLinuxTarget}/bin/")
    }
  }
}

tasks.register("preIzpack") {
  doLast {
    copy {
      from("${rootProject.projectDir}/installer/izpack/shortcutSpec.xml")
      into(pathWorkDir)
    }
    copy {
      from("${guiResourcesDir}/installer_leftside_image.png")
      into(pathWorkDir)
    }
    copy {
      from("${guiResourcesDir}/installer_icon.png")
      into(pathWorkDir)
    }
  }
}

tasks.register<CreateInstallerTask>("izpackWin64") {
  dependsOn("createWin64Target", "preIzpack")
  baseDir = file(pathWin64Target)
  installFile = file("${rootProject.projectDir}/installer/izpack/installer.xml")
  outputFile = file(pathWin64Installer)
  compression = "deflate"
  compressionLevel = 9
  appProperties =
    mapOf(
      "app.group" to "PacketProxy",
      "app.name" to "PacketProxy",
      "app.title" to "PacketProxy",
      "app.version" to gitVersion,
      "app.subpath" to "PacketProxy-${gitVersion}",
    )
}

tasks.register<CreateInstallerTask>("izpackLinux") {
  dependsOn("createLinuxTarget", "preIzpack")
  baseDir = file(pathLinuxTarget)
  installFile = file("${rootProject.projectDir}/installer/izpack/installer.xml")
  outputFile = file(pathLinuxInstaller)
  compression = "deflate"
  compressionLevel = 9
  appProperties =
    mapOf(
      "app.group" to "PacketProxy",
      "app.name" to "PacketProxy",
      "app.title" to "PacketProxy",
      "app.version" to gitVersion,
      "app.subpath" to "PacketProxy-${gitVersion}",
    )
}

tasks.register<Exec>("createMacJPackage") {
  dependsOn("prepareJPackage")
  onlyIf { Os.isFamily(Os.FAMILY_MAC) }
  workingDir = rootProject.projectDir
  commandLine(
    "/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home/bin/jpackage",
    "--verbose",
    "-d",
    pathMacWorkDir,
    "-n",
    "PacketProxy",
    "-i",
    "${layout.buildDirectory.get()}/libs/",
    "--app-version",
    gitVersion,
    "--icon",
    "${pathMacWorkDir}/icon.icns",
    "--main-jar",
    "PacketProxy.jar",
    "--mac-sign",
    "--mac-signing-key-user-name",
    extra["signedKey"] as String,
  )
}

tasks.register("resignMacJPackage") {
  dependsOn("createMacJPackage")
  onlyIf { Os.isFamily(Os.FAMILY_MAC) }
  doLast {
    val signedKey = extra["signedKey"] as String
    runShell(
      "hdiutil convert ${pathMacSignedReleaseJPackagePath} -format UDRW -o ${pathMacWorkDir}/tmp.dmg"
    )
    runShell("hdiutil mount ${pathMacWorkDir}/tmp.dmg")
    runShell(
      "rm -rf /tmp/packetproxy && mkdir /tmp/packetproxy && cd /tmp/packetproxy && jar xvf /Volumes/PacketProxy/PacketProxy.app/Contents/app/PacketProxy.jar"
    )
    runShell(
      "cd /tmp/packetproxy; for i in \$(find . -type f); do if file \$i | grep Mach-O > /dev/null; then codesign -fv --timestamp --options=runtime --deep -s \"${signedKey}\" \$i; fi; done"
    )
    runShell(
      "cd /tmp/packetproxy; jar cvfm /Volumes/PacketProxy/PacketProxy.app/Contents/app/PacketProxy.jar META-INF/MANIFEST.MF *"
    )
    runShell(
      "cd /Volumes/PacketProxy/PacketProxy.app/Contents/; for i in \$(find . -type f); do if file \$i | grep Mach-O > /dev/null; then codesign -fv --timestamp --options=runtime --deep -s \"${signedKey}\" \$i; fi; done"
    )
    runShell(
      "cd /Volumes/PacketProxy; codesign -fv --timestamp --options=runtime --deep -s \"${signedKey}\" --entitlements ${rootProject.projectDir}/assets/resign/entitlements.xml PacketProxy.app"
    )
    runShell("hdiutil detach /Volumes/PacketProxy")
    runShell(
      "rm ${pathMacSignedReleaseJPackagePath}; hdiutil convert ${pathMacWorkDir}/tmp.dmg -format UDZO -o ${pathMacSignedReleaseJPackagePath}"
    )
  }
}

tasks.register<Exec>("createMacInstaller") {
  dependsOn("resignMacJPackage")
  onlyIf { Os.isFamily(Os.FAMILY_MAC) }
  workingDir = rootProject.projectDir
  commandLine("mv", pathMacSignedReleaseJPackagePath, pathMacSignedReleasePath)
}

tasks.register<Exec>("notaryMacInstaller") {
  onlyIf { Os.isFamily(Os.FAMILY_MAC) }
  workingDir = rootProject.projectDir
  commandLine(
    "xcrun",
    "notarytool",
    "submit",
    pathMacSignedReleasePath,
    "--apple-id",
    extra["appleId"] as String,
    "--password",
    extra["applePasswd"] as String,
    "--team-id",
    extra["teamId"] as String,
    "--wait",
  )
}

tasks.register<Copy>("createWin64Release") {
  dependsOn("izpackWin64")
  from(pathWin64Installer)
  into(pathWin64WorkDir)
  rename(nameInstaller, nameWin64Release)
}

tasks.register<Copy>("createLinuxInstaller") {
  dependsOn("izpackLinux")
  from(pathLinuxInstaller)
  into(pathLinuxWorkDir)
  rename(nameInstaller, nameLinuxRelease)
}

tasks.register("createMacRelease") { dependsOn("createMacInstaller") }

tasks.register("createWinRelease") { dependsOn("createWin64Release") }

tasks.register("createLinuxRelease") {
  dependsOn("createLinuxInstaller")
  doLast {
    file(pathLinuxReleaseDir).mkdirs()
    copy {
      from("${rootProject.projectDir}/installer/izpack/installer.sh")
      from(pathLinuxInstaller)
      into(pathLinuxReleaseDir)
    }
    runShell("cd ${pathLinuxWorkDir}; tar czvf ${nameLinuxRelease} ${nameLinuxReleaseDir}")
  }
}

tasks.register("release") {
  dependsOn("createMacRelease", "createWinRelease", "createLinuxRelease")
}
