package net.dacworld.android.holyplacesofthelord.util

import android.content.res.Resources
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.data.AchievementRepository
import net.dacworld.android.holyplacesofthelord.data.UserPreferencesManager
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.Visit
import java.util.Calendar
import java.util.Date

/**
 * Calculates achievement state from visit data.
 * Ports logic from iOS AppDelegate.getVisits() and updateAchievement().
 */
class AchievementCalculator(
    private val resources: Resources,
    private val visitDao: VisitDao,
    private val userPreferencesManager: UserPreferencesManager
) {
    /**
     * Compute achievements from visit data.
     * @param visits All visits, ordered by date DESC (newest first)
     * @param isOrdinanceWorker Whether to include Worker (W) achievements
     */
    fun calculate(visits: List<Visit>, isOrdinanceWorker: Boolean): List<Achievement> {
        val defs = AchievementRepository.buildFixedAchievementDefinitions(resources, isOrdinanceWorker)
        val currentYear = AchievementRepository.getCurrentYear()
        val templeConsistentDef = AchievementRepository.templeConsistentDefinition(resources, currentYear)
        val allDefs = defs + templeConsistentDef

        // Build mutable achievements from definitions
        val achievementMap = mutableMapOf<String, MutableAchievement>()
        allDefs.forEachIndexed { _, (iconName, name, details) ->
            achievementMap[iconName] = MutableAchievement(name, details, iconName)
        }

        // Accumulate totals
        var baptismsTotal = 0
        var initiatoriesTotal = 0
        var endowmentsTotal = 0
        var sealingsTotal = 0
        var shiftHoursTotal = 0.0
        val distinctTemplesVisited = mutableSetOf<String>()
        val distinctHistoricSitesVisited = mutableSetOf<String>()
        // Year -> set of months with ordinances
        val yearMonthsWithOrdinances = mutableMapOf<Int, MutableSet<Int>>()

        // Process visits (newest first) for Temple Consistent
        visits.forEach { visit ->
            val date = visit.dateVisited ?: return@forEach
            val cal = Calendar.getInstance().apply { time = date }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1 // 1-12

            val baptisms = (visit.baptisms ?: 0).toInt()
            val confirmations = (visit.confirmations ?: 0).toInt()
            val initiatories = (visit.initiatories ?: 0).toInt()
            val endowments = (visit.endowments ?: 0).toInt()
            val sealings = (visit.sealings ?: 0).toInt()
            val shiftHrs = visit.shiftHrs ?: 0.0
            val hasOrdinances = baptisms > 0 || confirmations > 0 || initiatories > 0 || endowments > 0 || sealings > 0

            val placeName = visit.holyPlaceName ?: ""

            // Running totals (for ordinance achievements)
            baptismsTotal += baptisms + confirmations
            initiatoriesTotal += initiatories
            endowmentsTotal += endowments
            sealingsTotal += sealings
            shiftHoursTotal += shiftHrs

            // Temple visits (type T)
            if (visit.type == "T" && placeName.isNotBlank()) {
                distinctTemplesVisited.add(placeName)
            }
            // Historic sites (type H)
            if (visit.type == "H" && placeName.isNotBlank()) {
                distinctHistoricSitesVisited.add(placeName)
            }

            // Temple Consistent: track months with ordinances per year
            if (hasOrdinances) {
                yearMonthsWithOrdinances.getOrPut(year) { mutableSetOf() }.add(month)
            }
        }

        // Update achievements based on thresholds
        updateOrdinanceAchievements(achievementMap, 'B', baptismsTotal, visits, AchievementRepository.B_THRESHOLDS) { b, c -> b + c }
        updateOrdinanceAchievements(achievementMap, 'I', initiatoriesTotal, visits, AchievementRepository.I_THRESHOLDS) { i, _ -> i }
        updateOrdinanceAchievements(achievementMap, 'E', endowmentsTotal, visits, AchievementRepository.E_THRESHOLDS) { e, _ -> e }
        updateOrdinanceAchievements(achievementMap, 'S', sealingsTotal, visits, AchievementRepository.S_THRESHOLDS) { s, _ -> s }
        if (isOrdinanceWorker) {
            updateWorkerAchievements(achievementMap, shiftHoursTotal, visits, AchievementRepository.W_THRESHOLDS)
        }
        updateSetAchievements(achievementMap, 'T', distinctTemplesVisited.size, visits, AchievementRepository.T_THRESHOLDS)
        updateSetAchievements(achievementMap, 'H', distinctHistoricSitesVisited.size, visits, AchievementRepository.H_THRESHOLDS)

        // Temple Consistent: award for current year if all 12 months have ordinances
        processTempleConsistent(
            achievementMap,
            yearMonthsWithOrdinances,
            visits,
            currentYear
        )

        // Build final list: completed first (by achieve date desc), then incomplete with progress
        val completed: List<Achievement> = achievementMap.values.filter { it.achieved != null }
            .sortedByDescending { it.achieved?.time ?: 0L }
            .map { it.toAchievement(null, null) }
        val incomplete = achievementMap.values.filter { it.achieved == null }
            .map { calcProgress(it, baptismsTotal, initiatoriesTotal, endowmentsTotal, sealingsTotal, shiftHoursTotal, distinctTemplesVisited.size, distinctHistoricSitesVisited.size, yearMonthsWithOrdinances, currentYear, isOrdinanceWorker) }
            .sortedByDescending { it.progress ?: 0f }

        return completed + incomplete
    }

    private fun updateOrdinanceAchievements(
        map: MutableMap<String, MutableAchievement>,
        type: Char,
        currentValue: Int,
        visits: List<Visit>,
        thresholds: List<Int>,
        valueExtractor: (Int, Int) -> Int
    ) {
        thresholds.forEach { threshold ->
            if (currentValue >= threshold) {
                val iconName = "ach${threshold}$type"
                val ach = map[iconName] ?: return@forEach
                if (ach.achieved == null) {
                    val (date, place) = findAchieveDateAndPlace(visits, type, threshold, valueExtractor)
                    ach.achieved = date
                    ach.placeAchieved = place
                }
            }
        }
    }

    private fun updateWorkerAchievements(
        map: MutableMap<String, MutableAchievement>,
        currentValue: Double,
        visits: List<Visit>,
        thresholds: List<Int>
    ) {
        thresholds.forEach { threshold ->
            if (currentValue >= threshold) {
                val iconName = "ach${threshold}W"
                val ach = map[iconName] ?: return@forEach
                if (ach.achieved == null) {
                    val (date, place) = findWorkerAchieveDateAndPlace(visits, threshold)
                    ach.achieved = date
                    ach.placeAchieved = place
                }
            }
        }
    }

    private fun updateSetAchievements(
        map: MutableMap<String, MutableAchievement>,
        type: Char,
        currentValue: Int,
        visits: List<Visit>,
        thresholds: List<Int>
    ) {
        thresholds.forEach { threshold ->
            if (currentValue >= threshold) {
                val iconName = "ach${threshold}$type"
                val ach = map[iconName] ?: return@forEach
                if (ach.achieved == null) {
                    val (date, place) = findSetAchieveDateAndPlace(visits, type, threshold)
                    ach.achieved = date
                    ach.placeAchieved = place
                }
            }
        }
    }

    private fun findAchieveDateAndPlace(
        visits: List<Visit>,
        type: Char,
        threshold: Int,
        valueExtractor: (Int, Int) -> Int
    ): Pair<Date?, String?> {
        var baptismsTotal = 0
        var confirmationsTotal = 0
        var initiatoriesTotal = 0
        var endowmentsTotal = 0
        var sealingsTotal = 0
        for (visit in visits.reversed()) {
            baptismsTotal += (visit.baptisms ?: 0).toInt()
            confirmationsTotal += (visit.confirmations ?: 0).toInt()
            initiatoriesTotal += (visit.initiatories ?: 0).toInt()
            endowmentsTotal += (visit.endowments ?: 0).toInt()
            sealingsTotal += (visit.sealings ?: 0).toInt()
            val value = when (type) {
                'B' -> valueExtractor(baptismsTotal, confirmationsTotal)
                'I' -> valueExtractor(initiatoriesTotal, 0)
                'E' -> valueExtractor(endowmentsTotal, 0)
                'S' -> valueExtractor(sealingsTotal, 0)
                else -> 0
            }
            if (value >= threshold) {
                return Pair(visit.dateVisited, visit.holyPlaceName?.takeIf { it.isNotBlank() })
            }
        }
        return Pair(null, null)
    }

    private fun findWorkerAchieveDateAndPlace(visits: List<Visit>, threshold: Int): Pair<Date?, String?> {
        var total = 0.0
        for (visit in visits.reversed()) {
            total += visit.shiftHrs ?: 0.0
            if (total >= threshold) {
                return Pair(visit.dateVisited, visit.holyPlaceName?.takeIf { it.isNotBlank() })
            }
        }
        return Pair(null, null)
    }

    private fun findSetAchieveDateAndPlace(visits: List<Visit>, type: Char, threshold: Int): Pair<Date?, String?> {
        val set = mutableSetOf<String>()
        for (visit in visits.reversed()) {
            val placeName = visit.holyPlaceName ?: ""
            if (placeName.isNotBlank() &&
                ((type == 'T' && visit.type == "T") || (type == 'H' && visit.type == "H"))
            ) {
                set.add(placeName)
                if (set.size >= threshold) {
                    return Pair(visit.dateVisited, placeName)
                }
            }
        }
        return Pair(null, null)
    }

    private fun processTempleConsistent(
        map: MutableMap<String, MutableAchievement>,
        yearMonths: Map<Int, Set<Int>>,
        visits: List<Visit>,
        currentYear: Int
    ) {
        val ach = map["ach12MT"] ?: return
        val months = yearMonths[currentYear] ?: emptySet()
        if (months.size == 12) {
            // Find the visit that completed month 12 (December) for current year
            val decVisit = visits.find { v ->
                val d = v.dateVisited ?: return@find false
                val c = Calendar.getInstance().apply { time = d }
                c.get(Calendar.YEAR) == currentYear && c.get(Calendar.MONTH) + 1 == 12
            }
            ach.name = resources.getString(R.string.achievement_temple_consistent_title, currentYear)
            ach.details = resources.getString(R.string.achievement_temple_consistent_details)
            ach.achieved = decVisit?.dateVisited
            ach.placeAchieved = decVisit?.holyPlaceName
        }
    }

    private fun calcProgress(
        ach: MutableAchievement,
        baptismsTotal: Int,
        initiatoriesTotal: Int,
        endowmentsTotal: Int,
        sealingsTotal: Int,
        shiftHoursTotal: Double,
        templesCount: Int,
        historicCount: Int,
        yearMonths: Map<Int, Set<Int>>,
        currentYear: Int,
        isOrdinanceWorker: Boolean
    ): Achievement {
        val iconName = ach.iconName
        val (threshold, type) = parseIconName(iconName) ?: return ach.toAchievement(0f, null)

        val currentValue = when (type) {
            'B' -> baptismsTotal
            'I' -> initiatoriesTotal
            'E' -> endowmentsTotal
            'S' -> sealingsTotal
            'W' -> if (isOrdinanceWorker) shiftHoursTotal.toInt() else 0
            'T' -> templesCount
            'H' -> historicCount
            'M' -> yearMonths[currentYear]?.size ?: 0 // Temple Consistent
            else -> 0
        }

        val progress = if (threshold > 0) (currentValue.toFloat() / threshold).coerceIn(0f, 1f) else 0f
        val remaining = (threshold - currentValue).coerceAtLeast(0)
        return ach.toAchievement(progress, remaining)
    }

    private fun parseIconName(iconName: String): Pair<Int, Char>? {
        return when {
            iconName == "ach12MT" -> Pair(12, 'M')
            iconName.startsWith("ach") && iconName.length >= 4 -> {
                val numPart = iconName.substring(3, iconName.length - 1)
                val typeChar = iconName.last()
                numPart.toIntOrNull()?.let { Pair(it, typeChar) }
            }
            else -> null
        }
    }

    private data class MutableAchievement(
        var name: String,
        var details: String,
        val iconName: String,
        var achieved: Date? = null,
        var placeAchieved: String? = null
    ) {
        fun toAchievement(progress: Float?, remaining: Int?): Achievement = Achievement(
            name = name,
            details = details,
            iconName = iconName,
            achieved = achieved,
            placeAchieved = placeAchieved,
            progress = progress,
            remaining = remaining
        )
    }
}
