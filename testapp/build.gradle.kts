import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.github.triplet.gradle.androidpublisher.ResolutionStrategy
import com.github.triplet.gradle.common.utils.orNull
import com.github.triplet.gradle.common.utils.safeCreateNewFile
import com.github.triplet.gradle.play.PlayPublisherExtension
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.supercilex.gradle.versions.VersionOrchestratorExtension
import java.io.FileInputStream
import java.util.*

plugins {
    kotlin("android") version embeddedKotlinVersion
    id("com.android.application") version "8.2.0"
    id("com.supercilex.gradle.versions") version "0.10.0"
    id("com.github.triplet.play")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

android {
    namespace = "com.supercilex.test"
    compileSdk = 31

    defaultConfig {
        minSdk = 31
        targetSdk = 31
        versionCode = 1 // Updated on every build
        versionName = "1.0.0"
    }

    val releaseSigning by signingConfigs.creating {
        val keystorePropertiesFile = file("keystore.properties")
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))

        keyAlias = keystoreProperties["keyAlias"] as String
        keyPassword = keystoreProperties["keyPassword"] as String
        storeFile = file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["storePassword"] as String
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }

        register("debugPlay") {
            isDebuggable = true
            versionNameSuffix = "-DEBUG"
        }

        release {
            signingConfig = releaseSigning
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    (this as ExtensionAware).extensions.configure<NamedDomainObjectContainer<PlayPublisherExtension>>("playConfigs") {
        register("debug") {
            enabled = false
        }
    }

    lint {
        abortOnError = false
    }
}

play {
    serviceAccountCredentials = file("google-play-auto-publisher.json")
    defaultToAppBundles = true

    promoteTrack = "alpha"
    resolutionStrategy = ResolutionStrategy.AUTO
}

versionOrchestrator {
    configureVersionCode = false
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

abstract class BuildReadinessValidator : DefaultTask() {
    @TaskAction
    fun validate() {
        if (project.hasProperty("skipValidation")) return

        val playChecksumFile = project.layout.buildDirectory
                .file("build-validator/play").get().asFile
        val playPlugin = File(project.rootDir.parentFile, "play/plugin/build/libs")

        val oldHashes = playChecksumFile.orNull()?.readLines().orEmpty().toSet()
        val newHashes = playPlugin.listFiles().orEmpty().map {
            Files.asByteSource(it).hash(Hashing.sha256()).toString()
        }.toSet()

        check(oldHashes == newHashes) {
            playChecksumFile.safeCreateNewFile().writeText(newHashes.joinToString("\n"))

            "Plugin updated. Rerun command to finish build."
        }
    }
}

val validateBuildReadiness by tasks.registering(BuildReadinessValidator::class) {
    dependsOn(gradle.includedBuild("gradle-play-publisher")
                      .task(":play:play-publisher:publishToMavenLocal"))
}
tasks.matching { it.name != "validateBuildReadiness" }.configureEach {
    dependsOn(validateBuildReadiness)
}
