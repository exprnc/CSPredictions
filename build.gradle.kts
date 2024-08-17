plugins {
    kotlin("jvm") version "2.0.0"
}

group = "com.exprnc.cspredictions"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.seleniumhq.selenium:selenium-java:4.23.0")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.52")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
    implementation("org.jsoup:jsoup:1.18.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

sourceSets["main"].java.srcDirs("src/main/kotlin")