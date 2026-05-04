package dev.kensa.maven

import dev.kensa.site.SiteAssemblerLogger
import org.apache.maven.plugin.logging.Log

class MavenLogAdapter(private val log: Log) : SiteAssemblerLogger {
    override fun lifecycle(message: String) = log.info(message)
    override fun warn(message: String) = log.warn(message)
}
