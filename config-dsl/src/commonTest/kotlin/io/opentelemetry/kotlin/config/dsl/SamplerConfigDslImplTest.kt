package io.opentelemetry.kotlin.config.dsl

import io.opentelemetry.kotlin.behavior.SamplerBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SamplerConfigDslImplTest {

    @Test
    fun startsUnset() {
        assertNull(SamplerConfigDslImpl().toBehavior())
    }

    @Test
    fun mapsAlwaysOn() {
        val dsl = SamplerConfigDslImpl()
        dsl.alwaysOn()
        assertEquals(SamplerBehavior.AlwaysOn, SamplerConfigDslImpl().alwaysOn())
    }

    @Test
    fun mapsAlwaysOff() {
        val dsl = SamplerConfigDslImpl()
        dsl.alwaysOff()
        assertEquals(SamplerBehavior.AlwaysOff, dsl.toBehavior())
    }

    @Test
    fun mapsTraceIdRatioBased() {
        val dsl = SamplerConfigDslImpl()
        dsl.traceIdRatioBased(0.5)
        assertEquals(SamplerBehavior.TraceIdRatioBased(0.5), dsl.toBehavior())
    }

    @Test
    fun leavesOmittedRatioUnset() {
        val dsl = SamplerConfigDslImpl()
        dsl.traceIdRatioBased()
        assertEquals(SamplerBehavior.TraceIdRatioBased(), dsl.toBehavior())
    }

    @Test
    fun preservesARatioOfZero() {
        val dsl = SamplerConfigDslImpl()
        dsl.traceIdRatioBased(0.0)
        assertEquals(SamplerBehavior.TraceIdRatioBased(0.0), dsl.toBehavior())
    }

    @Test
    fun ignoresDisallowedRatio() {
        val dsl = SamplerConfigDslImpl()
        listOf(-0.1, 1.1, Double.NaN).forEach { value ->
            assertNull(dsl.traceIdRatioBased(value))
        }
        assertNull(dsl.toBehavior())
    }

    @Test
    fun disallowedRatioDoesNotReplaceAPreviousChoice() {
        val dsl = SamplerConfigDslImpl()
        dsl.alwaysOn()
        dsl.traceIdRatioBased(-1.0)
        assertEquals(SamplerBehavior.AlwaysOn, dsl.toBehavior())
    }

    @Test
    fun lastSuccessfulCallWins() {
        val dsl = SamplerConfigDslImpl()
        dsl.alwaysOn()
        dsl.alwaysOff()
        assertEquals(SamplerBehavior.AlwaysOff, dsl.toBehavior())
    }

    @Test
    fun mapsParentBasedWithNestedRoot() {
        val dsl = SamplerConfigDslImpl()
        dsl.parentBased(root = SamplerBehavior.TraceIdRatioBased(0.01))
        assertEquals(
            SamplerBehavior.ParentBased(root = SamplerBehavior.TraceIdRatioBased(0.01)),
            dsl.toBehavior()
        )
    }

    @Test
    fun mapsEveryParentBasedChild() {
        val dsl = SamplerConfigDslImpl()
        dsl.parentBased(
            root = dsl.alwaysOff(),
            remoteParentSampled = dsl.alwaysOn(),
            remoteParentNotSampled = dsl.alwaysOff(),
            localParentSampled = dsl.alwaysOn(),
            localParentNotSampled = dsl.alwaysOff(),
        )
        assertEquals(
            SamplerBehavior.ParentBased(
                root = SamplerBehavior.AlwaysOff,
                remoteParentSampled = SamplerBehavior.AlwaysOn,
                remoteParentNotSampled = SamplerBehavior.AlwaysOff,
                localParentSampled = SamplerBehavior.AlwaysOn,
                localParentNotSampled = SamplerBehavior.AlwaysOff,
            ),
            dsl.toBehavior()
        )
    }

    @Test
    fun emptyParentBasedLeavesChildrenUnset() {
        val dsl = SamplerConfigDslImpl()
        dsl.parentBased()
        assertEquals(SamplerBehavior.ParentBased(), dsl.toBehavior())
    }
}
