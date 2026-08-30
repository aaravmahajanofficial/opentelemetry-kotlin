package io.opentelemetry.kotlin.tracing.sampling

import io.opentelemetry.kotlin.attributes.AttributeContainer
import io.opentelemetry.kotlin.attributes.EmptyAttributeContainer
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.model.SpanLink
import io.opentelemetry.kotlin.tracing.sampling.SamplingResult.Decision
import kotlin.math.abs

internal class TraceIdRatioBasedSampler(
    ratio: Double,
) : Sampler {

    private val isUpperBound: Long = boundForRatio(ratio)

    override val description: String = "TraceIdRatioBasedSampler{$ratio}"

    override fun shouldSample(
        context: Context,
        traceIdBytes: ByteArray,
        name: String,
        spanKind: SpanKind,
        attributes: AttributeContainer,
        links: List<SpanLink>,
    ): SamplingResult {
        val parentTraceState = context.extractSpan().spanContext.traceState
        val decision = if (isSampled(traceIdBytes)) {
            Decision.RECORD_AND_SAMPLE
        } else {
            Decision.DROP
        }
        return SamplingResultImpl(
            decision = decision,
            attributes = EmptyAttributeContainer,
            traceState = parentTraceState
        )
    }

    private fun isSampled(traceBytes: ByteArray): Boolean {
        if (traceBytes.size != TRACE_ID_LENGTH) return false
        return abs(traceIdRandomPart(traceBytes)) < isUpperBound
    }

    private companion object {
        const val TRACE_ID_LENGTH = 16
        const val RANDOM_PART_OFFSET = 8

        fun boundForRatio(ratio: Double): Long {
            require(ratio in 0.0..1.0) { "ratio must be in range [0.0, 1.0], got $ratio" }
            return when (ratio) {
                0.0 -> Long.MIN_VALUE
                1.0 -> Long.MAX_VALUE
                else -> (ratio * Long.MAX_VALUE).toLong()
            }
        }

        fun traceIdRandomPart(traceIdBytes: ByteArray): Long {
            var result = 0L
            for (i in RANDOM_PART_OFFSET until TRACE_ID_LENGTH) {
                result = (result shl 8) or (traceIdBytes[i].toLong() and 0xFF)
            }
            return result
        }
    }
}