import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-test-fixtures`
    `maven-publish`
    signing
}

dependencies {
    implementation(projects.common.utils)
    implementation(libs.androidpublisher)
    implementation(libs.client.api)
    implementation(libs.client.auth)
    implementation(libs.client.http)

    testImplementation(testLibs.junit)
    testImplementation(testLibs.junit.engine)
    testImplementation(testLibs.truth)
    testImplementation(testLibs.mockito)
}

// Mockito needs to be able to pass in null params
tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xno-call-assertions"
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("pluginMaven") {
        artifactId = "android-publisher"
        configurePom()
        signing.sign(this)
    }
}
