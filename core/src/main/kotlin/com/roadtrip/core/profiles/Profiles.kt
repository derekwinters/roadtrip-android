package com.roadtrip.core.profiles

import com.roadtrip.core.api.ApiException
import com.roadtrip.core.api.Profile
import com.roadtrip.core.api.RoadtripApi
import com.roadtrip.core.common.Clock
import com.roadtrip.core.common.Role
import com.roadtrip.core.storage.CacheStore
import com.roadtrip.core.storage.SelectedProfileStore
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Launch profile picker: avatars from GET /api/profiles, tapping one selects it — no
 * passwords anywhere; the selection persists across restarts via [SelectedProfileStore]
 * (AND-001/002). A zero-profile server puts the picker into the first-run family setup
 * flow instead of the grid (AND-007).
 */
class ProfilePicker(
    private val api: RoadtripApi,
    private val store: SelectedProfileStore,
) {
    suspend fun load(): List<Profile> = api.getProfiles()

    fun select(profile: Profile) {
        store.set(profile)
    }

    fun selected(): Profile? = store.get()

    /**
     * First-run bootstrap (AND-007): creates the very first profile and signs in as them.
     * The parent role is enforced by the flow — the backend's zero-profiles bootstrap
     * accepts an unauthenticated POST /api/profiles only for role=parent — and the call
     * carries no `X-Profile-Id` because nobody is signed in yet ([RoadtripApi]
     * implementations omit the header when there is no selected profile). No avatar is
     * sent: the wizard has no avatar input and the server assigns its default.
     *
     * Failures never dead-end the wizard (AND-009). A 401 means the server's bootstrap
     * is closed, so the flow re-probes `GET /api/profiles` to find out why: profiles
     * appeared (setup finished elsewhere — lost race, or a retry whose first attempt
     * committed) hands the list back for the ordinary grid, selecting nobody; a
     * still-empty list means the server predates first-run setup, reported as an
     * actionable update-the-server message rather than the raw envelope. Transport
     * failures on either call keep a human offline message.
     */
    suspend fun runFirstRunCreate(name: String): FirstRunCreateResult {
        val created = try {
            api.createProfile(name, avatar = null, role = Role.PARENT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            return if (e.status == 401) {
                recoverFromClosedBootstrap()
            } else {
                FirstRunCreateResult.Failed(e.message ?: CREATE_FAILED_MESSAGE)
            }
        } catch (e: IOException) {
            return FirstRunCreateResult.Failed(OFFLINE_MESSAGE)
        }
        store.set(created)
        return FirstRunCreateResult.SignedIn(created)
    }

    private suspend fun recoverFromClosedBootstrap(): FirstRunCreateResult = try {
        val profiles = load()
        if (profiles.isEmpty()) {
            // A live server that lists zero profiles yet refuses the bootstrap create
            // predates PRO-008 — only a server update can unblock first-run setup.
            FirstRunCreateResult.Failed(SERVER_TOO_OLD_MESSAGE)
        } else {
            FirstRunCreateResult.ProfilesAppeared(profiles)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        FirstRunCreateResult.Failed(OFFLINE_MESSAGE)
    }

    companion object {
        const val OFFLINE_MESSAGE =
            "Can't reach the trip server — check the connection and try again."
        const val SERVER_TOO_OLD_MESSAGE =
            "The trip server refused first-run setup — it may be running an older version. " +
                "Update the server, then try again."
        const val CREATE_FAILED_MESSAGE = "Could not create the profile"
    }
}

/** Outcome of the wizard's create attempt (AND-007, AND-009). */
sealed class FirstRunCreateResult {
    /** Created and signed in — leave the picker for the app proper. */
    data class SignedIn(val profile: Profile) : FirstRunCreateResult()

    /**
     * The bootstrap was closed but profiles exist now: show the select-only grid with
     * this fresh list. Nothing is auto-selected — the person holding the device picks
     * who they are (AND-009).
     */
    data class ProfilesAppeared(val profiles: List<Profile>) : FirstRunCreateResult()

    /** Human-readable, retryable failure — never a raw 401 envelope (AND-009). */
    data class Failed(val message: String) : FirstRunCreateResult()
}

/** What the launch picker shows (AND-001/007/008). */
sealed class ProfilePickerState {
    /** Profiles not loaded yet (server or cache pending). */
    object Loading : ProfilePickerState()

    /** Zero profiles on the server: first-run "Set up your family" flow (AND-007). */
    object SetupRequired : ProfilePickerState()

    /** Select-only avatar grid — no create affordance once profiles exist (AND-007). */
    data class Grid(val profiles: List<Profile>) : ProfilePickerState()

    /**
     * The server could not be reached and no cached profile list is usable (AND-008):
     * instead of dead-ending, the picker shows the current server address, an explicit
     * Retry, and the pre-sign-in server-address editor. Never rendered as
     * [SetupRequired] — only a live server can confirm zero profiles, and creating the
     * first profile needs the server anyway.
     */
    object Unreachable : ProfilePickerState()
}

object ProfilePickerReducer {
    /**
     * [loadFailed] = the `GET /api/profiles` probe failed and [profiles] is the cached
     * list, if any. A non-empty cache still renders the grid so offline selection keeps
     * working (AND-005); a null or empty cache is [ProfilePickerState.Unreachable],
     * never [ProfilePickerState.SetupRequired] (AND-008).
     */
    fun reduce(profiles: List<Profile>?, loadFailed: Boolean = false): ProfilePickerState = when {
        loadFailed -> if (profiles.isNullOrEmpty()) {
            ProfilePickerState.Unreachable
        } else {
            ProfilePickerState.Grid(profiles)
        }
        profiles == null -> ProfilePickerState.Loading
        profiles.isEmpty() -> ProfilePickerState.SetupRequired
        else -> ProfilePickerState.Grid(profiles)
    }
}

/**
 * Owns the picker's probe → cache → reduce cycle so the Compose screen stays thin
 * (AND-008): a successful probe of `GET /api/profiles` refreshes the cache and reduces
 * per AND-007; a failed one falls back to the cached list (AND-005) or Unreachable.
 * Callers re-run [refresh] on server-address changes, explicit Retry, and connectivity
 * return — every run is a fresh probe.
 */
class ProfilePickerLoader(
    private val picker: ProfilePicker,
    private val cache: CacheStore<List<Profile>>,
    private val clock: Clock,
) {
    suspend fun refresh(): ProfilePickerState = try {
        val loaded = picker.load()
        cache.write(loaded, clock.now())
        ProfilePickerReducer.reduce(loaded)
    } catch (e: CancellationException) {
        // The caller's effect was cancelled (url change / retry restarting it): rethrow —
        // an aborted probe must never masquerade as a result.
        throw e
    } catch (e: Exception) {
        ProfilePickerReducer.reduce(cache.read()?.value, loadFailed = true)
    }
}

/**
 * Role-driven feature visibility (AND-003): parent-only sections are hidden for kid
 * profiles, based purely on the profile record's role attribute.
 */
data class FeatureVisibility(
    val destinationEditing: Boolean,
    val settingsTuning: Boolean,
    val profileAdmin: Boolean,
    val trackerToggle: Boolean,
    val journal: Boolean,
    val map: Boolean,
    val games: Boolean,
    val checklist: Boolean,
    val trip: Boolean,
)

object FeatureVisibilityRules {
    fun forRole(role: Role): FeatureVisibility {
        val parent = role == Role.PARENT
        return FeatureVisibility(
            destinationEditing = parent,
            settingsTuning = parent,
            profileAdmin = parent,
            trackerToggle = parent, // any device class; kid profiles never see it (ANDLOC-003)
            journal = true,
            map = true,
            games = true,
            checklist = true,
            trip = true,
        )
    }
}
