package io.opentelemetry.kotlin.config.envar

import io.opentelemetry.kotlin.behavior.AttributeLimitsBehavior
import io.opentelemetry.kotlin.behavior.LogLimitsBehavior
import io.opentelemetry.kotlin.behavior.LoggerProviderBehavior
import io.opentelemetry.kotlin.behavior.OpenTelemetryBehavior
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.behavior.SpanLimitsBehavior
import io.opentelemetry.kotlin.behavior.TracerProviderBehavior
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class OpenTelemetryEnvVarsTest {

    @Test
    fun `should read every node from its own env vars`() {
        val env = mapOf(
            "OTEL_ATTRIBUTE_COUNT_LIMIT" to "1",
            "OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT" to "2",
            "OTEL_SPAN_ATTRIBUTE_COUNT_LIMIT" to "3",
            "OTEL_SPAN_ATTRIBUTE_VALUE_LENGTH_LIMIT" to "4",
            "OTEL_SPAN_LINK_COUNT_LIMIT" to "5",
            "OTEL_SPAN_EVENT_COUNT_LIMIT" to "6",
            "OTEL_EVENT_ATTRIBUTE_COUNT_LIMIT" to "7",
            "OTEL_LINK_ATTRIBUTE_COUNT_LIMIT" to "8",
            "OTEL_LOGRECORD_ATTRIBUTE_COUNT_LIMIT" to "9",
            "OTEL_LOGRECORD_ATTRIBUTE_VALUE_LENGTH_LIMIT" to "10",
            "OTEL_TRACES_SAMPLER" to "traceidratio",
            "OTEL_TRACES_SAMPLER_ARG" to "0.25",
        )

        val expected = OpenTelemetryBehavior(
            attributeLimits = AttributeLimitsBehavior(
                attributeCountLimit = 1,
                attributeValueLengthLimit = 2,
            ),
            tracerProvider = TracerProviderBehavior(
                spanLimits = SpanLimitsBehavior(
                    attributeCountLimit = 3,
                    attributeValueLengthLimit = 4,
                    linkCountLimit = 5,
                    eventCountLimit = 6,
                    attributeCountPerEventLimit = 7,
                    attributeCountPerLinkLimit = 8,
                ),
            ),
            loggerProvider = LoggerProviderBehavior(
                logLimits = LogLimitsBehavior(
                    attributeCountLimit = 9,
                    attributeValueLengthLimit = 10,
                ),
            ),
        )
        assertEquals(expected, toBehavior(env::get))
    }

    @Test
    fun `should leave every limit unset when the environment configures nothing`() {
        val expected = OpenTelemetryBehavior(
            attributeLimits = AttributeLimitsBehavior(),
            tracerProvider = TracerProviderBehavior(spanLimits = SpanLimitsBehavior()),
            loggerProvider = LoggerProviderBehavior(logLimits = LogLimitsBehavior()),
        )
        assertEquals(expected, toBehavior({ null }))
    }

    @Test
    fun `should map sampler env vars`() {
        val env = mapOf(
            "OTEL_TRACES_SAMPLER" to "always_off",
        )
        val behavior = toBehavior(env::get)
        assertEquals(SamplerBehavior.AlwaysOff, behavior.tracerProvider?.sampler)
    }

    @Test
    fun `should leave sampler unset when OTEL_TRACES_SAMPLER is unset`() {
        assertNull(toBehavior({ null }).tracerProvider?.sampler)
    }

    @Test
    fun `should forward onSamplerWarning for unknown sampler`() {
        val warnings = mutableListOf<String>()
        toBehavior(env("not_a_sampler"), warnings::add)
        assertEquals(1, warnings.size)
        assertContains(warnings.single(), "not_a_sampler")
    }

    @Test
    fun `should not warn when sampler is unset`() {
        val warnings = mutableListOf<String>()
        toBehavior({ null }, warnings::add)
        assertTrue(warnings.isEmpty())
    }

    private fun env(sampler: String, arg: String? = null): (String) -> String? {
        val values = buildMap {
            put("OTEL_TRACES_SAMPLER", sampler)
            if (arg != null) {
                put("OTEL_TRACES_SAMPLER_ARG", arg)
            }
        }
        return values::get
    }

    private fun toBehavior(
        getEnvVar: (String) -> String?,
        onSamplerWarning: (String) -> Unit = {},
    ): OpenTelemetryBehavior =
        OpenTelemetryEnvVars(EnvVarReader(getEnvVar), onSamplerWarning).toBehavior()
}
