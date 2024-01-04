plugins {
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish")
}

dependencies {
    implementation(projects.play.androidPublisher)
    implementation(projects.common.utils)
    implementation(projects.common.validation)

    compileOnly(libs.agp) // Compile only to not force a specific AGP version
    compileOnly(libs.agp.common)
    compileOnly(libs.agp.test)
    compileOnly(libs.agp.ddms)
    implementation(libs.guava)
    implementation(libs.client.gson)

    implementation(projects.common.utils)
    implementation(projects.common.validation)
    testImplementation(testFixtures(projects.play.androidPublisher))
    testImplementation(libs.agp)

    testImplementation(testLibs.junit)
    testImplementation(testLibs.junit.engine)
    testImplementation(testLibs.junit.params)
    testImplementation(testLibs.truth)
}

tasks.withType<PluginUnderTestMetadata>().configureEach {
    dependsOn(tasks.compileKotlin, tasks.compileTestKotlin, tasks.compileJava, tasks.compileTestJava)
    dependsOn(tasks.processResources, tasks.processTestResources)
    dependsOn(projects.play.androidPublisher.dependencyProject.tasks.named("testFixturesJar"))

    pluginClasspath.setFrom(/* reset */)

    pluginClasspath.from(configurations.compileClasspath)
    pluginClasspath.from(configurations.testCompileClasspath)
    pluginClasspath.from(configurations.runtimeClasspath)
    pluginClasspath.from(provider { sourceSets.test.get().runtimeClasspath.files })
}

afterEvaluate {
    tasks.withType<PublishToMavenRepository>().configureEach {
        isEnabled = isSnapshotBuild || publication.name == "pluginMaven"
    }
}

tasks.withType<Test>().configureEach {
    inputs.files(fileTree("src/test/fixtures"))

    // AGP 8 requires JDK 17 and we want to to be compatible with previous JDKs
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }

    // Our integration tests need a fully compiled jar
    dependsOn(tasks.assemble)

    // Those tests also need to know which version was built
    systemProperty("VERSION_NAME", version)
}

gradlePlugin {
    website = "https://github.com/Triple-T/gradle-play-publisher"
    vcsUrl = "https://github.com/Triple-T/gradle-play-publisher"

    plugins.create("play") {
        id = "com.github.triplet.play"
        displayName = "Gradle Play Publisher"
        description = "Gradle Play Publisher allows you to upload your App Bundle or APK " +
                "and other app details to the Google Play Store."
        implementationClass = "com.github.triplet.gradle.play.PlayPublisherPlugin"
        tags.addAll(listOf("android", "google-play", "publishing", "deployment", "apps", "mobile"))
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("pluginMaven") {
        artifactId = "play-publisher"
        configurePom()
        signing.sign(this)
    }
}
