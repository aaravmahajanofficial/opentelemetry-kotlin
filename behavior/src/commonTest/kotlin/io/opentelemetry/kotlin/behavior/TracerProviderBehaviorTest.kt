package io.opentelemetry.kotlin.behavior

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class TracerProviderBehaviorTest {

    @Test
    fun spanLimitsStartUnset() {
        assertNull(TracerProviderBehavior().spanLimits)
    }

    @Test
    fun staysUnsetWhenNeitherLayerConfiguredSpanLimits() {
        assertNull(TracerProviderBehavior().mergeWith(TracerProviderBehavior()).spanLimits)
    }

    @Test
    fun adoptsSpanLimitsFromWhicheverLayerSuppliedThem() {
        val limits = SpanLimitsBehavior(linkCountLimit = 3)

        assertEquals(
            limits,
            TracerProviderBehavior().mergeWith(TracerProviderBehavior(spanLimits = limits)).spanLimits,
        )
        assertEquals(
            limits,
            TracerProviderBehavior(spanLimits = limits).mergeWith(TracerProviderBehavior()).spanLimits,
        )
    }

    @Test
    fun mergesSpanLimitsWhenBothLayersSuppliedThem() {
        val merged = TracerProviderBehavior(
            spanLimits = SpanLimitsBehavior(attributeCountLimit = 1, linkCountLimit = 3),
        ).mergeWith(
            TracerProviderBehavior(spanLimits = SpanLimitsBehavior(linkCountLimit = 99)),
        )

        assertEquals(1, merged.spanLimits?.attributeCountLimit)
        assertEquals(99, merged.spanLimits?.linkCountLimit)
    }

    @Test
    fun samplerStartsUnset() {
        assertNull(TracerProviderBehavior().sampler)
    }

    @Test
    fun staysUnsetWhenNeitherLayerConfiguredSampler() {
        assertNull(TracerProviderBehavior().mergeWith(TracerProviderBehavior()).sampler)
    }

    @Test
    fun adoptsSamplerFromWhicheverLayerSuppliedIt() {
        val sampler = SamplerBehavior.AlwaysOff
        assertEquals(
            sampler,
            TracerProviderBehavior().mergeWith(TracerProviderBehavior(sampler = sampler)).sampler,
        )
        assertEquals(
            sampler,
            TracerProviderBehavior(sampler = sampler).mergeWith(TracerProviderBehavior()).sampler,
        )
    }

    @Test
    fun higherSamplerReplacesLowerSampler() {
        val merged = TracerProviderBehavior(sampler = SamplerBehavior.AlwaysOff).mergeWith(
            TracerProviderBehavior(sampler = SamplerBehavior.TraceIdRatioBased(0.5))
        )

        assertEquals(SamplerBehavior.TraceIdRatioBased(0.5), merged.sampler)
    }

    @Test
    fun samplerMergeDoesNotDropSpanLimits() {
        val spanLimits = SpanLimitsBehavior(linkCountLimit = 3)
        val merged = TracerProviderBehavior(spanLimits = spanLimits).mergeWith(
            TracerProviderBehavior(sampler = SamplerBehavior.AlwaysOff)
        )

        assertEquals(spanLimits, merged.spanLimits)
        assertEquals(SamplerBehavior.AlwaysOff, merged.sampler)
    }
}
