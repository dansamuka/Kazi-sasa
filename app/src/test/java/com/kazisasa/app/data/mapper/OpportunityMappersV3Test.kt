package com.kazisasa.app.data.mapper

import com.kazisasa.app.data.remote.dto.LocationDto
import com.kazisasa.app.data.remote.dto.OpportunityDto
import com.kazisasa.app.data.remote.dto.OrganisationDto
import com.kazisasa.app.data.remote.dto.SourceDto
import com.kazisasa.app.domain.model.ContractType
import com.kazisasa.app.domain.model.EducationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers Phase 3a of the v3 general-search spec: the seven new fields added
 * to OpportunityDto/Opportunity/OpportunityEntity (industry, specialisations,
 * years_experience_min/max, education_required, education_field,
 * contract_type).
 *
 * Deliberately checks the exact bug class found and fixed three separate
 * times during this project's CI runs (enumOrNull(x) called without an
 * explicit <Type> argument, which infers the nullable target type and
 * violates enumOrNull's own T : Enum<T> bound) - see git history around
 * OpportunityMappers.kt for the real incidents this is guarding against.
 * A regression here would be a compile error, not a runtime failure, but a
 * green build alone doesn't prove the *values* round-trip correctly - that's
 * what these tests actually check.
 */
class OpportunityMappersV3Test {

    private fun sampleDto(
        industry: String? = "energy_environment",
        contractType: String? = "unknown",
        educationRequired: String? = "diploma",
    ) = OpportunityDto(
        id = "test-1",
        title = "Senior Solar Field Technician",
        opportunityType = "job",
        organisation = OrganisationDto(name = "Sun King", type = "private", verified = true),
        location = LocationDto(raw = "Nairobi, Kenya", country = "Kenya"),
        source = SourceDto(name = "Sun King", url = "https://sunking.pinpointhq.com", confidence = "official"),
        industry = industry,
        specialisations = listOf("field_operations"),
        yearsExperienceMin = 5,
        yearsExperienceMax = 8,
        educationRequired = educationRequired,
        educationField = listOf("electrical engineering"),
        contractType = contractType,
    )

    @Test
    fun `v3 fields survive DTO to Entity mapping`() {
        val entity = sampleDto().toEntity(fetchedAtMillis = 1_000L)

        assertEquals("energy_environment", entity.industry)
        assertEquals(listOf("field_operations"), entity.specialisations)
        assertEquals(5, entity.yearsExperienceMin)
        assertEquals(8, entity.yearsExperienceMax)
        assertEquals(EducationLevel.DIPLOMA, entity.educationRequired)
        assertEquals(listOf("electrical engineering"), entity.educationField)
        assertEquals(ContractType.UNKNOWN, entity.contractType)
    }

    @Test
    fun `v3 fields survive the full DTO to Entity to Domain round trip`() {
        val entity = sampleDto().toEntity(fetchedAtMillis = 1_000L)
        val domain = entity.toDomain()

        assertEquals("energy_environment", domain.industry)
        assertEquals(listOf("field_operations"), domain.specialisations)
        assertEquals(5, domain.yearsExperienceMin)
        assertEquals(8, domain.yearsExperienceMax)
        assertEquals(EducationLevel.DIPLOMA, domain.educationRequired)
        assertEquals(ContractType.UNKNOWN, domain.contractType)
    }

    @Test
    fun `null v3 fields stay null rather than crashing or defaulting wrongly`() {
        val entity = sampleDto(industry = null, contractType = null, educationRequired = null).toEntity(1_000L)

        assertNull(entity.industry)
        assertNull(entity.contractType)
        assertNull(entity.educationRequired)
    }

    @Test
    fun `malformed enum strings degrade to null, never crash`() {
        // spec §14.6 - malformed/unrecognised feed values must not crash the
        // pipeline. "not_a_real_contract_type" and "not_a_real_education_level"
        // are not valid enum constants for ContractType/EducationLevel.
        val entity = sampleDto(
            contractType = "not_a_real_contract_type",
            educationRequired = "not_a_real_education_level",
        ).toEntity(1_000L)

        assertNull(entity.contractType)
        assertNull(entity.educationRequired)
    }

    @Test
    fun `domain to entity reverse mapping preserves v3 fields`() {
        val original = sampleDto().toEntity(1_000L).toDomain()
        val roundTripped = original.toEntity(2_000L)

        assertEquals(original.industry, roundTripped.industry)
        assertEquals(original.specialisations, roundTripped.specialisations)
        assertEquals(original.yearsExperienceMin, roundTripped.yearsExperienceMin)
        assertEquals(original.yearsExperienceMax, roundTripped.yearsExperienceMax)
        assertEquals(original.educationRequired, roundTripped.educationRequired)
        assertEquals(original.educationField, roundTripped.educationField)
        assertEquals(original.contractType, roundTripped.contractType)
    }

    @Test
    fun `empty specialisations and education field lists round-trip as empty, not null`() {
        val dto = sampleDto().copy(specialisations = emptyList(), educationField = emptyList())
        val entity = dto.toEntity(1_000L)

        assertEquals(emptyList<String>(), entity.specialisations)
        assertEquals(emptyList<String>(), entity.educationField)
    }
}
