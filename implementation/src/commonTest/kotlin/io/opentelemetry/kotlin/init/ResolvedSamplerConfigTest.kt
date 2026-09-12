package io.opentelemetry.kotlin.init

import io.opentelemetry.kotlin.attributes.AttributesModel
import io.opentelemetry.kotlin.behavior.OpenTelemetryBehavior
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.behavior.TracerProviderBehavior
import io.opentelemetry.kotlin.clock.FakeClock
import io.opentelemetry.kotlin.error.FakeSdkErrorHandler
import io.opentelemetry.kotlin.error.SdkErrorHandler
import io.opentelemetry.kotlin.error.SdkErrorSeverity
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
        errorHandler: SdkErrorHandler? = null,
        configure: TracerProviderConfigDsl.() -> Unit,
    ) = OpenTelemetryConfigImpl(clock).apply {
        this.getEnvVar = getEnvVar
        this.declarativeFileBehavior = declarativeFile
        if (errorHandler != null) {
            errorHandler(errorHandler)
        }
        tracerProvider(configure)
    }.generateTracingConfig()

    private fun samplerOf(
        getEnvVar: (String) -> String? = { null },
        declarativeFile: OpenTelemetryBehavior? = null,
        errorHandler: SdkErrorHandler? = null,
        configure: TracerProviderConfigDsl.() -> Unit = {},
    ) = tracingConfig(getEnvVar, declarativeFile, errorHandler, configure).samplerFactory(
        spanFactory
    )

    private fun env(sampler: String): (String) -> String? {
        val values = buildMap {
            put("OTEL_TRACES_SAMPLER", sampler)
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

    private fun fileSampler(sampler: SamplerBehavior) = OpenTelemetryBehavior(
        tracerProvider = TracerProviderBehavior(sampler = sampler)
    )

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
    @Test
    fun envAlwaysOffIsAppliedWhenDslOmitsSampler() {
        val sampler = samplerOf(getEnvVar = env("always_off"))
        assertEquals("AlwaysOffSampler", sampler.description)
        assertEquals(Decision.DROP, sampler.shouldSampleRoot())
    }

    /**
     * Environment variable OTEL_TRACES_SAMPLER=parentbased_always_on applies a
     * ParentBasedSampler with AlwaysOn root sampler when DSL omits sampler.
     */
    @Test
    fun envParentBasedAlwaysOnIsAppliedWhenDslOmitsSampler() {
        val sampler =
            assertIs<ParentBasedSampler>(samplerOf(getEnvVar = env("parentbased_always_on")))
        assertContains(sampler.description, "root:AlwaysOnSampler")
        assertEquals(Decision.RECORD_AND_SAMPLE, sampler.shouldSampleRoot())
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

    /**
     * A declarative file layer with AlwaysOff overrides the SDK default.
     */
    @Test
    fun declarativeFileSamplerIsAppliedWhenDslOmitsSampler() {
        val sampler = samplerOf(declarativeFile = fileSampler(SamplerBehavior.AlwaysOff))
        assertEquals("AlwaysOffSampler", sampler.description)
        assertEquals(Decision.DROP, sampler.shouldSampleRoot())
    }

    /**
     * Per the OpenTelemetry spec, the presence of a declarative file
     * (even empty) drops environment variables wholesale (File > Env).
     */
    @Test
    fun emptyDeclarativeFileReplacesEnvSampler() {
        val sampler = samplerOf(
            getEnvVar = env("always_off"),
            declarativeFile = OpenTelemetryBehavior()
        )
        val parentBased = assertIs<ParentBasedSampler>(sampler)
        assertContains(parentBased.description, "root:AlwaysOnSampler")
        assertEquals(Decision.RECORD_AND_SAMPLE, sampler.shouldSampleRoot())
    }

    /**
     * Non-empty declarative file with AlwaysOff beats OTEL_TRACES_SAMPLER=always_on
     * when DSL omits sampler (File > Env).
     */
    @Test
    fun declarativeFileBeatsEnvWhenDslOmitsSampler() {
        val sampler = samplerOf(
            getEnvVar = env("always_on"),
            declarativeFile = fileSampler(SamplerBehavior.AlwaysOff),
        )
        assertEquals("AlwaysOffSampler", sampler.description)
        assertEquals(Decision.DROP, sampler.shouldSampleRoot())
    }

    /**
     * Unknown OTEL_TRACES_SAMPLER is reported via SdkErrorHandler and leaves the
     * SDK default (ParentBased with AlwaysOn root).
     */
    @Test
    fun invalidEnvSamplerReportsWarningAndKeepsDefault() {
        val handler = FakeSdkErrorHandler()
        val sampler = samplerOf(
            getEnvVar = env("not_a_sampler"),
            errorHandler = handler,
        )

        val parentBased = assertIs<ParentBasedSampler>(sampler)
        assertContains(parentBased.description, "root:AlwaysOnSampler")
        assertEquals(Decision.RECORD_AND_SAMPLE, sampler.shouldSampleRoot())
        assertEquals(1, handler.apiMisuses.size)

        val misuse = handler.apiMisuses.single()
        assertEquals("OTEL_TRACES_SAMPLER", misuse.api)
        assertContains(misuse.message, "not_a_sampler")
        assertEquals(SdkErrorSeverity.WARNING, misuse.severity)
    }

    /**
     * Programmatic DSL sampler { alwaysOn() } beats BOTH
     * a declarative file and environment variables (DSL > File > Env).
     */
    @Test
    fun dslSamplerWinsOverDeclarativeFile() {
        val sampler = samplerOf(
            getEnvVar = env("always_off"),
            declarativeFile = fileSampler(SamplerBehavior.AlwaysOff)
        ) {
            sampler { alwaysOn() }
        }
        assertEquals("AlwaysOnSampler", sampler.description)
    }

    private companion object {
        val ZERO_TRACE_ID = "00000000000000000000000000000000".hexToByteArray()
    }
}
