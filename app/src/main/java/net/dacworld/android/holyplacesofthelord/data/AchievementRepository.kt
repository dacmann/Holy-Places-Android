package net.dacworld.android.holyplacesofthelord.data

import android.content.Context
import android.content.res.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.dacworld.android.holyplacesofthelord.R
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Achievement
import net.dacworld.android.holyplacesofthelord.util.AchievementCalculator
import java.util.Calendar

/**
 * Repository for achievement management.
 * Holds achievement definitions and exposes computed achievements based on visit data.
 */
class AchievementRepository(
    private val context: Context,
    private val visitDao: VisitDao,
    private val userPreferencesManager: UserPreferencesManager,
    private val scope: CoroutineScope
) {
    private val calculator = AchievementCalculator(context.resources, visitDao, userPreferencesManager)

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    private val _completedAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val completedAchievements: StateFlow<List<Achievement>> = _completedAchievements.asStateFlow()

    private val _incompleteAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val incompleteAchievements: StateFlow<List<Achievement>> = _incompleteAchievements.asStateFlow()

    init {
        scope.launch {
            combine(
                visitDao.getAllVisits(),
                userPreferencesManager.enableHoursWorkedFlow
            ) { visits, isOrdinanceWorker ->
                Pair(visits, isOrdinanceWorker)
            }.collect { (visits, isOrdinanceWorker) ->
                recalculate(visits, isOrdinanceWorker)
            }
        }
    }

    /**
     * Recalculate achievements from visit data.
     * Called when visits change (save, delete, import).
     */
    suspend fun recalculate() {
        val visits = visitDao.getAllVisitsForAchievementCalc()
        val isOrdinanceWorker = userPreferencesManager.enableHoursWorkedFlow.first()
        recalculate(visits, isOrdinanceWorker)
    }

    private fun recalculate(visits: List<net.dacworld.android.holyplacesofthelord.model.Visit>, isOrdinanceWorker: Boolean) {
        val computed = calculator.calculate(visits, isOrdinanceWorker)
        _achievements.value = computed
        _completedAchievements.value = computed.filter { it.achieved != null }
        _incompleteAchievements.value = computed.filter { it.achieved == null }
    }

    companion object {
        /** Baptisms (B) - 6 levels */
        val B_THRESHOLDS = listOf(25, 50, 100, 200, 400, 800)
        /** Initiatories (I) - 6 levels */
        val I_THRESHOLDS = listOf(25, 50, 100, 200, 400, 800)
        /** Endowments (E) - 10 levels */
        val E_THRESHOLDS = listOf(10, 25, 50, 100, 150, 200, 300, 400, 550, 700)
        /** Sealings (S) - 6 levels */
        val S_THRESHOLDS = listOf(50, 100, 200, 400, 800, 1600)
        /** Worker Hours (W) - 6 levels */
        val W_THRESHOLDS = listOf(50, 100, 200, 400, 800, 1600)
        /** Temples Visited (T) - 12 levels */
        val T_THRESHOLDS = listOf(10, 20, 30, 40, 50, 60, 75, 100, 125, 150, 175, 200)
        /** Historic Sites (H) - 8 levels */
        val H_THRESHOLDS = listOf(10, 25, 40, 55, 75, 100, 125, 150)

        /**
         * Build the list of achievement definitions for initAchievements.
         * Returns (iconName, name, details) for each fixed achievement.
         * Uses string resources for localization.
         */
        fun buildFixedAchievementDefinitions(resources: Resources, isOrdinanceWorker: Boolean): List<Triple<String, String, String>> {
            val defs = mutableListOf<Triple<String, String, String>>()

            val baptismsNames = resources.getStringArray(R.array.achievement_baptisms_names)
            B_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}B", baptismsNames[i], resources.getString(R.string.achievement_details_complete_baptisms, t))
                )
            }

            val initiatoriesNames = resources.getStringArray(R.array.achievement_initiatories_names)
            I_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}I", initiatoriesNames[i], resources.getString(R.string.achievement_details_complete_initiatories, t))
                )
            }

            val endowmentsNames = resources.getStringArray(R.array.achievement_endowments_names)
            E_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}E", endowmentsNames[i], resources.getString(R.string.achievement_details_complete_endowments, t))
                )
            }

            val sealingsNames = resources.getStringArray(R.array.achievement_sealings_names)
            S_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}S", sealingsNames[i], resources.getString(R.string.achievement_details_complete_sealings, t))
                )
            }

            if (isOrdinanceWorker) {
                val workerNames = resources.getStringArray(R.array.achievement_worker_names)
                W_THRESHOLDS.forEachIndexed { i, t ->
                    defs.add(
                        Triple("ach${t}W", workerNames[i], resources.getString(R.string.achievement_details_work_hours, t))
                    )
                }
            }

            val templeNames = resources.getStringArray(R.array.achievement_temple_names)
            T_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}T", templeNames[i], resources.getString(R.string.achievement_details_visit_temples, t))
                )
            }

            val historyNames = resources.getStringArray(R.array.achievement_history_names)
            H_THRESHOLDS.forEachIndexed { i, t ->
                defs.add(
                    Triple("ach${t}H", historyNames[i], resources.getString(R.string.achievement_details_visit_historic, t))
                )
            }
            return defs
        }

        /**
         * Temple Consistent achievement for a given year.
         * Icon is always ach12MT (no year-specific image).
         */
        fun templeConsistentDefinition(resources: Resources, year: Int): Triple<String, String, String> {
            val name = resources.getString(R.string.achievement_temple_consistent_title, year)
            val details = resources.getString(R.string.achievement_temple_consistent_details)
            return Triple("ach12MT", name, details)
        }

        fun getCurrentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
    }
}
