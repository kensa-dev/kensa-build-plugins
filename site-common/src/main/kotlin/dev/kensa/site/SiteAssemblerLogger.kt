package dev.kensa.site

interface SiteAssemblerLogger {
    fun lifecycle(message: String)
    fun warn(message: String)
}
