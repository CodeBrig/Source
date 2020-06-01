package com.sourceplusplus.core.integration.apm.skywalking

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.google.common.base.Charsets
import com.google.common.io.Resources
import com.sourceplusplus.api.model.artifact.SourceArtifact
import com.sourceplusplus.api.model.trace.TraceSpanStackQuery
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import org.junit.BeforeClass
import org.junit.Test

import static com.sourceplusplus.core.integration.apm.skywalking.SkywalkingIntegration.processTraceStack
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

class SkywalkingIntegrationTest {

    private static final String INNER_ERROR = Resources.toString(Resources.getResource(
            "integration/apm/skywalking/traceStack/innerError.json"), Charsets.UTF_8)
    private static final String SKIP_ENTRY_COMPONENT = Resources.toString(Resources.getResource(
            "integration/apm/skywalking/traceStack/skipEntryComponent.json"), Charsets.UTF_8)

    @BeforeClass
    static void setup() {
        DatabindCodec.mapper().registerModule(new GuavaModule())
    }

    @Test
    void innerError() {
        def topLevelArtifact = SourceArtifact.builder()
                .artifactQualifiedName("com.company.JavaErrorExample.invokesMethod()").build()
        def spanQuery = TraceSpanStackQuery.builder()
                .traceId("8.1.15908316452720103")
                .oneLevelDeep(true).build()
        def processedTraceStack = processTraceStack(new JsonObject(INNER_ERROR), spanQuery, topLevelArtifact, [:])

        assertNotNull(processedTraceStack)
        assertEquals(3, processedTraceStack.size())
        assertEquals(0L, processedTraceStack.get(0).spanId())
        assertEquals(true, processedTraceStack.get(0).isChildError())
        assertEquals(1L, processedTraceStack.get(1).spanId())
        assertEquals(true, processedTraceStack.get(1).isChildError())
        assertEquals(3L, processedTraceStack.get(2).spanId())
        assertEquals(false, processedTraceStack.get(2).isChildError())
    }

    @Test
    void followSpan() {
        def topLevelArtifact = SourceArtifact.builder()
                .artifactQualifiedName("com.company.JavaErrorExample.invokesMethod()").build()
        def spanQuery = TraceSpanStackQuery.builder()
                .traceId("8.1.15908316452720103").spanId(1)
                .oneLevelDeep(true).build()
        def processedTraceStack = processTraceStack(new JsonObject(INNER_ERROR), spanQuery, topLevelArtifact, [:])

        assertNotNull(processedTraceStack)
        assertEquals(2, processedTraceStack.size())
        assertEquals(1L, processedTraceStack.get(0).spanId())
        assertEquals(false, processedTraceStack.get(0).isError())
        assertEquals(true, processedTraceStack.get(0).isChildError())
        assertEquals(2L, processedTraceStack.get(1).spanId())
        assertEquals(true, processedTraceStack.get(1).isError())
        assertEquals(false, processedTraceStack.get(1).isChildError())
    }

    @Test
    void skipEntryComponent() {
        def topLevelArtifact = SourceArtifact.builder()
                .artifactQualifiedName("com.sourceplusplus.core.api.artifact.ArtifactAPI.getApplicationSourceArtifactsRoute(io.vertx.ext.web.RoutingContext)").build()
        def spanQuery = TraceSpanStackQuery.builder()
                .traceId("11.36.15908373537845481")
                .oneLevelDeep(true).build()
        def processedTraceStack = processTraceStack(new JsonObject(SKIP_ENTRY_COMPONENT), spanQuery, topLevelArtifact, [:])

        assertNotNull(processedTraceStack)
        assertEquals(2, processedTraceStack.size())
        assertEquals(4L, processedTraceStack.get(0).spanId())
        assertEquals(false, processedTraceStack.get(0).isError())
        assertEquals(false, processedTraceStack.get(0).isChildError())
        assertEquals(5L, processedTraceStack.get(1).spanId())
        assertEquals(false, processedTraceStack.get(1).isError())
        assertEquals(false, processedTraceStack.get(1).isChildError())
    }

    //todo: followExit test
}