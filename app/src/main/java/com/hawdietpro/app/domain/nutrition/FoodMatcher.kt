package com.hawdietpro.app.domain.nutrition

import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.util.TextNormalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Mencocokkan nama elemen dari AI ke database nutrisi lokal.
 * Urutan: cocok persis (nama/alias) → nama + metode masak → kemiripan token → toleransi salah ketik.
 */
class FoodMatcher(references: List<FoodReference>) {

    data class Match(val reference: FoodReference, val score: Double)

    private class Key(val text: String, val tokens: Set<String>, val ref: FoodReference)

    private val keys: List<Key>
    private val exact: Map<String, FoodReference>

    init {
        val list = ArrayList<Key>()
        val map = HashMap<String, FoodReference>()
        for (ref in references) {
            val names = (listOf(ref.name) + ref.aliases).map(TextNormalizer::normalize).filter { it.isNotBlank() }.distinct()
            for (n in names) {
                list += Key(n, meaningfulTokens(n), ref)
                map.putIfAbsent(n, ref)
            }
        }
        keys = list
        exact = map
    }

    fun match(name: String, cookingMethod: String? = null, minScore: Double = DEFAULT_MIN_SCORE): Match? {
        val normalized = TextNormalizer.normalize(name)
        if (normalized.isBlank()) return null

        exact[normalized]?.let { return Match(it, 1.0) }

        val method = cookingMethod?.let(TextNormalizer::normalize)?.takeIf { it.isNotBlank() }
        if (method != null && !normalized.contains(method)) {
            exact["$normalized $method"]?.let { return Match(it, 0.95) }
        }

        val queryTokens = meaningfulTokens(normalized) + (method?.let { meaningfulTokens(it) } ?: emptySet())
        if (queryTokens.isEmpty()) return null

        var best: Match? = null
        for (key in keys) {
            val score = similarity(normalized, queryTokens, key)
            if (score >= minScore && (best == null || score > best.score ||
                    (score == best.score && key.text.length < best.reference.name.length))
            ) {
                best = Match(key.ref, score)
            }
        }
        return best
    }

    /** Pencarian untuk input manual: hasil diurutkan dari paling relevan. */
    fun search(query: String, limit: Int = 30): List<FoodReference> {
        val q = TextNormalizer.normalize(query)
        if (q.isBlank()) return emptyList()
        val qTokens = meaningfulTokens(q)
        val scored = HashMap<String, Pair<FoodReference, Double>>()
        for (key in keys) {
            val s = when {
                key.text == q -> 1.0
                key.text.startsWith(q) -> 0.9
                key.text.contains(q) -> 0.8
                else -> similarity(q, qTokens, key) * 0.75
            }
            if (s >= 0.4) {
                val prev = scored[key.ref.id]
                if (prev == null || s > prev.second) scored[key.ref.id] = key.ref to s
            }
        }
        return scored.values.sortedWith(compareByDescending<Pair<FoodReference, Double>> { it.second }.thenBy { it.first.name })
            .take(limit)
            .map { it.first }
    }

    private fun similarity(normalized: String, queryTokens: Set<String>, key: Key): Double {
        if (key.tokens.isEmpty()) return 0.0
        val common = queryTokens.count { it in key.tokens }
        val dice = 2.0 * common / (queryTokens.size + key.tokens.size)
        val typo = if (min(normalized.length, key.text.length) >= 5) {
            val dist = levenshtein(normalized, key.text)
            1.0 - dist.toDouble() / max(normalized.length, key.text.length)
        } else 0.0
        return max(dice, if (typo >= 0.88) typo else 0.0)
    }

    companion object {
        const val DEFAULT_MIN_SCORE = 0.66

        private val STOPWORDS = setOf(
            "dan", "dengan", "pakai", "porsi", "potong", "sepotong", "buah", "biji", "irisan",
            "sedikit", "setengah", "satu", "dua", "isi", "ala", "khas", "yang", "berisi",
        )

        fun meaningfulTokens(text: String): Set<String> =
            TextNormalizer.tokens(text).filter { it !in STOPWORDS && it.length > 1 }.toSet()

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val t = prev; prev = curr; curr = t
            }
            return prev[b.length]
        }
    }
}
