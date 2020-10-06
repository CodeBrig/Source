package com.sourceplusplus.mentor.impl.task.analyze

import com.sourceplusplus.mentor.base.ContextKey
import com.sourceplusplus.mentor.base.MentorJob
import com.sourceplusplus.mentor.base.MentorTask
import com.sourceplusplus.mentor.base.MentorTaskContext
import com.sourceplusplus.protocol.advice.cautionary.RampDetectionAdvice
import com.sourceplusplus.protocol.artifact.ArtifactQualifiedName
import com.sourceplusplus.protocol.artifact.ArtifactType
import com.sourceplusplus.protocol.artifact.trace.TraceResult
import org.apache.commons.math3.stat.regression.SimpleRegression

/**
 * todo: description.
 *
 * @since 0.0.1
 * @author [Brandon Fergerson](mailto:bfergerson@apache.org)
 */
class CalculateLinearRegression(
    private val byTracesContext: ContextKey<TraceResult>,
    private val confidence: Double,
    val regressionMap: MutableMap<String, SimpleRegression> = mutableMapOf()
) : MentorTask() {

    override suspend fun executeTask(job: MentorJob) {
        job.log("Task configuration\n\tbyTracesContext: $byTracesContext")

        val traceResult = job.context.get(byTracesContext)
        for (trace in traceResult.traces) {
            regressionMap.putIfAbsent(trace.operationNames[0], SimpleRegression())
            val regression = regressionMap[trace.operationNames[0]]!!
            regression.addData(trace.start.toDouble(), trace.duration.toDouble())
        }

        //todo: there should likely be a way to give endpoints priority based on the likelihood for it to be a performance ramp

        regressionMap.forEach {
            if (it.value.slope >= 0 && it.value.rSquare >= confidence && it.value.n >= 100) {
                job.addAdvice(
                    RampDetectionAdvice(
                        ArtifactQualifiedName(it.key, "todo", ArtifactType.ENDPOINT),
                        ApacheSimpleRegression(it.value)
                    )
                )
            }
        }
    }

    /**
     * This task uses a persistent regression map so two tasks should never assume their using the same context.
     */
    override fun usingSameContext(
        selfContext: MentorTaskContext,
        otherContext: MentorTaskContext,
        task: MentorTask
    ): Boolean = false

    class ApacheSimpleRegression(private val sr: SimpleRegression) : RampDetectionAdvice.SimpleRegression {
        override val n get() = sr.n
        override val intercept get() = sr.intercept
        override val slope get() = sr.slope
        override val sumSquaredErrors get() = sr.sumSquaredErrors
        override val totalSumSquares get() = sr.totalSumSquares
        override val xSumSquares get() = sr.xSumSquares
        override val sumOfCrossProducts get() = sr.sumOfCrossProducts
        override val regressionSumSquares get() = sr.regressionSumSquares
        override val meanSquareError get() = sr.meanSquareError
        override val r get() = sr.r
        override val rSquare get() = sr.rSquare
        override val interceptStdErr get() = sr.interceptStdErr
        override val slopeStdErr get() = sr.slopeStdErr
        override val slopeConfidenceInterval get() = sr.slopeConfidenceInterval
        override val significance get() = sr.significance
        override fun predict(x: Double) = sr.predict(x)
    }
}