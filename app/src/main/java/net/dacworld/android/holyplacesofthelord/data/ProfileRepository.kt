package net.dacworld.android.holyplacesofthelord.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import net.dacworld.android.holyplacesofthelord.dao.ProfileDao
import net.dacworld.android.holyplacesofthelord.dao.VisitDao
import net.dacworld.android.holyplacesofthelord.model.Profile
import net.dacworld.android.holyplacesofthelord.model.ProfileContract
import java.util.Date
import java.util.UUID

/**
 * Single source of truth for all profile-related data.
 *
 * Wraps [ProfileDao] (Room) and [UserPreferencesManager] (DataStore) in a clean
 * domain interface consumed by [ProfileViewModel].  Mirrors the responsibilities
 * of iOS `ProfileManager`.
 */
class ProfileRepository(
    private val profileDao: ProfileDao,
    private val visitDao: VisitDao,
    private val preferencesManager: UserPreferencesManager
) {

    // ── Observed streams ──────────────────────────────────────────────────────

    val profiles: Flow<List<Profile>> = profileDao.getAll()

    val activeProfileId: Flow<String?> = preferencesManager.activeProfileIdFlow

    val profilesEnabled: Flow<Boolean> = preferencesManager.profilesEnabledFlow

    /**
     * Profile id used to scope visits, stats, achievements, and exports.
     * Always the active profile — even when the multi-profile management UI is disabled.
     */
    val scopedProfileId: Flow<String?> = activeProfileId

    /**
     * Emits the currently-active [Profile], falling back to the default profile
     * when no active-profile id is stored (mirrors iOS `activeProfile()`).
     */
    val activeProfile: Flow<Profile?> = preferencesManager.activeProfileIdFlow
        .flatMapLatest { storedId ->
            if (storedId != null) profileDao.observeById(storedId) else flowOf(null)
        }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /**
     * Creates a new profile capped at [ProfileContract.MAX_PROFILES].
     * Returns `null` when the limit has been reached.
     */
    suspend fun createProfile(name: String, iconName: String = ProfileContract.DEFAULT_ICON_NAME): Profile? =
        withContext(Dispatchers.IO) {
            if (profileDao.count() >= ProfileContract.MAX_PROFILES) {
                Log.w(TAG, "Profile limit of ${ProfileContract.MAX_PROFILES} reached")
                return@withContext null
            }
            val profile = Profile(
                profileId = UUID.randomUUID().toString(),
                name = name,
                isDefault = false,
                iconName = iconName,
                createdDate = Date()
            )
            profileDao.insert(profile)
            Log.i(TAG, "Created profile '${profile.name}' (${profile.profileId})")
            profile
        }

    suspend fun updateProfile(profile: Profile) = withContext(Dispatchers.IO) {
        profileDao.update(profile)
    }

    /**
     * Deletes a non-default profile and all its associated visits, then switches
     * the active profile to the default if the deleted profile was active.
     * Mirrors iOS `ProfileManager.deleteProfile(_:)`.
     */
    suspend fun deleteProfile(profile: Profile) = withContext(Dispatchers.IO) {
        if (profile.isDefault) {
            Log.w(TAG, "Attempted to delete the default profile — ignored")
            return@withContext
        }
        val deletedVisits = profileDao.deleteVisitsForProfile(profile.profileId)
        profileDao.delete(profile)
        Log.i(TAG, "Deleted profile '${profile.name}' and $deletedVisits visits")

        // If the deleted profile was active, switch to the default
        val currentActiveId = preferencesManager.activeProfileIdFlow.first()
        if (currentActiveId == profile.profileId) {
            val defaultProfile = profileDao.getDefault()
            defaultProfile?.let { setActiveProfile(it.profileId) }
        }
    }

    // ── Active profile ────────────────────────────────────────────────────────

    suspend fun setActiveProfile(profileId: String) {
        preferencesManager.saveActiveProfileId(profileId)
        Log.i(TAG, "Active profile set to $profileId")
    }

    // ── Feature toggle ────────────────────────────────────────────────────────

    /**
     * Enables or disables the profile feature.
     * When enabling, ensures profile state is fully repaired (default profile, visit assignment, active id).
     */
    suspend fun setProfilesEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        preferencesManager.saveProfilesEnabled(enabled)
        repairProfileState()
    }

    /**
     * Ensures profile tables, default profile, visit assignments, and active profile id
     * are consistent. Safe to call on every launch — all steps are idempotent.
     * Called synchronously from [net.dacworld.android.holyplacesofthelord.MyApplication]
     * before the UI opens the database for queries.
     */
    suspend fun repairProfileState() = withContext(Dispatchers.IO) {
        val defaultProfile = ensureDefaultProfile()
        val migrated = profileDao.assignUnassignedVisitsToProfile(defaultProfile.profileId)
        if (migrated > 0) {
            Log.i(TAG, "Assigned $migrated unassigned visits to default profile")
        }
        repairActiveProfileId(defaultProfile.profileId)
    }

    /**
     * Creates the default "Me" profile on first enable if one doesn't already exist.
     * Prefer [repairProfileState] at startup; this remains for callers that only need creation.
     */
    suspend fun createDefaultProfileIfNeeded() = withContext(Dispatchers.IO) {
        repairProfileState()
    }

    private suspend fun ensureDefaultProfile(): Profile {
        profileDao.getDefault()?.let { return it }

        val defaultProfile = Profile(
            profileId = UUID.randomUUID().toString(),
            name = ProfileContract.DEFAULT_PROFILE_NAME,
            isDefault = true,
            iconName = ProfileContract.DEFAULT_ICON_NAME,
            createdDate = Date()
        )
        profileDao.insert(defaultProfile)
        Log.i(TAG, "Created default profile '${defaultProfile.name}' (${defaultProfile.profileId})")
        return defaultProfile
    }

    private suspend fun repairActiveProfileId(defaultProfileId: String) {
        val currentActive = preferencesManager.activeProfileIdFlow.first()
        when {
            currentActive == null -> {
                setActiveProfile(defaultProfileId)
            }
            profileDao.getById(currentActive) == null -> {
                Log.w(TAG, "Active profile id $currentActive not found — resetting to default")
                setActiveProfile(defaultProfileId)
            }
        }
    }

    // ── Copy visits ───────────────────────────────────────────────────────────

    /**
     * Copies a set of visits to a target profile by duplicating each visit record
     * with the new [targetProfileId].  Mirrors iOS `VisitTableVC` "Copy to Profile".
     *
     * @param visitIds  Row IDs of visits to copy.
     * @param targetProfileId  Profile to copy visits into.
     */
    suspend fun copyVisitsToProfile(visitIds: List<Long>, targetProfileId: String) =
        withContext(Dispatchers.IO) {
            var copied = 0
            for (id in visitIds) {
                val original = visitDao.getVisitWithPictureById(id) ?: continue
                val copy = original.copy(id = 0L, profileId = targetProfileId)
                visitDao.insertVisit(copy)
                copied++
            }
            Log.i(TAG, "Copied $copied visits to profile $targetProfileId")
        }

    companion object {
        private const val TAG = "ProfileRepository"
    }
}
