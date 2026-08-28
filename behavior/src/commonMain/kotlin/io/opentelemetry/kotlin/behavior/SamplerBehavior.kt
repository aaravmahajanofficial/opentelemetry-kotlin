package io.opentelemetry.kotlin.behavior

import io.opentelemetry.kotlin.ExperimentalApi

@ExperimentalApi
sealed class SamplerBehavior : Behavior<SamplerBehavior> {

    override fun mergeWith(higher: SamplerBehavior): SamplerBehavior = higher

    data object AlwaysOn : SamplerBehavior()
    data object AlwaysOff : SamplerBehavior()
    data class TraceIdRatioBased(val ratio: Double? = null) : SamplerBehavior()

    data class ParentBased(
        val root: SamplerBehavior? = null,
        val remoteParentSampled: SamplerBehavior? = null,
        val remoteParentNotSampled: SamplerBehavior? = null,
        val localParentSampled: SamplerBehavior? = null,
        val localParentNotSampled: SamplerBehavior? = null,
    ) : SamplerBehavior()
}