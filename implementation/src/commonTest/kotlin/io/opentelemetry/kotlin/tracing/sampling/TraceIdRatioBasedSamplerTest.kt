package io.opentelemetry.kotlin.tracing.sampling

import io.opentelemetry.kotlin.InstrumentationScopeInfoImpl
import io.opentelemetry.kotlin.attributes.AttributesModel
import io.opentelemetry.kotlin.clock.FakeClock
import io.opentelemetry.kotlin.error.NoopSdkErrorHandler
import io.opentelemetry.kotlin.export.MutableShutdownState
import io.opentelemetry.kotlin.factory.ContextFactoryImpl
import io.opentelemetry.kotlin.factory.IdGeneratorImpl
import io.opentelemetry.kotlin.factory.SpanContextFactoryImpl
import io.opentelemetry.kotlin.factory.SpanFactoryImpl
import io.opentelemetry.kotlin.factory.TraceFlagsFactoryImpl
import io.opentelemetry.kotlin.factory.TraceStateFactoryImpl
import io.opentelemetry.kotlin.init.SamplerConfigDsl
import io.opentelemetry.kotlin.resource.FakeResource
import io.opentelemetry.kotlin.tracing.FakeTraceState
import io.opentelemetry.kotlin.tracing.NonRecordingSpan
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.TraceState
import io.opentelemetry.kotlin.tracing.TracerImpl
import io.opentelemetry.kotlin.tracing.export.FakeSpanProcessor
import io.opentelemetry.kotlin.tracing.fakeSpanLimitsConfig
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult.Decision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalStdlibApi::class)
internal class TraceIdRatioBasedSamplerTest {

    private val clock = FakeClock()
    private val idGenerator = IdGeneratorImpl()
    private val traceFlagsFactory = TraceFlagsFactoryImpl()
    private val traceStateFactory = TraceStateFactoryImpl()
    private val spanContextFactory =
        SpanContextFactoryImpl(idGenerator, traceFlagsFactory, traceStateFactory)
    private val spanFactory = SpanFactoryImpl(spanContextFactory = spanContextFactory)
    private val contextFactory = ContextFactoryImpl(spanFactory)
    private val scope = InstrumentationScopeInfoImpl("test", null, null, emptyMap())

    private val samplerDsl = object : SamplerConfigDsl {
        override val spanFactory = this@TraceIdRatioBasedSamplerTest.spanFactory
    }

    private fun sample(
        sampler: Sampler,
        traceIdBytes: ByteArray,
        parentTraceState: TraceState = traceStateFactory.default,
    ): SamplingResult {
        val parentContext = spanContextFactory.create(
            traceId = "12345678901234567890123456789012",
            spanId = "1234567890123456",
            traceFlags = traceFlagsFactory.default,
            traceState = parentTraceState,
            isRemote = false,
        )

        val parentSpan = NonRecordingSpan(spanContextFactory.invalid, parentContext)

        val context = contextFactory.root().storeSpan(parentSpan)

        return sampler.shouldSample(
            context = context,
            traceIdBytes = traceIdBytes,
            name = "span",
            spanKind = SpanKind.INTERNAL,
            attributes = AttributesModel(),
            links = emptyList()
        )
    }

    private fun buildTracer(sampler: Sampler) = TracerImpl(
        clock = clock,
        processor = FakeSpanProcessor(),
        contextFactory = contextFactory,
        spanContextFactory = spanContextFactory,
        traceFlagsFactory = traceFlagsFactory,
        scope = scope,
        resource = FakeResource(),
        spanLimitConfig = fakeSpanLimitsConfig,
        idGenerator = idGenerator,
        shutdownState = MutableShutdownState(),
        sampler = sampler,
        sdkErrorHandler = NoopSdkErrorHandler,
    )

    /**
     * Verifies the human-readable description format required by the OpenTelemetry spec:
     * "TraceIdRatioBasedSampler{RATIO}".
     */
    @Test
    fun descriptionIncludesRatio() {
        assertEquals(
            "TraceIdRatioBasedSampler{0.5}",
            samplerDsl.traceIdRatioBased(0.5).description,
        )
    }

    /**
     * Boundary Check: Ratio 0.0 must ALWAYS drop spans (idUpperBound = Long.MIN_VALUE).
     */
    @Test
    fun ratioZeroAlwaysDrops() {
        val result = sample(samplerDsl.traceIdRatioBased(0.0), ZERO_TRACE_ID)
        assertEquals(Decision.DROP, result.decision)
    }

    /**
     * Boundary Check: Ratio 1.0 must ALWAYS record and sample spans (idUpperBound = Long.MAX_VALUE).
     */
    @Test
    fun ratioOneAlwaysSamples() {
        val result = sample(samplerDsl.traceIdRatioBased(1.0), ZERO_TRACE_ID)
        assertEquals(Decision.RECORD_AND_SAMPLE, result.decision)
    }

    /**
     * Math Check: A trace ID whose lower 64 bits are all zero (0x0000000000000000)
     * falls within the sampled range for any positive ratio (e.g. 50%) and will be sampled.
     */
    @Test
    fun zeroRandomPartIsSampledWhenRatioIsPositive() {
        val result = sample(samplerDsl.traceIdRatioBased(0.5), ZERO_TRACE_ID)
        assertEquals(Decision.RECORD_AND_SAMPLE, result.decision)
    }

    /**
     * Math Check: A trace ID whose lower 64 bits represent a very large positive number
     * (e.g. 0x7FFFFFFFFFFFFFFF) will exceed the 50% cutoff bound and be dropped.
     */
    @Test
    fun highRandomPartIsDroppedWhenExceedingRatioBound() {
        val highTraceId = "00000000000000007fffffffffffffff".hexToByteArray()
        val result = sample(samplerDsl.traceIdRatioBased(0.5), highTraceId)
        assertEquals(Decision.DROP, result.decision)
    }

    /**
     * Fail-Fast Validation: Programmatic SDK initialization must immediately reject
     * out-of-range ratios (< 0.0, > 1.0, NaN) with IllegalArgumentException.
     */
    @Test
    fun rejectsRatioOutsideUnitInterval() {
        listOf(-0.1, 1.1, Double.NaN).forEach { ratio ->
            assertFailsWith<IllegalArgumentException> {
                samplerDsl.traceIdRatioBased(ratio)
            }
        }
    }

    /**
     * Error Handling: Malformed trace IDs (e.g. byte array size != 16)
     * must be safely dropped without throwing an exception or crashing the host app.
     */
    @Test
    fun malformedTraceIdDrops() {
        val malformedId = byteArrayOf(1, 2, 3)
        val result = sample(samplerDsl.traceIdRatioBased(1.0), malformedId)
        assertEquals(Decision.DROP, result.decision)
    }

    /**
     * Spec Check: Unlike ProbabilitySampler (which modifies `ot` tracestate),
     * classic TraceIdRatioBasedSampler must preserve incoming parent TraceState untouched.
     */
    @Test
    fun preservesParentTraceState() {
        val customTraceState = FakeTraceState(mapOf("vendor" to "custom-value"))
        val result = sample(
            sampler = samplerDsl.traceIdRatioBased(1.0),
            traceIdBytes = ZERO_TRACE_ID,
            parentTraceState = customTraceState,
        )
        assertEquals("custom-value", result.traceState.get("vendor"))
    }

    /**
     * Integration Check: Verifies that TraceIdRatioBasedSampler works end-to-end as the root
     * delegate inside a ParentBasedSampler with a real Tracer.
     */
    @Test
    fun worksAsParentBasedRoot() {
        val tracer = buildTracer(samplerDsl.parentBased(root = samplerDsl.traceIdRatioBased(0.0)))
        val span = tracer.startSpan("root")
        assertFalse(span.isRecording())
    }

    private companion object {
        val ZERO_TRACE_ID = "00000000000000000000000000000000".hexToByteArray()
    }
}
