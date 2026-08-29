package io.opentelemetry.kotlin.config.yaml

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.config.schema.model.ParentBasedSampler
import io.opentelemetry.kotlin.config.schema.model.Sampler
import io.opentelemetry.kotlin.config.schema.model.TraceIdRatioBasedSampler

@ExperimentalApi
fun Sampler.toBehavior(): SamplerBehavior? {
    val mapped = listOfNotNull(
        alwaysOn?.let { SamplerBehavior.AlwaysOn },
        alwaysOff?.let { SamplerBehavior.AlwaysOff },
        traceIdRatioBased?.toBehavior(),
        parentBased?.toBehavior()
    )

    return mapped.singleOrNull()
}

private fun ParentBasedSampler.toBehavior(): SamplerBehavior.ParentBased =
    SamplerBehavior.ParentBased(
        root = root?.toBehavior(),
        remoteParentSampled = remoteParentSampled?.toBehavior(),
        remoteParentNotSampled = remoteParentNotSampled?.toBehavior(),
        localParentSampled = localParentSampled?.toBehavior(),
        localParentNotSampled = localParentNotSampled?.toBehavior()
    )

private fun TraceIdRatioBasedSampler.toBehavior(): SamplerBehavior.TraceIdRatioBased? {
    if (ratio != null && ratioOrUnset(ratio) == null) return null
    return SamplerBehavior.TraceIdRatioBased(ratio = ratioOrUnset(ratio))
}

private fun ratioOrUnset(ratio: Double?): Double? {
    return ratio?.takeIf { it in 0.0..1.0 }
}
