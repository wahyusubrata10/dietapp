package com.hawdietpro.app.domain.config

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelChainTest {

    private val fallbacks = listOf("gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.0-flash")

    @Test
    fun `tanpa daftar model, pakai urutan config dan buang lite di mode teliti`() {
        assertEquals(
            listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash"),
            ModelChain.build(null, "gemini-flash-latest", fallbacks, null, allowLite = false),
        )
    }

    @Test
    fun `model yang sudah mati dibuang dan flash terbaru ditambahkan`() {
        val alive = setOf("gemini-3.5-flash", "gemini-3-flash", "gemini-3.1-flash-lite", "gemini-3.5-flash-image", "gemini-pro-latest")
        assertEquals(
            listOf("gemini-flash-latest", "gemini-3.5-flash", "gemini-3-flash"),
            ModelChain.build(null, "gemini-flash-latest", fallbacks, alive, allowLite = false),
        )
        assertEquals(
            listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-3.5-flash", "gemini-3-flash"),
            ModelChain.build(null, "gemini-flash-latest", fallbacks, alive, allowLite = true),
        )
    }

    @Test
    fun `override pengguna selalu dicoba pertama`() {
        val chain = ModelChain.build("gemini-custom", "gemini-flash-latest", fallbacks, setOf("gemini-3.5-flash"), allowLite = false)
        assertEquals("gemini-custom", chain.first())
    }

    @Test
    fun `tidak pernah kosong`() {
        assertEquals(listOf("gemini-x"), ModelChain.build(null, "gemini-x", emptyList(), emptySet(), allowLite = false))
    }
}
