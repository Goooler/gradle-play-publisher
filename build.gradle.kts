import io.github.gradlenexus.publishplugin.CloseNexusStagingRepository
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import java.time.Duration

plugins {
    kotlin("jvm") version embeddedKotlinVersion apply false
    `lifecycle-base`
    alias(libs.plugins.depUpdates)

    // Needed to support publishing all modules atomically
    alias(libs.plugins.gradlePublish) apply false
    alias(libs.plugins.nexusPublish)
}

tasks.register("configureGithubActions") {
    doLast {
        logger.info("::set-output name=is_snapshot::$isSnapshotBuild")
    }
}

nexusPublishing {
    repositories {
        sonatype {
            username = System.getenv("SONATYPE_NEXUS_USERNAME")
            password = System.getenv("SONATYPE_NEXUS_PASSWORD")
        }
    }

    transitionCheckOptions {
        // 15 minutes
        delayBetween = Duration.ofSeconds(5)
        maxRetries = 180
    }
}

tasks.withType<CloseNexusStagingRepository>().configureEach {
    mustRunAfter(allprojects.map {
        it.tasks.matching { task ->
            task.name.contains("publishToSonatype")
        }
    })
}

val versionName = rootProject.file("version.txt").readText().trim()
allprojects {
    version = versionName
    group = "com.github.triplet.gradle"

    plugins.withType<JavaBasePlugin> {
        extensions.configure<JavaPluginExtension>{
            toolchain.languageVersion.convention(JavaLanguageVersion.of(11))
            withJavadocJar()
            withSourcesJar()
        }
    }

    plugins.withType<KotlinBasePlugin> {
        extensions.configure<KotlinProjectExtension> {
            sourceSets.configureEach {
                languageSettings.progressiveMode = true
                languageSettings.enableLanguageFeature("NewInference")
            }
        }
    }

    plugins.withType<PublishingPlugin> {
        extensions.configure<PublishingExtension> {
            configureMaven(repositories)
        }
    }

    plugins.withType<SigningPlugin> {
        extensions.configure<SigningExtension> {
            isRequired = false

            useInMemoryPgpKeys(System.getenv("SIGNING_KEY"), System.getenv("SIGNING_PASSWORD"))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        maxHeapSize = "4g"
        systemProperty("junit.jupiter.execution.parallel.enabled", true)
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")

        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
            setExceptionFormat("full")
        }
    }

    tasks.withType<ValidatePlugins>().configureEach {
        enableStricterValidation = true
    }
}
