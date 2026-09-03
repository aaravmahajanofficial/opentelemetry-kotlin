package io.opentelemetry.kotlin.config.yaml

import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.config.schema.model.AlwaysOffSampler
import io.opentelemetry.kotlin.config.schema.model.AlwaysOnSampler
import io.opentelemetry.kotlin.config.schema.model.ParentBasedSampler
import io.opentelemetry.kotlin.config.schema.model.Sampler
import io.opentelemetry.kotlin.config.schema.model.TraceIdRatioBasedSampler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SamplerMapperTest {
    /**
     * Verifies that declaring `always_on: {}` in YAML maps cleanly to [SamplerBehavior.AlwaysOn].
     */
    @Test
    fun mapsAlwaysOn() {
        assertEquals(
            SamplerBehavior.AlwaysOn,
            Sampler(alwaysOn = AlwaysOnSampler()).toBehavior(),
        )
    }

    /**
     * Verifies that declaring `always_off: {}` in YAML maps cleanly to [SamplerBehavior.AlwaysOff].
     */
    @Test
    fun mapsAlwaysOff() {
        assertEquals(
            SamplerBehavior.AlwaysOff,
            Sampler(alwaysOff = AlwaysOffSampler()).toBehavior()
        )
    }

    /**
     * Error-handling test: Verifies that invalid ratios (< 0.0, > 1.0, NaN, Infinity) safely reject
     * the entire sampler as unset (`null`) instead of crashing or sampling at an erroneous rate.
     */
    @Test
    fun leavesDisallowedRatioUnset() {
        listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertNull(
                Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(ratio = value)).toBehavior(),
                "<$value> should not configure a sampler"
            )
        }
    }

    /**
     * Field-wiring integrity test: Verifies that all 5 distinct delegates of `ParentBased` are correctly
     * mapped to their corresponding IR fields without copy-paste or swapped-parameter bugs.
     */
    @Test
    fun mapsEveryParentBasedChild() {
        val schema = Sampler(
            parentBased = ParentBasedSampler(
                root = Sampler(alwaysOff = AlwaysOffSampler()),
                remoteParentSampled = Sampler(alwaysOn = AlwaysOnSampler()),
                remoteParentNotSampled = Sampler(alwaysOff = AlwaysOffSampler()),
                localParentSampled = Sampler(alwaysOn = AlwaysOnSampler()),
                localParentNotSampled = Sampler(alwaysOff = AlwaysOffSampler())
            )
        )

        assertEquals(
            SamplerBehavior.ParentBased(
                root = SamplerBehavior.AlwaysOff,
                remoteParentSampled = SamplerBehavior.AlwaysOn,
                remoteParentNotSampled = SamplerBehavior.AlwaysOff,
                localParentSampled = SamplerBehavior.AlwaysOn,
                localParentNotSampled = SamplerBehavior.AlwaysOff
            ),
            schema.toBehavior()
        )
    }

    /**
     * Verifies that declaring `parent_based: {}` is recognized as explicitly choosing the ParentBased
     * strategy, while leaving all 5 sub-delegates unset (`null`) so SDK defaults can apply at runtime.
     */
    @Test
    fun emptyParentBasedLeavesChildrenUnset() {
        assertEquals(
            SamplerBehavior.ParentBased(),
            Sampler(parentBased = ParentBasedSampler()).toBehavior()
        )
    }

    /**
     * Verifies that an empty `sampler: {}` block (no sampler specified) cleanly evaluates to unset (`null`).
     */
    @Test
    fun leavesOmittedSamplerUnset() {
        assertNull(Sampler().toBehavior())
    }

    /**
     * One-of validation test: Verifies that if YAML contains 2 or more conflicting sampler keys,
     * the mapper safely degrades to unset (`null`) rather than crashing the application.
     */
    @Test
    fun leavesMultipleSamplersUnset() {
        assertNull(
            Sampler(
                alwaysOff = AlwaysOffSampler(),
                alwaysOn = AlwaysOnSampler()
            ).toBehavior()
        )
    }
}
