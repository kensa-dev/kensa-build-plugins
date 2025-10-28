plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.buildConfig)
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
}

buildConfig {
    packageName("dev.kensa.gradle")
    useKotlinOutput { topLevelConstants = true }
    buildConfigField("KENSA_VERSION", provider { "${project.version}" })
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(gradleApi())
}

gradlePlugin {
    plugins {
        create("kensaPlugin") {
            id = "dev.kensa.gradle-plugin"
            implementationClass = "dev.kensa.gradle.KensaGradlePlugin"
            displayName = "Kensa Gradle Plugin"
            description = "Gradle plugin for Kensa compiler plugin integration"
            website.set("https://kensa.dev/build-plugins")
            vcsUrl.set("https://github.com/kensa-dev/kensa-build-plugins")
            tags.set(listOf("kotlin", "compiler-plugin", "testing", "kensa"))        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}