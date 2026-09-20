package com.robpelo.companion.update

internal object FirefoxVersion {
    fun updateAvailable(candidate: String, installed: String?): Boolean =
        installed == null || isNewer(candidate, installed)

    fun isNewer(candidate: String, installed: String): Boolean {
        val candidateParts = numericParts(candidate)
        val installedParts = numericParts(installed)
        val size = maxOf(candidateParts.size, installedParts.size)
        for (index in 0 until size) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val installedPart = installedParts.getOrElse(index) { 0 }
            if (candidatePart != installedPart) {
                return candidatePart > installedPart
            }
        }
        return false
    }

    private fun numericParts(version: String): List<Int> =
        version.substringBefore('-')
            .split('.')
            .map { part -> part.toIntOrNull() ?: 0 }
}
