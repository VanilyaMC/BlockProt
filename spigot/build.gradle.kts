plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

val nbtApiVersion: String by project
val townyVersion: String by project

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://repo.codemc.org/repository/maven-public/") {
        name = "CodeMC"
        content {
            includeGroup("de.tr7zw")
        }
    }
    maven("https://repo.opencollab.dev/main/")
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.244-stable"

dependencies {
    implementation(project(":common"))

    compileOnly(paperApi)

    // Block data lives in the tile entity / chunk data through the NBT API.
    implementation("de.tr7zw:item-nbt-api:$nbtApiVersion")

    // Integrations (all optional at run time)
    compileOnly("com.palmergames.bukkit.towny:towny:$townyVersion") { isTransitive = false }
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT") { isTransitive = false }

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("BlockProt")
    relocate("de.tr7zw.changeme.nbtapi", "de.sean.blockprot.bukkit.shaded.nbtapi")
    dependencies {
        include(project(":common"))
        include(dependency("org.jetbrains:annotations"))
        include(dependency("de.tr7zw:item-nbt-api"))
    }
    exclude("META-INF/maven/**", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar { archiveClassifier.set("plain") }
tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.test {
    useJUnitPlatform()
}
