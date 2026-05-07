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
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("${rootProject.name}-${project.name}")
                    description.set(provider { project.description ?: "Kensa build plugins module: ${project.name}" })
                    url.set("https://kensa.dev")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.html")
                        }
                    }
                    developers {
                        developer {
                            name.set("Paul Brooks")
                            email.set("paul@kensa.dev")
                        }
                    }
                    scm {
                        connection.set("scm:git:git@github.com:kensa-dev/build-plugins.git")
                        developerConnection.set("scm:git:git@github.com:kensa-dev/build-plugins.git")
                        url.set("https://github.com/kensa-dev/build-plugins")
                    }
                }
            }
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
            withSourcesJar()
            withJavadocJar()
        }
    }

    tasks {
        withType<KotlinCompile> {
            compilerOptions {
                jvmTarget.set(kotlinJvmTarget)
            }
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