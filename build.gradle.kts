plugins {
    id("org.gradle.java-library")
}

val blockProtVersion: String by project

allprojects {
    apply(plugin = "org.gradle.java-library")

    group = "de.sean.blockprot"
    version = blockProtVersion

    repositories {
        mavenLocal()
        mavenCentral()
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
}
