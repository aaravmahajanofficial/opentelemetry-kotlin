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
    @Test
    fun mapsAlwaysOn() {
        assertEquals(
            SamplerBehavior.AlwaysOn,
            Sampler(alwaysOn = AlwaysOnSampler()).toBehavior(),
        )
    }

    @Test
    fun mapsAlwaysOff() {
        assertEquals(
            SamplerBehavior.AlwaysOff,
            Sampler(alwaysOff = AlwaysOffSampler()).toBehavior()
        )
    }

    @Test
    fun mapsTraceIdRatioBased() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(0.5),
            Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(0.5)).toBehavior()
        )
    }

    @Test
    fun leavesOmittedRatioUnset() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = null),
            Sampler(traceIdRatioBased = TraceIdRatioBasedSampler()).toBehavior()
        )
    }

    @Test
    fun preservesARatioOfZero() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = 0.0),
            Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(ratio = 0.0)).toBehavior()
        )
    }

    @Test
    fun preservesARatioOfOne() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = 1.0),
            Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(ratio = 1.0)).toBehavior()
        )
    }

    @Test
    fun leavesDisallowedRatioUnset() {
        listOf(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertNull(
                Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(ratio = value)).toBehavior(),
                "<$value> should not configure a sampler"
            )
        }
    }

    @Test
    fun mapsParentBasedWithNestedRoot() {
        val schema = Sampler(
            parentBased = ParentBasedSampler(
                root = Sampler(traceIdRatioBased = TraceIdRatioBasedSampler(0.01))
            )
        )

        assertEquals(
            SamplerBehavior.ParentBased(root = SamplerBehavior.TraceIdRatioBased(0.01)),
            schema.toBehavior()
        )
    }

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

    @Test
    fun emptyParentBasedLeavesChildrenUnset() {
        assertEquals(
            SamplerBehavior.ParentBased(),
            Sampler(parentBased = ParentBasedSampler()).toBehavior()
        )
    }

    @Test
    fun leavesOmittedSamplerUnset() {
        assertNull(Sampler().toBehavior())
    }

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