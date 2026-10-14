package com.hawdietpro.app.domain

import com.hawdietpro.app.domain.config.FoodDbFile
import com.hawdietpro.app.domain.config.InsightRulesFile
import com.hawdietpro.app.domain.model.FoodReference
import com.hawdietpro.app.domain.model.Nutrients
import kotlinx.serialization.json.Json
import java.io.File

/** Memuat file /remote dari root repo agar test memakai data yang sama dengan aplikasi. */
object TestFixtures {
    val json = Json { ignoreUnknownKeys = true }

    fun remoteFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "remote/$name")
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("remote/$name tidak ditemukan")
    }

    val insightRules: InsightRulesFile by lazy {
        json.decodeFromString(InsightRulesFile.serializer(), remoteFile("insight_rules.json").readText())
    }

    val foodDb: FoodDbFile by lazy {
        json.decodeFromString(FoodDbFile.serializer(), remoteFile("food_db.json").readText())
    }

    val references: List<FoodReference> by lazy {
        foodDb.foods.map {
            FoodReference(
                id = it.id,
                name = it.name,
                aliases = it.aliases,
                category = it.category,
                per100g = Nutrients(it.kcal, it.protein, it.carbs, it.fat, it.fiber, it.sugar ?: 0.0, it.sodium ?: 0.0),
                defaultServingG = it.servingG,
                servingLabel = it.servingLabel,
                source = it.source,
                hasSugarSodium = it.sugar != null && it.sodium != null,
            )
        }
    }
}
