plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "cn.cloudfl4re"
version = "1.2.0"

val supportedTargetPlatforms = listOf("26.2", "26.1.2", "1.21.11")
val buildOutputDirectory = providers.gradleProperty("buildOutputDir")
layout.buildDirectory.set(
    buildOutputDirectory.map { layout.projectDirectory.dir(it) }
        .orElse(layout.projectDirectory.dir("build"))
)

val targetPlatform = providers.gradleProperty("targetPlatform").orElse("26.2").get().trim()
val targetApiVersion = when (targetPlatform) {
    "1.21.11", "26.1.2", "26.2" -> targetPlatform
    else -> error("Unsupported targetPlatform: $targetPlatform")
}
val targetJavaVersion = if (targetPlatform == "1.21.11") 21 else 25
val lophineApiDependency = when (targetPlatform) {
    "26.1.2" -> "fun.bm.lophine:lophine-api:26.1.2.build.638-stable"
    "26.2" -> "fun.bm.lophine:lophine-api:26.2.build.651-stable"
    else -> null
}
val resourceTokens = mapOf(
    "version" to version.toString(),
    "targetApi" to targetApiVersion,
)

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.papermc.io/repository/maven-snapshots/")
    maven("https://repo.bacteriawa.com/repository/maven-public/")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    if (lophineApiDependency != null) {
        compileOnly(lophineApiDependency)
    } else {
        compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
    }
    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}

val integrationHarness by sourceSets.creating

configurations[integrationHarness.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations.testCompileOnly.get().extendsFrom(configurations.compileOnly.get())
configurations.testRuntimeOnly.get().extendsFrom(configurations.compileOnly.get())

val integrationHarnessJar by tasks.registering(Jar::class) {
    archiveBaseName.set("BoatRace-IntegrationHarness")
    archiveVersion.set(version.toString())
    from(integrationHarness.output)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.properties(resourceTokens)
    filesMatching("plugin.yml") {
        expand(resourceTokens)
    }
}

tasks.named<ProcessResources>("processIntegrationHarnessResources") {
    filteringCharset = "UTF-8"
    inputs.properties(resourceTokens)
    filesMatching("plugin.yml") {
        expand(resourceTokens)
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}

val buildAllTargets = tasks.register("buildAllTargets") {
    group = "build"
    description = "Builds the plugin for every supported target platform."
    doLast {
        val outputDirectory = layout.projectDirectory.dir("build/libs").asFile
        outputDirectory.mkdirs()
        outputDirectory.listFiles()
            ?.filter {
                it.isFile && it.extension == "jar" && (
                    it.name == "${project.name}-${project.version}.jar"
                        || it.name.startsWith("${project.name}-${project.version}-")
                )
            }
            ?.forEach { it.delete() }

        val windows = System.getProperty("os.name").lowercase().contains("win")
        val wrapperArguments = supportedTargetPlatforms.map { target ->
            listOf(
                "clean",
                "test",
                "shadowJar",
                "-PtargetPlatform=$target",
                "-PbuildOutputDir=build/targets/$target"
            )
        }
        supportedTargetPlatforms.zip(wrapperArguments).forEach { (target, arguments) ->
            val wrapper = project.layout.projectDirectory.file(if (windows) "gradlew.bat" else "gradlew").asFile
            val command = if (windows) {
                listOf("cmd", "/c", wrapper.absolutePath) + arguments
            } else {
                listOf(wrapper.absolutePath) + arguments
            }
            val process = ProcessBuilder(command)
                .directory(project.projectDir)
                .inheritIO()
                .start()
            check(process.waitFor() == 0) { "Build failed for target platform $target" }

            val artifact = layout.projectDirectory.dir("build/targets/$target/libs").file("${project.name}-${project.version}.jar").asFile
            check(artifact.isFile) { "Missing artifact for target platform $target: $artifact" }
            copy {
                from(artifact)
                into(outputDirectory)
                rename { "${project.name}-${project.version}-$target.jar" }
            }
        }
    }
}

tasks.build {
    dependsOn(buildAllTargets)
}
