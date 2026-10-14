package com.hawdietpro.app.domain.util

import java.text.Normalizer

object TextNormalizer {
    private val nonAlnum = Regex("[^a-z0-9 ]")
    private val spaces = Regex("\\s+")
    private val diacritics = Regex("\\p{Mn}+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(diacritics, "")
            .replace(nonAlnum, " ")
            .replace(spaces, " ")
            .trim()
    }

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotBlank() }
}
