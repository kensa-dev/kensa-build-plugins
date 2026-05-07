import org.gradle.api.JavaVersion.VERSION_17
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.jreleaser)
    `maven-publish`
    base
}

group = "dev.kensa"
version = project.properties["releaseVersion"]
    ?: "${file("snapshot-version.txt").readText().trim()}-SNAPSHOT"

subprojects {
    group = "dev.kensa"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()
    }

    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "stagingDeploy"
                    url = uri(rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
                }
            }
        }
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

jreleaser {
    gitRootSearch.set(true)
    deploy {
        maven {
            nexus2.create("snapshots") {
                active.set(Active.SNAPSHOT)
                snapshotUrl.set("https://central.sonatype.com/repository/maven-snapshots/")
                applyMavenCentralRules.set(true)
                snapshotSupported.set(true)
                sign.set(false)
                closeRepository.set(true)
                releaseRepository.set(true)
                stagingRepositories.add(layout.buildDirectory.dir("staging-deploy").get().asFile.absolutePath)
            }
        }
    }
}