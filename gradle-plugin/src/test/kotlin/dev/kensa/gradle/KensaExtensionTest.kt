package dev.kensa.gradle

import io.kotest.matchers.shouldBe
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class KensaExtensionTest {

    @Test
    fun `site defaults to false`() {
        val project = ProjectBuilder.builder().build()
        val ext = project.extensions.create("kensa", KensaExtension::class.java)
        ext.site.get() shouldBe false
    }

    @Test
    fun `siteRoot defaults to layout buildDirectory dir kensa-site`() {
        val project = ProjectBuilder.builder().build()
        val ext = project.extensions.create("kensa", KensaExtension::class.java)
        ext.siteRoot.get().asFile.absolutePath shouldBe project.layout.buildDirectory.dir("kensa-site").get().asFile.absolutePath
    }

    @Test
    fun `enabled defaults to true and sourceSets defaults to test`() {
        val project = ProjectBuilder.builder().build()
        val ext = project.extensions.create("kensa", KensaExtension::class.java)
        ext.enabled.get() shouldBe true
        ext.sourceSets.get() shouldBe setOf("test")
    }
}
