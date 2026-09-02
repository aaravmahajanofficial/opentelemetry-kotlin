package io.opentelemetry.kotlin.behavior

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SamplerBehaviorTest {

    @Test
    fun parentBasedChildrenStartUnset() {
        val behavior = SamplerBehavior.ParentBased()

        assertNull(behavior.root)
        assertNull(behavior.remoteParentSampled)
        assertNull(behavior.remoteParentNotSampled)
        assertNull(behavior.localParentSampled)
        assertNull(behavior.localParentNotSampled)
    }

    @Test
    fun ratioStartsUnsetWhenOmitted() {
        assertNull(SamplerBehavior.TraceIdRatioBased().ratio)
    }

    @Test
    fun treatsZeroRatioAsConfigured() {
        assertEquals(0.0, SamplerBehavior.TraceIdRatioBased(0.0).ratio)
    }

    @Test
    fun higherLayerReplacesLowerSampler() {
        val lower: SamplerBehavior = SamplerBehavior.AlwaysOn
        val higher: SamplerBehavior = SamplerBehavior.TraceIdRatioBased(ratio = 0.1)

        assertEquals(higher, lower.mergeWith(higher))
    }

    @Test
    fun parentBasedIsReplacedAsAWholeNode() {
        val lower = SamplerBehavior.ParentBased(
            root = SamplerBehavior.AlwaysOn,
            remoteParentNotSampled = SamplerBehavior.AlwaysOn
        )
        val higher = SamplerBehavior.ParentBased(
            root = SamplerBehavior.AlwaysOff,
            remoteParentSampled = SamplerBehavior.AlwaysOn
        )

        val merged = lower.mergeWith(higher)

        assertEquals(higher, merged)
        assertEquals(SamplerBehavior.AlwaysOff, (merged as SamplerBehavior.ParentBased).root)
        assertEquals(SamplerBehavior.AlwaysOn, merged.remoteParentSampled)

        // lower's omitted children stay omitted; they are not filled from `lower`
        assertNull(merged.remoteParentNotSampled)
    }

    @Test
    fun doesNotMutateEitherLayer() {
        val lower = SamplerBehavior.AlwaysOn
        val higher = SamplerBehavior.AlwaysOff

        lower.mergeWith(higher)

        assertEquals(SamplerBehavior.AlwaysOn, lower)
        assertEquals(SamplerBehavior.AlwaysOff, higher)
    }
}
