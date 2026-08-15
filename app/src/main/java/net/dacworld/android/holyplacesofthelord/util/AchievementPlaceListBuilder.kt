package net.dacworld.android.holyplacesofthelord.util

import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.database.AppDatabase
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.model.Visit
import net.dacworld.android.holyplacesofthelord.model.achievementType
import net.dacworld.android.holyplacesofthelord.model.threshold
import java.util.Date

object AchievementPlaceListBuilder {

    suspend fun places(
        achievement: Achievement,
        visitDao: VisitDao,
        profileId: String?,
        db: AppDatabase
    ): List<String> {
        val type = achievement.achievementType ?: return emptyList()
        val threshold = achievement.threshold ?: return emptyList()
        val unlockDate = achievement.achieved ?: return emptyList()

        val canonicalMap = HistoricalNamesHelper.buildCanonicalNameMap(db.templeDao(), db.nameChangeDao())
        val visits = visitDao.getVisitsForAchievementCalcByProfileOnce(profileId)
            .filter { visit ->
                val date = visit.dateVisited ?: return@filter false
                !date.after(unlockDate)
            }
            .sortedBy { it.dateVisited ?: Date(0) }

        val distinct = mutableListOf<String>()
        for (visit in visits) {
            val rawName = visit.holyPlaceName.orEmpty()
            val placeName = canonicalMap[rawName] ?: rawName
            if (placeName.isEmpty()) continue
            when (type) {
                "T" -> {
                    if ((visit.type == "T" || visit.type == "C") && placeName !in distinct) {
                        distinct.add(placeName)
                        if (distinct.size >= threshold) return distinct
                    }
                }
                "H" -> {
                    if (visit.type == "H" && placeName !in distinct) {
                        distinct.add(placeName)
                        if (distinct.size >= threshold) return distinct
                    }
                }
                "B" -> addIfOrdinance(distinct, placeName, visit, visit.baptisms)
                "I" -> addIfOrdinance(distinct, placeName, visit, visit.initiatories)
                "E" -> addIfOrdinance(distinct, placeName, visit, visit.endowments)
                "S" -> addIfOrdinance(distinct, placeName, visit, visit.sealings)
                "W" -> {
                    if ((visit.shiftHrs ?: 0.0) > 0 && placeName !in distinct &&
                        (visit.type == "T" || visit.type == "C")
                    ) {
                        distinct.add(placeName)
                    }
                }
            }
        }
        return distinct
    }

    private fun addIfOrdinance(
        distinct: MutableList<String>,
        placeName: String,
        visit: Visit,
        count: Short?
    ) {
        if ((visit.type == "T" || visit.type == "C") && (count ?: 0) > 0 && placeName !in distinct) {
            distinct.add(placeName)
        }
    }
}
