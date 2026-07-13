package com.kazisasa.app.di

import android.content.Context
import com.kazisasa.app.data.datastore.ActiveProfileStore
import com.kazisasa.app.data.local.AppDatabase
import com.kazisasa.app.data.remote.FeedApiService
import com.kazisasa.app.data.repository.FeedRepository
import com.kazisasa.app.data.repository.OpportunityRepository
import com.kazisasa.app.data.repository.ProfileRepository
import com.kazisasa.app.data.repository.ReminderRepository
import com.kazisasa.app.data.repository.SavedRepository
import com.kazisasa.app.data.repository.TriageRepository
import com.kazisasa.app.domain.fit.FitEngine
import com.kazisasa.app.domain.fit.FitEngineImpl
import com.kazisasa.app.work.ReminderScheduler

/**
 * A plain service locator rather than Hilt/Dagger. This is a deliberate scope call,
 * not an oversight: this project can't be Gradle-built in the environment it was
 * authored in (no Google Maven access - see README "What's verified vs. not"), so
 * adding an annotation-processor-based DI framework would mean shipping KSP/Hilt
 * wiring that has never actually been compiled. A manual container is fully
 * explicit, has no codegen step, and is easy to read top-to-bottom. Swapping to
 * Hilt later is a mechanical refactor if the app grows enough to want it - none of
 * the classes below depend on how they're constructed.
 */
class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val database by lazy { AppDatabase.getInstance(appContext) }
    private val feedApi by lazy { FeedApiService.create() }
    private val activeProfileStore by lazy { ActiveProfileStore(appContext) }
    private val reminderScheduler by lazy { ReminderScheduler(appContext) }

    val fitEngine: FitEngine by lazy { FitEngineImpl() }

    val feedRepository by lazy {
        FeedRepository(
            context = appContext,
            api = feedApi,
            opportunityDao = database.opportunityDao(),
            profileDao = database.profileDao(),
            feedMetaDao = database.feedMetaDao(),
        )
    }

    val opportunityRepository by lazy {
        OpportunityRepository(dao = database.opportunityDao(), fitEngine = fitEngine)
    }

    val profileRepository by lazy {
        ProfileRepository(dao = database.profileDao(), activeProfileStore = activeProfileStore)
    }

    val triageRepository by lazy { TriageRepository(dao = database.triageDao()) }

    val savedRepository by lazy {
        SavedRepository(
            savedDao = database.savedOpportunityDao(),
            opportunityDao = database.opportunityDao(),
            triageDao = database.triageDao(),
        )
    }

    val reminderRepository by lazy {
        ReminderRepository(dao = database.reminderDao(), scheduler = reminderScheduler)
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
    }
}
