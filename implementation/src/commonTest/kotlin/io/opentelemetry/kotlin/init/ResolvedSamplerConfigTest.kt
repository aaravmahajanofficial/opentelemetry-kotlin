package io.opentelemetry.kotlin.init

import io.opentelemetry.kotlin.attributes.AttributesModel
import io.opentelemetry.kotlin.behavior.OpenTelemetryBehavior
import io.opentelemetry.kotlin.clock.FakeClock
import io.opentelemetry.kotlin.factory.ContextFactoryImpl
import io.opentelemetry.kotlin.factory.IdGeneratorImpl
import io.opentelemetry.kotlin.factory.SpanContextFactoryImpl
import io.opentelemetry.kotlin.factory.SpanFactoryImpl
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.sampling.FakeSampler
import io.opentelemetry.kotlin.tracing.sampling.ParentBasedSampler
import io.opentelemetry.kotlin.tracing.sampling.Sampler
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult.Decision
import io.opentelemetry.kotlin.tracing.sampling.alwaysOn
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalStdlibApi::class)
internal class ResolvedSamplerConfigTest {

    private val clock = FakeClock()
    private val idGenerator = IdGeneratorImpl()
    private val spanContextFactory = SpanContextFactoryImpl(idGenerator)
    private val spanFactory = SpanFactoryImpl(spanContextFactory)
    private val contextFactory = ContextFactoryImpl(spanFactory)

    private fun tracingConfig(
        getEnvVar: (String) -> String? = { null },
        declarativeFile: OpenTelemetryBehavior? = null,
        configure: TracerProviderConfigDsl.() -> Unit,
    ) = OpenTelemetryConfigImpl(clock).apply {
        this.getEnvVar = getEnvVar
        this.declarativeFileBehavior = declarativeFile
        tracerProvider(configure)
    }.generateTracingConfig()

    private fun samplerOf(
        getEnvVar: (String) -> String? = { null },
        declarativeFile: OpenTelemetryBehavior? = null,
        configure: TracerProviderConfigDsl.() -> Unit = {},
    ) = tracingConfig(getEnvVar, declarativeFile, configure).samplerFactory(spanFactory)

    private fun env(sampler: String, arg: String? = null): (String) -> String? {
        val values = buildMap {
            put("OTEL_TRACES_SAMPLER", sampler)
            if (arg != null) put("OTEL_TRACES_SAMPLER_ARG", arg)
        }
        return values::get
    }

    private fun Sampler.shouldSampleRoot(): Decision = shouldSample(
        context = contextFactory.root(),
        traceIdBytes = ZERO_TRACE_ID,
        name = "root",
        spanKind = SpanKind.INTERNAL,
        attributes = AttributesModel(),
        links = emptyList()
    ).decision

    /**
     * When nothing is configured, the SDK must keep its standard default
     * (ParentBased with AlwaysOn root).
     */
    @Test
    fun unsetLayersLeaveTheSdkDefaultSampler() {
        val sampler = assertIs<ParentBasedSampler>(samplerOf())
        assertContains(sampler.description, "root:AlwaysOnSampler")
    }

    /**
     * Environment variable OTEL_TRACES_SAMPLER=always_off overrides
     * the SDK default when DSL is omitted.
     */
    fun envAlwaysOffIsAppliedWhenDslOmitsSampler() {
        val sampler = samplerOf(getEnvVar = env("always_off"))
        assertEquals("AlwaysOffSampler", sampler.description)
        assertEquals(Decision.DROP, sampler.shouldSampleRoot())
    }

    /**
     * OTEL_TRACES_SAMPLER=traceidratio with ARG=0 materializes
     * TraceIdRatioBasedSampler(0.0) and drops root spans.
     */
    @Test
    fun envTraceIdRatioZeroDrops() {
        val sampler = samplerOf(getEnvVar = env("traceidratio", "0"))
        assertEquals("TraceIdRatioBasedSampler{0.0}", sampler.description)
        assertEquals(Decision.DROP, sampler.shouldSampleRoot())
    }

    /**
     * Programmatic DSL sampler { alwaysOn() } MUST beat
     * OTEL_TRACES_SAMPLER=always_off (DSL > Env).
     */
    @Test
    fun dslSamplerWinsOverEnv() {
        val sampler = samplerOf(getEnvVar = env("always_off")) {
            sampler { alwaysOn() }
        }
        assertEquals("AlwaysOnSampler", sampler.description)
        assertEquals(Decision.RECORD_AND_SAMPLE, sampler.shouldSampleRoot())
    }

    /**
     * A custom instance provided in the DSL must
     * be preserved and never replaced by environment variables.
     */
    @Test
    fun dslCustomSamplerIsNotReplacedByEnv() {
        val custom = FakeSampler(Decision.RECORD_ONLY)
        val sampler = samplerOf(getEnvVar = env("always_on")) {
            sampler { custom }
        }
        assertSame(custom, sampler)
    }





    private companion object {
        val ZERO_TRACE_ID = "00000000000000000000000000000000".hexToByteArray()
    }

}