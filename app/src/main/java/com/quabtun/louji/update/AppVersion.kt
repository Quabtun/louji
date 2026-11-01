package com.quabtun.louji.update

/**
 * A small semantic-ish version parser. It intentionally ignores pre-release suffixes because
 * release tags in this project are plain vX.Y.Z values.
 */
class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val left = parts.getOrElse(index) { 0 }
            val right = other.parts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    override fun equals(other: Any?): Boolean =
        other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        fun parse(raw: String): AppVersion? {
            val cleaned = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            if (cleaned.isBlank()) return null
            val parts = cleaned.split('.').map { it.toIntOrNull() ?: return null }
            if (parts.isEmpty() || parts.any { it < 0 }) return null
            return AppVersion(parts)
        }

        fun isNewer(candidate: String, current: String): Boolean {
            val left = parse(candidate) ?: return false
            val right = parse(current) ?: return false
            return left > right
        }
    }
}
