package io.opentelemetry.kotlin.config.envar.tracing

import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.config.envar.EnvVarReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SamplerEnvVarsTest {

    @Test
    fun `should leave unset env vars unset`() {
        assertNull(toBehavior { null })
    }

    @Test
    fun `should map always_on`() {
        assertEquals(SamplerBehavior.AlwaysOn, toBehavior(env("always_on")))
    }

    @Test
    fun `should map always_off`() {
        assertEquals(SamplerBehavior.AlwaysOff, toBehavior(env("always_off")))
    }

    @Test
    fun `should map sampler names case-insensitively`() {
        assertEquals(SamplerBehavior.AlwaysOn, toBehavior(env("ALWAYS_ON")))
        assertEquals(SamplerBehavior.AlwaysOff, toBehavior(env("Always_Off")))
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = 0.25),
            toBehavior(env("TraceIdRatio", "0.25")),
        )
    }

    @Test
    fun `should map traceidratio with a ratio arg`() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = 0.25),
            toBehavior(env("traceidratio", "0.25")),
        )
    }

    @Test
    fun `should leave omitted ratio arg unset`() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = null),
            toBehavior(env("traceidratio")),
        )
    }

    @Test
    fun `should preserve a ratio of zero`() {
        assertEquals(
            SamplerBehavior.TraceIdRatioBased(ratio = 0.0),
            toBehavior(env("traceidratio", "0")),
        )
    }

    @Test
    fun `should map parentbased_always_on`() {
        assertEquals(
            SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOn),
            toBehavior(env("parentbased_always_on")),
        )
    }

    @Test
    fun `should map parentbased_always_off`() {
        assertEquals(
            SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOff),
            toBehavior(env("parentbased_always_off")),
        )
    }

    @Test
    fun `should map parentbased_traceidratio`() {
        assertEquals(
            SamplerBehavior.ParentBased(root = SamplerBehavior.TraceIdRatioBased(ratio = 0.01)),
            toBehavior(env("parentbased_traceidratio", "0.01")),
        )
    }

    @Test
    fun `should ignore arg for samplers that do not take one`() {
        assertEquals(SamplerBehavior.AlwaysOn, toBehavior(env("always_on", "0.25")))
    }

    @Test
    fun `should ignore arg when sampler is unset`() {
        assertNull(toBehavior(mapOf("OTEL_TRACES_SAMPLER_ARG" to "0.25")::get))
    }

    @Test
    fun `should leave unknown sampler unset`() {
        listOf("sampler_test", "").forEach { name ->
            assertNull(toBehavior(env(name)), "<$name> should not configure a sampler")
        }
    }

    @Test
    fun `should leave invalid ratio arg unset`() {
        listOf("invalid", "", "-0.1", "1.1", "NaN").forEach { arg ->
            assertEquals(
                SamplerBehavior.TraceIdRatioBased(ratio = null),
                toBehavior(env("traceidratio", arg)),
                "<$arg> should ignore ARG",
            )
            assertEquals(
                SamplerBehavior.ParentBased(root = SamplerBehavior.TraceIdRatioBased(ratio = null)),
                toBehavior(env("parentbased_traceidratio", arg)),
                "<$arg> should ignore ARG on parentbased",
            )
        }
    }

    @Test
    fun `should warn on unknown sampler`() {
        val warnings = mutableListOf<String>()
        SamplerEnvVars(EnvVarReader(env("not_a_sampler")), warnings::add).toBehavior()
        assertEquals(1, warnings.size)
    }

    @Test
    fun `should warn on invalid ratio arg`() {
        val warnings = mutableListOf<String>()
        SamplerEnvVars(EnvVarReader(env("traceidratio", "nope")), warnings::add).toBehavior()
        assertEquals(1, warnings.size)
    }

    @Test
    fun `should not warn when sampler is unset`() {
        val warnings = mutableListOf<String>()
        SamplerEnvVars(EnvVarReader { null }, warnings::add).toBehavior()
        assertEquals(emptyList(), warnings)
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

    private fun toBehavior(getEnvVar: (String) -> String?) =
        SamplerEnvVars(EnvVarReader(getEnvVar)).toBehavior()
}
