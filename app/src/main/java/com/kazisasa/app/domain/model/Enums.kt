package com.kazisasa.app.domain.model

/**
 * Shared enums for the domain layer. These mirror the feed contract (spec §24)
 * and the Room schema (spec §26) - kept in one place so all three layers stay
 * in lockstep as the feed evolves.
 */

enum class OpportunityType { JOB, FELLOWSHIP, GRANT, INTERNSHIP, PROGRAMME }

enum class OrgType { EMPLOYER, NGO, MULTILATERAL, PRIVATE, UNVERIFIED }

enum class WorkMode { ONSITE, HYBRID, REMOTE_KENYA, REMOTE_REGIONAL, REMOTE_GLOBAL }

/** Ordinal order matters: used for seniority-gap arithmetic in the fit engine. */
enum class Seniority { ENTRY, MID, SENIOR, LEADERSHIP }

enum class LocationScope { LOCAL, NATIONAL, REGIONAL, INTERNATIONAL }

enum class SourceConfidence { OFFICIAL, AGGREGATED, COMMUNITY, UNVERIFIED }

enum class DeadlineConfidence { EXPLICIT, INFERRED, UNKNOWN }

/**
 * v3 additions (feed spec §4.1 / SCHEMA.md "v3 additions"). Both mirror
 * validate_feed.py's VALID_CONTRACT_TYPES / VALID_EDUCATION_LEVELS exactly -
 * keep these two in lockstep with the feed repo's validator if either changes.
 */
enum class ContractType { PERMANENT, CONTRACT, FIXED_TERM, PART_TIME, CONSULTANT, VOLUNTEER, UNKNOWN }

/** Ordinal order matters for future "meets/exceeds required education" comparisons. */
enum class EducationLevel { NONE, SECONDARY, DIPLOMA, BACHELOR, MASTERS, PHD }

/**
 * Per (profile, opportunity) triage state - spec §9.
 *
 * SAVED was deliberately removed from this enum (implementation-recommendations
 * doc §12, "preferred solution") - it duplicated the saved_opportunities table
 * and created ambiguity on unsave (was the item now untriaged again, or hidden
 * forever?). Saving now always implies KEPT (see SavedRepository.save), and
 * unsaving leaves the triage state at KEPT rather than reverting it (doc §13:
 * "the user already showed some interest, so it should not return as a
 * completely new item"). A saved item is therefore always a subset of kept
 * items, never a separate state.
 */
enum class TriageAction { UNSEEN, KEPT, SKIPPED, DISMISSED }

/** What the UI leads with - spec §8.3. Never shown higher than the reasons justify. */
enum class FitBand { STRONG, GOOD, STRETCH }

/** The eight scoring dimensions - spec §8.2. */
enum class FitDimension {
    SKILL_MATCH, SECTOR_MATCH, SENIORITY_MATCH, LOCATION_FIT,
    MODE_FIT, GROWTH_SIGNAL, RECENCY, DEADLINE_RISK
}

enum class Direction { POSITIVE, NEUTRAL, CAUTION }

/** Feed-asserted facts (spec §24.3) - distinct from computed fit signals. */
enum class OpportunityFlag { URGENT, RELOCATION_WORTHY, AI_RELEVANT, HIDDEN_GEM, ELIGIBILITY_REVIEW, SAMPLE }
