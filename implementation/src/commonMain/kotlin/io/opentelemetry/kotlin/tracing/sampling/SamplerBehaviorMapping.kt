package io.opentelemetry.kotlin.tracing.sampling

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.behavior.SamplerBehavior
import io.opentelemetry.kotlin.init.SamplerConfigDsl

@ExperimentalApi
internal fun SamplerConfigDsl.toSampler(behavior: SamplerBehavior): Sampler = when (behavior) {
    SamplerBehavior.AlwaysOn -> alwaysOn()
    SamplerBehavior.AlwaysOff -> alwaysOff()
    is SamplerBehavior.TraceIdRatioBased -> traceIdRatioBased(behavior.ratio ?: 1.0)
    is SamplerBehavior.ParentBased -> parentBased(
        root = behavior.root?.let { toSampler(it) } ?: alwaysOn(),
        remoteParentSampled = behavior.remoteParentSampled?.let { toSampler(it) } ?: alwaysOn(),
        remoteParentNotSampled = behavior.remoteParentNotSampled?.let { toSampler(it) }
            ?: alwaysOff(),
        localParentSampled = behavior.localParentSampled?.let { toSampler(it) } ?: alwaysOn(),
        localParentNotSampled = behavior.localParentNotSampled?.let { toSampler(it) } ?: alwaysOff()
    )
}