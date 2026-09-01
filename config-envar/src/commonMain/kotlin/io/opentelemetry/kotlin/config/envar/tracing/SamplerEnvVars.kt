package io.opentelemetry.kotlin.config.envar.tracing

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.config.envar.EnvVarReader
import io.opentelemetry.kotlin.config.envar.model.EnvVarName.Companion.envVarName

/**
 * Maps `OTEL_TRACES_SAMPLER` and `OTEL_TRACES_SAMPLER_ARG` onto behavior.
 * Unrecognized sampler names are ignored (and reported via [onWarning]).
 * Invalid ARG is ignored as if ARG were unset (ratio omitted → SDK default 1.0).
 *
 * https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/#general-sdk-configuration
 */
@ExperimentalApi
class SamplerEnvVars(
    private val reader: EnvVarReader,
    private val onWarning: (String) -> Unit = {},
) {
    fun toBehavior(): SamplerBehavior? {
        val name = reader.readString(SAMPLER)?.takeIf { it.isNotEmpty() } ?: return null
        return when (name.lowercase()) {
            ALWAYS_ON -> SamplerBehavior.AlwaysOn
            ALWAYS_OFF -> SamplerBehavior.AlwaysOff
            TRACE_ID_RATIO -> ratioBased()
            PARENT_BASED_ALWAYS_ON -> SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOn)
            PARENT_BASED_ALWAYS_OFF -> SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOff)
            PARENT_BASED_TRACE_ID_RATIO -> SamplerBehavior.ParentBased(root = ratioBased())
            else -> {
                onWarning("Unknown OTEL_TRACES_SAMPLER value '$name'; ignoring")
                null
            }
        }
    }

    private fun ratioBased(): SamplerBehavior.TraceIdRatioBased {
        val raw = reader.readString(SAMPLER_ARG) ?: return SamplerBehavior.TraceIdRatioBased()
        if (raw.isEmpty()) return SamplerBehavior.TraceIdRatioBased()
        val ratio = raw.toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
        if (ratio == null) {
            onWarning("Invalid OTEL_TRACES_SAMPLER_ARG '$raw'; ignoring argument")
            return SamplerBehavior.TraceIdRatioBased()
        }
        return SamplerBehavior.TraceIdRatioBased(ratio)
    }

    private companion object {
        val SAMPLER = envVarName("OTEL_TRACES_SAMPLER")
        val SAMPLER_ARG = envVarName("OTEL_TRACES_SAMPLER_ARG")
        const val ALWAYS_ON = "always_on"
        const val ALWAYS_OFF = "always_off"
        const val TRACE_ID_RATIO = "traceidratio"
        const val PARENT_BASED_ALWAYS_ON = "parentbased_always_on"
        const val PARENT_BASED_ALWAYS_OFF = "parentbased_always_off"
        const val PARENT_BASED_TRACE_ID_RATIO = "parentbased_traceidratio"
    }
}