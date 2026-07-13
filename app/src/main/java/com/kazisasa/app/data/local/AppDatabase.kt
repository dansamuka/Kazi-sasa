package com.kazisasa.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kazisasa.app.data.local.dao.FeedMetaDao
import com.kazisasa.app.data.local.dao.OpportunityDao
import com.kazisasa.app.data.local.dao.ProfileDao
import com.kazisasa.app.data.local.dao.ReminderDao
import com.kazisasa.app.data.local.dao.SavedOpportunityDao
import com.kazisasa.app.data.local.dao.TriageDao
import com.kazisasa.app.data.local.entity.FeedMetaEntity
import com.kazisasa.app.data.local.entity.OpportunityEntity
import com.kazisasa.app.data.local.entity.ProfileEntity
import com.kazisasa.app.data.local.entity.ReminderEntity
import com.kazisasa.app.data.local.entity.SavedOpportunityEntity
import com.kazisasa.app.data.local.entity.TriageStateEntity

@Database(
    entities = [
        OpportunityEntity::class,
        ProfileEntity::class,
        TriageStateEntity::class,
        SavedOpportunityEntity::class,
        ReminderEntity::class,
        FeedMetaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun opportunityDao(): OpportunityDao
    abstract fun profileDao(): ProfileDao
    abstract fun triageDao(): TriageDao
    abstract fun savedOpportunityDao(): SavedOpportunityDao
    abstract fun reminderDao(): ReminderDao
    abstract fun feedMetaDao(): FeedMetaDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kazi_sasa.db",
                )
                    // v1 -> v2 (Phase 3a of the v3 general-search spec) is the
                    // first real migration this app has - see Migrations.kt for
                    // what it does and why. fallbackToDestructiveMigration()
                    // stays in place as a safety net ONLY for any future version
                    // gap that ships without its own explicit Migration - Room
                    // always prefers an addMigrations() match when one exists
                    // for the exact (from, to) version pair, so this does not
                    // weaken the v1->v2 path itself.
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
