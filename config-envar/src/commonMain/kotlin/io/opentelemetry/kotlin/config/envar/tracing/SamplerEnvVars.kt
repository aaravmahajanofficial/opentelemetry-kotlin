package io.opentelemetry.kotlin.config.envar.tracing

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.config.envar.EnvVarReader
import io.opentelemetry.kotlin.config.envar.model.EnvVarName.Companion.envVarName

@ExperimentalApi
class SamplerEnvVars(private val reader: EnvVarReader) {
    fun toBehavior(): SamplerBehavior? = when (reader.readString(SAMPLER)) {
        null -> null
        ALWAYS_ON -> SamplerBehavior.AlwaysOn
        ALWAYS_OFF -> SamplerBehavior.AlwaysOff
        TRACE_ID_RATIO -> ratioBased()
        PARENT_BASED_ALWAYS_ON -> SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOn)
        PARENT_BASED_ALWAYS_OFF -> SamplerBehavior.ParentBased(root = SamplerBehavior.AlwaysOff)
        PARENT_BASED_TRACE_ID_RATIO -> ratioBased()?.let { SamplerBehavior.ParentBased(root = it) }
        else -> null
    }

    private fun ratioBased(): SamplerBehavior.TraceIdRatioBased? {
        val raw = reader.readString(SAMPLER_ARG) ?: return SamplerBehavior.TraceIdRatioBased()
        val ratio = raw.toDoubleOrNull()?.takeIf { it in 0.0..1.0 } ?: return null
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