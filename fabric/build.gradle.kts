import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("multiloader-loader")
    alias(libs.plugins.fabric.loom)
}

val modId = project.property("mod_id").toString()

val patchLuminGraphicsFabricJar by tasks.registering {
    group = "build"
    description = "Relaxes LuminGraphics-MC's exact loader metadata for compatible Fabric runtimes."
    dependsOn(tasks.named("processIncludeJars"))

    doLast {
        val includeDirectory = layout.buildDirectory.dir("processIncludeJars").get().asFile
        val luminJar = includeDirectory
            .listFiles()
            ?.single { it.name.startsWith("lumin-graphics-mc-fabric-") && it.extension == "jar" }
            ?: error("LuminGraphics-MC Fabric jar was not generated in $includeDirectory")
        val temporaryJar = luminJar.resolveSibling("${luminJar.name}.tmp")
        val loaderMinimum = project.property("fabric_loader_version").toString()

        ZipFile(luminJar).use { input ->
            ZipOutputStream(Files.newOutputStream(temporaryJar.toPath())).use { output ->
                input.entries().asSequence().forEach { entry ->
                    val replacement = if (entry.name == "fabric.mod.json") {
                        input.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                            .replace(Regex(""""fabricloader"\s*:\s*"=[^"]+"""")) {
                                "\"fabricloader\": \">=$loaderMinimum\""
                            }
                            .replace(Regex(""""fabric-api"\s*:\s*"=[^"]+"""")) {
                                "\"fabric-api\": \"*\""
                            }
                            .toByteArray(Charsets.UTF_8)
                    } else {
                        input.getInputStream(entry).use { it.readBytes() }
                    }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(replacement)
                    output.closeEntry()
                }
            }
        }
        Files.move(temporaryJar.toPath(), luminJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.lumin.graphics.mc.fabric.v2612) {
        isTransitive = false
    }
    include(libs.lumin.graphics.mc.fabric.v2612) {
        isTransitive = false
    }
    compileOnly(libs.lumin.graphics.mc.bridge.contract) {
        isTransitive = false
    }
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.luaj.jse)
    include(libs.luaj.jse)
    compileOnly(libs.sodium.fabric)
    compileOnly(libs.jsr305)
}

loom {
    val aw = project(":common").file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
    }
}

tasks.register("remapJar") {
    group = "build"
    description = "Builds the final Fabric archive; Mojang mappings require no separate remap pass."
    dependsOn(tasks.named("jar"))
}

tasks.named<Jar>("jar") {
    dependsOn(patchLuminGraphicsFabricJar)
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
            }
        }
    }
}

/*
tasks.register<Copy>("extractRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into("$projectDir/build/runtimeClasspath")
    doFirst {
        file("$projectDir/build/runtimeClasspath").mkdirs()
    }
}
*/
