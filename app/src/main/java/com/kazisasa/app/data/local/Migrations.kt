package com.kazisasa.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real migrations, not fallbackToDestructiveMigration - Phase 3a of the v3
 * general-search spec. The database has been version 1 with
 * fallbackToDestructiveMigration() since the very first build (see the
 * comment on AppDatabase.getInstance() explaining why that was fine
 * pre-release), so this is the first schema change that needs to actually
 * preserve data across an upgrade rather than just wipe and recreate.
 *
 * MIGRATION_1_2 adds the seven v3 columns to `opportunities` (industry,
 * specialisations, years_experience_min/max, education_required,
 * education_field, contract_type) - see OpportunityEntity.kt for the Kotlin
 * side and OpportunityMappers.kt for how they're populated from the feed.
 *
 * Column names below match the Kotlin property names exactly (Room's default
 * naming, since none of the new OpportunityEntity fields have @ColumnInfo
 * overrides) - if that ever changes on the Kotlin side, this migration's
 * ADD COLUMN statements need to change with it.
 *
 * Default values for the ADD COLUMN statements are chosen to match exactly
 * what a freshly-inserted row with the Kotlin defaults would produce via
 * Converters.kt:
 *   - List<String> columns (specialisations, educationField) are TEXT NOT
 *     NULL DEFAULT '[]' - matches Converters.fromStringList(emptyList()),
 *     which is literally the string "[]" via kotlinx.serialization.
 *   - Nullable String/enum-as-TEXT columns (industry, educationRequired,
 *     contractType) are TEXT DEFAULT NULL.
 *   - Nullable Int columns (yearsExperienceMin/Max) are INTEGER DEFAULT NULL.
 *
 * Tested: NOT run against a real populated device database from this
 * sandbox (no Android SDK / instrumented test runner available here - see
 * the project's established pattern of verifying Kotlin changes via the
 * real GitHub Actions build rather than claiming local verification that
 * didn't happen). Do run this through a real migration test
 * (androidx.room.testing.MigrationTestHelper) before relying on it against
 * real user data - see docs/RELEASE_BUILD.md's testing checklist.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE opportunities ADD COLUMN industry TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN specialisations TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN yearsExperienceMin INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN yearsExperienceMax INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN educationRequired TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN educationField TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE opportunities ADD COLUMN contractType TEXT DEFAULT NULL")
    }
}
