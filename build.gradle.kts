plugins {
    java
    id("com.gradleup.shadow") version "9.3.1"
}

group = "cn.cloudfl4re"
version = "1.1.0"

val resourceTokens = mapOf("version" to version.toString())

repositories {
    mavenCentral()
    maven("https://repo.bacteriawa.com/repository/maven-public/")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    compileOnly("fun.bm.lophine:lophine-api:26.2.build.651-stable")
    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

val integrationHarness by sourceSets.creating

configurations[integrationHarness.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())

val integrationHarnessJar by tasks.registering(Jar::class) {
    archiveBaseName.set("BoatRace-IntegrationHarness")
    archiveVersion.set(version.toString())
    from(integrationHarness.output)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
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

tasks.build {
    dependsOn(tasks.shadowJar)
}
