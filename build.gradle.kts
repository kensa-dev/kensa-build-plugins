import org.gradle.api.JavaVersion.VERSION_17
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm) apply false
}

group = "dev.kensa"
version = project.properties["releaseVersion"] ?: "SNAPSHOT"

subprojects {
    group = "dev.kensa"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
    }

    var javaVersion = VERSION_17
    var kotlinJvmTarget = JVM_17

    apply(plugin = "org.jetbrains.kotlin.jvm")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<JavaPluginExtension> {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }
    }

    tasks {
        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(kotlinJvmTarget)
            }
        }

        register<Jar>("sourcesJar") {
            group = "build"
            archiveClassifier.set("sources")
            from(project.the<SourceSetContainer>()["main"].allSource)
            dependsOn(named("classes"))
        }

        withType<Jar> {
            archiveBaseName.set("${rootProject.name}-${project.name}")
        }
    }
}