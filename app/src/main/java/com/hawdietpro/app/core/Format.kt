package com.hawdietpro.app.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Format angka & tanggal gaya Indonesia. */
object Format {
    val locale: Locale = Locale.forLanguageTag("id-ID")

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val fullDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("HH.mm", locale)

    fun kcal(value: Double): String = "${grouped(value.roundToInt())} kkal"
    fun kcalNumber(value: Double): String = grouped(value.roundToInt())
    fun grams(value: Double): String = "${oneDecimal(value)} g"
    fun gramsInt(value: Double): String = "${value.roundToInt()} g"
    fun kg(value: Double): String = "${oneDecimal(value)} kg"

    fun grouped(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun oneDecimal(value: Double): String {
        val r = (value * 10).roundToInt() / 10.0
        return if (r == r.toInt().toDouble()) r.toInt().toString() else String.format(locale, "%.1f", r)
    }

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun date(date: LocalDate): String = date.format(fullDate).replaceFirstChar { it.uppercase() }
    fun shortDate(date: LocalDate): String = date.format(shortDate)
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)
    fun monthYear(date: LocalDate): String = date.format(monthYear).replaceFirstChar { it.uppercase() }
    fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    fun time(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(time)

    fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Hari ini"
        today.minusDays(1) -> "Kemarin"
        else -> date(date)
    }
}
