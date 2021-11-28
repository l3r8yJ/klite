import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.0"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")
  testImplementation(kotlin("test"))
}

sourceSets {
  named("main") {
    java.srcDirs("src")
    resources.srcDirs("src").exclude("**/*.kt")
  }
  named("test") {
    java.srcDirs("test")
    resources.srcDirs("test").exclude("**/*.kt")
  }
}

tasks.test {
  useJUnit()
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "16"
    javaParameters = true
  }
}