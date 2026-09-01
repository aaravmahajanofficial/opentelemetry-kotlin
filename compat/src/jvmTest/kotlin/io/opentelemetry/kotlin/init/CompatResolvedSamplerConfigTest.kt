package io.opentelemetry.kotlin.init

import io.opentelemetry.kotlin.behavior.OpenTelemetryBehavior
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.behavior.TracerProviderBehavior
import io.opentelemetry.kotlin.clock.FakeClock
import io.opentelemetry.kotlin.factory.CompatIdGenerator
import io.opentelemetry.kotlin.tracing.sampling.FakeSampler
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult
import io.opentelemetry.kotlin.tracing.sampling.alwaysOn
import junit.framework.TestCase.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue

internal class CompatResolvedSamplerConfigTest {

    private val clock = FakeClock()
    private val idGenerator = CompatIdGenerator()

    private fun startSpan(
        getEnvVar: (String) -> String? = { null },
        declarativeFile: OpenTelemetryBehavior? = null,
        configure: TracerProviderConfigDsl.() -> Unit = {},
    ) = CompatOpenTelemetryConfig(clock).apply {
        this.getEnvVar = getEnvVar
        this.declarativeFileBehavior = declarativeFile
        tracerProvider(configure)
        applyResolvedSampler()
    }.tracerProviderConfig.build(clock, idGenerator)
        .getTracer("test")
        .startSpan("span")

    private fun env(sampler: String, arg: String? = null): (String) -> String? {
        val values = buildMap {
            put("OTEL_TRACES_SAMPLER", sampler)
            if (arg != null) put("OTEL_TRACES_SAMPLER_ARG", arg)
        }
        return values::get
    }

    private fun fileSampler(sampler: SamplerBehavior) = OpenTelemetryBehavior(
        tracerProvider = TracerProviderBehavior(sampler = sampler),
    )

    /** Unset env/file: Java default ParentBased(AlwaysOn) samples roots. */
    @Test
    fun unsetLayersLeaveTheSdkDefaultSampler() {
        val span = startSpan()
        assertTrue(span.isRecording())
        assertTrue(span.spanContext.traceFlags.isSampled)
    }

    /** OTEL_TRACES_SAMPLER=always_off is applied when sampler { } is omitted. */
    @Test
    fun envAlwaysOffIsAppliedWhenDslOmitsSampler() {
        val span = startSpan(getEnvVar = env("always_off"))
        assertFalse(span.isRecording())
        assertFalse(span.spanContext.traceFlags.isSampled)
    }

    /** traceidratio + ARG=0 drops roots (Java TraceIdRatioBased). */
    @Test
    fun envTraceIdRatioZeroDrops() {
        val span = startSpan(getEnvVar = env("traceidratio", "0"))
        assertFalse(span.isRecording())
        assertFalse(span.spanContext.traceFlags.isSampled)
    }

    /** ALWAYS_ON is accepted (env names are case-insensitive). */
    @Test
    fun envSamplerNameIsCaseInsensitive() {
        val span = startSpan(getEnvVar = env("ALWAYS_OFF"))
        assertFalse(span.isRecording())
    }

    /**
     * Invalid ARG is ignored; sampler stays. Spec default ratio is 1.0, so roots sample.
     */
    @Test
    fun invalidSamplerArgKeepsSamplerWithDefaultRatio() {
        val span = startSpan(getEnvVar = env("traceidratio", "not-a-ratio"))
        assertTrue(span.isRecording())
        assertTrue(span.spanContext.traceFlags.isSampled)
    }

    /** sampler { alwaysOn() } outranks OTEL_TRACES_SAMPLER=always_off. */
    @Test
    fun dslSamplerWinsOverEnv() {
        val span = startSpan(getEnvVar = env("always_off")) {
            sampler { alwaysOn() }
        }
        assertTrue(span.isRecording())
        assertTrue(span.spanContext.traceFlags.isSampled)
    }

    /**
     * Custom DSL sampler is not replaced by env.
     * RECORD_ONLY → recording but not sampled; always_on would also set sampled.
     */
    @Test
    fun dslCustomSamplerIsNotReplacedByEnv() {
        val span = startSpan(getEnvVar = env("always_on")) {
            sampler { FakeSampler(SamplingResult.Decision.RECORD_ONLY) }
        }
        assertTrue(span.isRecording())
        assertFalse(span.spanContext.traceFlags.isSampled)
    }

    /** Injected file IR is applied when DSL omits sampler. */
    @Test
    fun declarativeFileSamplerIsAppliedWhenDslOmitsSampler() {
        val span = startSpan(declarativeFile = fileSampler(SamplerBehavior.AlwaysOff))
        assertFalse(span.isRecording())
    }

    /**
     * A non-null file layer, even empty, replaces env (BehaviorResolver: file ?: env).
     * Empty file has no sampler → Java default samples.
     */
    @Test
    fun emptyDeclarativeFileReplacesEnvSampler() {
        val span = startSpan(
            getEnvVar = env("always_off"),
            declarativeFile = OpenTelemetryBehavior(),
        )
        assertTrue(span.isRecording())
        assertTrue(span.spanContext.traceFlags.isSampled)
    }

    /** DSL outranks file and env. */
    @Test
    fun dslSamplerWinsOverDeclarativeFile() {
        val span = startSpan(
            getEnvVar = env("always_off"),
            declarativeFile = fileSampler(SamplerBehavior.AlwaysOff),
        ) {
            sampler { alwaysOn() }
        }
        assertTrue(span.isRecording())
        assertTrue(span.spanContext.traceFlags.isSampled)
    }
}