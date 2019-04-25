package com.sourceplusplus.portal.display.tabs

import com.sourceplusplus.api.bridge.PluginBridgeEndpoints
import com.sourceplusplus.api.client.SourceCoreClient
import com.sourceplusplus.api.model.config.SourcePortalConfig
import com.sourceplusplus.api.model.internal.InnerTraceStackInfo
import com.sourceplusplus.api.model.internal.TraceSpanInfo
import com.sourceplusplus.api.model.trace.*
import com.sourceplusplus.portal.PortalBootstrap
import com.sourceplusplus.portal.SourcePortal
import com.sourceplusplus.portal.coordinate.track.PortalViewTracker
import com.sourceplusplus.portal.display.tabs.views.TracesView
import io.vertx.core.AbstractVerticle
import io.vertx.core.eventbus.Message
import io.vertx.core.json.Json
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration
import java.util.regex.Pattern

/**
 * Displays traces (and the underlying spans) for a given source code artifact.
 *
 * @version 0.2.0
 * @since 0.1.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class TracesTab extends AbstractVerticle {

    public static final String TRACES_TAB_OPENED = "TracesTabOpened"
    public static final String GET_TRACE_STACK = "GetTraceStack"
    public static final String CLICKED_DISPLAY_TRACES = "ClickedDisplayTraces"
    public static final String CLICKED_DISPLAY_TRACE_STACK = "ClickedDisplayTraceStack"
    public static final String CLICKED_DISPLAY_SPAN_INFO = "ClickedDisplaySpanInfo"
    public static final String DISPLAY_TRACES = "DisplayTraces"
    public static final String DISPLAY_TRACE_STACK = "DisplayTraceStack"
    public static final String DISPLAY_SPAN_INFO = "DisplaySpanInfo"

    private static final Logger log = LoggerFactory.getLogger(this.name)
    private static final Pattern QUALIFIED_NAME_PATTERN = Pattern.compile('.+\\..+\\(.*\\)')
    private final SourceCoreClient coreClient
    private final boolean pluginAvailable

    TracesTab(SourceCoreClient coreClient, boolean pluginAvailable) {
        this.coreClient = Objects.requireNonNull(coreClient)
        this.pluginAvailable = pluginAvailable
    }

    @Override
    void start() throws Exception {
        //refresh with traces from cache (if avail)
        vertx.eventBus().consumer(TRACES_TAB_OPENED, {
            log.info("Traces tab opened")

            if (pluginAvailable) {
                def message = JsonObject.mapFrom(it.body())
                def portal = SourcePortal.getPortal(message.getString("portal_uuid"))
                def orderType = message.getString("trace_order_type")
                if (orderType) {
                    //user possibly changed current trace order type; todo: create event
                    portal.interface.tracesView.orderType = TraceOrderType.valueOf(orderType.toUpperCase())
                }
                triggerPortalOpened(it)
            } else {
                def subscriptions = config().getJsonArray("artifact_subscriptions")
                for (int i = 0; i < subscriptions.size(); i++) {
                    def sub = subscriptions.getJsonObject(i)
                    def appUuid = sub.getString("app_uuid")
                    def artifactQualifiedName = sub.getString("artifact_qualified_name")

                    SourcePortal.getPortals(appUuid, artifactQualifiedName).each {
                        if (it.interface.tracesView.artifactTraceResult) {
                            updateUI(it)
                        }
                    }
                }
            }
        })
        vertx.eventBus().consumer(PluginBridgeEndpoints.ARTIFACT_TRACE_UPDATED.address, {
            handleArtifactTraceResult(it.body() as ArtifactTraceResult)
        })

        //user viewing portal under new artifact
        vertx.eventBus().consumer(PortalViewTracker.CHANGED_PORTAL_ARTIFACT, {
            def portal = SourcePortal.getPortal(JsonObject.mapFrom(it.body()).getString("portal_uuid"))
            vertx.eventBus().send(portal.portalUuid + "-ClearTraceStack", new JsonObject())
        })

        //populate with latest traces from cache (if avail) on switch to traces
        vertx.eventBus().consumer(PortalViewTracker.OPENED_PORTAL, {
            triggerPortalOpened(it)
        })

        //user clicked into trace stack
        vertx.eventBus().consumer(CLICKED_DISPLAY_TRACE_STACK, { messageHandler ->
            def request = messageHandler.body() as JsonObject
            log.debug("Displaying trace stack: " + request)

            if (request.getString("trace_id") == null) {
                def portal = SourcePortal.getPortal(request.getString("portal_uuid"))
                portal.interface.tracesView.viewType = TracesView.ViewType.TRACE_STACK
                updateUI(portal)
            } else {
                vertx.eventBus().send(GET_TRACE_STACK, request, {
                    if (it.failed()) {
                        it.cause().printStackTrace()
                        log.error("Failed to display trace stack", it.cause())
                    } else {
                        def portal = SourcePortal.getPortal(request.getString("portal_uuid"))
                        portal.interface.tracesView.viewType = TracesView.ViewType.TRACE_STACK
                        portal.interface.tracesView.traceStack = it.result().body() as JsonArray
                        portal.interface.tracesView.traceId = request.getString("trace_id")
                        updateUI(portal)
                    }
                })
            }
        })

        vertx.eventBus().consumer(CLICKED_DISPLAY_TRACES, {
            def portal = SourcePortal.getPortal((it.body() as JsonObject).getString("portal_uuid"))
            def representation = portal.interface.tracesView
            representation.viewType = TracesView.ViewType.TRACES

            if (representation.rootArtifactQualifiedName == null) {
                representation.innerTrace = false
                representation.innerLevel = 0
                updateUI(portal)
            } else {
                vertx.eventBus().send("NavigateToArtifact",
                        new JsonObject().put("portal_uuid", portal.portalUuid)
                                .put("artifact_qualified_name", representation.rootArtifactQualifiedName)
                )
            }
        })

        //user clicked into span
        vertx.eventBus().consumer(CLICKED_DISPLAY_SPAN_INFO, { messageHandler ->
            def spanInfoRequest = messageHandler.body() as JsonObject
            log.debug("Clicked display span info: " + spanInfoRequest)

            def portalUuid = spanInfoRequest.getString("portal_uuid")
            def portal = SourcePortal.getPortal(portalUuid)
            def representation = portal.interface.tracesView
            representation.viewType = TracesView.ViewType.SPAN_INFO
            representation.traceId = spanInfoRequest.getString("trace_id")
            representation.spanId = spanInfoRequest.getInteger("span_id")
            updateUI(portal)
        })

        //query core for trace stack (or get from cache)
        vertx.eventBus().consumer(GET_TRACE_STACK, { messageHandler ->
            def timer = PortalBootstrap.portalMetrics.timer(GET_TRACE_STACK)
            def context = timer.time()
            def request = messageHandler.body() as JsonObject
            def portalUuid = request.getString("portal_uuid")
            def appUuid = request.getString("app_uuid")
            def artifactQualifiedName = request.getString("artifact_qualified_name")
            def globalTraceId = request.getString("trace_id")
            log.trace("Getting trace spans. Artifact qualified name: %s - Trace id: %s",
                    artifactQualifiedName, globalTraceId)

            def portal = SourcePortal.getPortal(portalUuid)
            def representation = portal.interface.tracesView
            def traceStack = representation.getTraceStack(globalTraceId)
            if (traceStack != null) {
                log.trace("Got trace spans: $globalTraceId from cache - Stack size: " + traceStack.size())
                messageHandler.reply(traceStack)
                context.stop()
            } else {
                def traceStackQuery = TraceSpanStackQuery.builder()
                        .oneLevelDeep(true)
                        .traceId(globalTraceId).build()
                coreClient.getTraceSpans(appUuid, artifactQualifiedName, traceStackQuery, {
                    if (it.failed()) {
                        log.error("Failed to get trace spans", it.cause())
                    } else {
                        representation.cacheTraceStack(globalTraceId, handleTraceStack(
                                appUuid, artifactQualifiedName, it.result()))
                        messageHandler.reply(representation.getTraceStack(globalTraceId))
                        context.stop()
                    }
                })
            }
        })

        vertx.eventBus().consumer(PortalViewTracker.UPDATED_METRIC_TIME_FRAME, {
            if (pluginAvailable) {
                def portalUuid = (it.body() as JsonObject).getString("portal_uuid")
                def portal = SourcePortal.getPortal(portalUuid)
                if (portal.interface.viewingPortalArtifact == null) {
                    return
                }

                //subscribe (re-subscribe) to traces
                def request = ArtifactTraceSubscribeRequest.builder()
                        .appUuid(portal.appUuid)
                        .artifactQualifiedName(portal.interface.viewingPortalArtifact)
                        .addOrderTypes(TraceOrderType.LATEST_TRACES, TraceOrderType.SLOWEST_TRACES)
                        .build()
                coreClient.subscribeToArtifact(request, {
                    if (it.succeeded()) {
                        log.info("Successfully subscribed to traces with request: " + request)
                    } else {
                        log.error("Failed to subscribe to artifact traces", it.cause())
                    }
                })
            } else {
                //subscribe (re-subscribe) to traces
                def subscriptions = config().getJsonArray("artifact_subscriptions")
                for (int i = 0; i < subscriptions.size(); i++) {
                    def sub = subscriptions.getJsonObject(i)
                    def request = ArtifactTraceSubscribeRequest.builder()
                            .appUuid(sub.getString("app_uuid"))
                            .artifactQualifiedName(sub.getString("artifact_qualified_name"))
                            .addOrderTypes(TraceOrderType.LATEST_TRACES, TraceOrderType.SLOWEST_TRACES)
                            .build()
                    coreClient.subscribeToArtifact(request, {
                        if (it.succeeded()) {
                            log.info("Successfully subscribed to traces with request: " + request)
                        } else {
                            log.error("Failed to subscribe to artifact traces", it.cause())
                        }
                    })
                }
            }
        })
        log.info("{} started", getClass().getSimpleName())
    }

    private void updateUI(SourcePortal portal) {
        switch (portal.interface.tracesView.viewType) {
            case TracesView.ViewType.TRACES:
                displayTraces(portal)
                break
            case TracesView.ViewType.TRACE_STACK:
                displayTraceStack(portal)
                break
            case TracesView.ViewType.SPAN_INFO:
                displaySpanInfo(portal)
                break
        }
    }

    private void displayTraces(SourcePortal portal) {
        if (portal.interface.tracesView.artifactTraceResult) {
            def artifactTraceResult = portal.interface.tracesView.artifactTraceResult
            vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_TRACES",
                    new JsonObject(Json.encode(artifactTraceResult)))
            log.info("Displayed traces for artifact: " + artifactTraceResult.artifactQualifiedName()
                    + " - Type: " + artifactTraceResult.orderType()
                    + " - Trace size: " + artifactTraceResult.traces().size())
        }
    }

    private void displayTraceStack(SourcePortal portal) {
        def traceId = portal.interface.tracesView.traceId
        def traceStack = portal.interface.tracesView.traceStack
        if (traceStack && !traceStack.isEmpty()) {
            vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_TRACE_STACK",
                    portal.interface.tracesView.traceStack)
            log.info("Displayed trace stack for id: $traceId - Stack size: " + traceStack.size())
        }
    }

    private void displaySpanInfo(SourcePortal portal) {
        def traceId = portal.interface.tracesView.traceId
        def spanId = portal.interface.tracesView.spanId
        def representation = portal.interface.tracesView
        def traceStack
        if (representation.innerTrace) {
            traceStack = representation.innerTraceStack
        } else {
            traceStack = representation.getTraceStack(traceId)
        }

        for (int i = 0; i < traceStack.size(); i++) {
            def span = traceStack.getJsonObject(i).getJsonObject("span")
            if (span.getInteger("span_id") == spanId) {
                def spanArtifactQualifiedName = span.getString("artifact_qualified_name")
                if (portal.external
                        || spanArtifactQualifiedName == null
                        || spanArtifactQualifiedName == portal.interface.viewingPortalArtifact) {
                    vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_SPAN_INFO", span)
                    log.info("Displayed trace span info: " + span)
                } else {
                    vertx.eventBus().send("CanNavigateToArtifact", spanArtifactQualifiedName, {
                        if (it.succeeded() && it.result().body() == true) {
                            def spanStackQuery = TraceSpanStackQuery.builder()
                                    .oneLevelDeep(true).followExit(true)
                                    .segmentId(span.getString("segment_id"))
                                    .spanId(span.getLong("span_id"))
                                    .traceId(traceId).build()

                            def spanPortal = SourcePortal.getInternalPortal(SourcePortalConfig.current.appUuid, spanArtifactQualifiedName)
                            if (!spanPortal.isPresent()) {
                                log.error("Failed to get span portal:" + spanArtifactQualifiedName)
                                vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_SPAN_INFO", span)
                                return
                            }

                            //todo: cache
                            coreClient.getTraceSpans(SourcePortalConfig.current.appUuid,
                                    portal.interface.viewingPortalArtifact, spanStackQuery, {
                                if (it.failed()) {
                                    log.error("Failed to get trace spans", it.cause())
                                    vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_SPAN_INFO", span)
                                } else {
                                    //navigated away from portal; reset to trace stack
                                    portal.interface.tracesView.viewType = TracesView.ViewType.TRACE_STACK

                                    def queryResult = it.result()
                                    def innerLevel = representation.innerLevel + 1
                                    def spanTracesView = spanPortal.get().interface.tracesView
                                    if (span.getString("type") == "Exit"
                                            && queryResult.traceSpans().get(0).type() == "Entry") {
                                        innerLevel = 0
                                    } else {
                                        spanTracesView.rootArtifactQualifiedName = portal.interface.viewingPortalArtifact
                                    }
                                    spanTracesView.innerTrace = true
                                    spanTracesView.innerLevel = innerLevel
                                    spanTracesView.innerTraceStack = handleTraceStack(
                                            SourcePortalConfig.current.appUuid, portal.interface.viewingPortalArtifact, queryResult)
                                    vertx.eventBus().send("NavigateToArtifact",
                                            new JsonObject().put("portal_uuid", spanPortal.get().portalUuid)
                                                    .put("artifact_qualified_name", spanArtifactQualifiedName))
                                }
                            })
                        } else {
                            vertx.eventBus().send(portal.portalUuid + "-$DISPLAY_SPAN_INFO", span)
                            log.info("Displayed trace span info: " + span)
                        }
                    })
                }
            }
        }
    }

    private void triggerPortalOpened(Message<Object> it) {
        def portal = SourcePortal.getPortal(JsonObject.mapFrom(it.body()).getString("portal_uuid"))
        def representation = portal.interface.tracesView
        if (representation.innerTrace && representation.viewType != TracesView.ViewType.SPAN_INFO) {
            def innerTraceStackInfo = InnerTraceStackInfo.builder()
                    .innerLevel(representation.innerLevel)
                    .traceStack(representation.innerTraceStack).build()
            vertx.eventBus().publish(portal.portalUuid + "-DisplayInnerTraceStack",
                    new JsonObject(Json.encode(innerTraceStackInfo))
            )
            log.info("Displayed inner trace stack. Stack size: " + representation.innerTraceStack.size())
        } else if (representation.artifactTraceResult != null) {
            updateUI(portal)
        }
    }

    private void handleArtifactTraceResult(ArtifactTraceResult artifactTraceResult) {
        def traces = new ArrayList<Trace>()
        artifactTraceResult.traces().each {
            traces.add(it.withPrettyDuration(humanReadableDuration(Duration.ofMillis(it.duration()))))
        }
        artifactTraceResult = artifactTraceResult.withTraces(traces)
                .withArtifactSimpleName(removePackageAndClassName(removePackageNames(artifactTraceResult.artifactQualifiedName())))

        SourcePortal.getPortals(artifactTraceResult.appUuid(), artifactTraceResult.artifactQualifiedName()).each {
            def representation = it.interface.tracesView
            representation.cacheArtifactTraceResult(artifactTraceResult)

            if (!pluginAvailable || it.interface.viewingPortalArtifact == artifactTraceResult.artifactQualifiedName()
                    && it.interface.tracesView.viewType == TracesView.ViewType.TRACES) {
                updateUI(it)
            }
        }
    }

    private static JsonArray handleTraceStack(String appUuid, String rootArtifactQualifiedName,
                                              TraceSpanStackQueryResult spanQueryResult) {
        def spanInfos = new ArrayList<TraceSpanInfo>()
        def totalTime = spanQueryResult.traceSpans().get(0).endTime() - spanQueryResult.traceSpans().get(0).startTime()

        for (def span : spanQueryResult.traceSpans()) {
            def timeTookMs = span.endTime() - span.startTime()
            def timeTook = humanReadableDuration(Duration.ofMillis(timeTookMs))
            def spanInfo = TraceSpanInfo.builder()
                    .span(span)
                    .appUuid(appUuid)
                    .rootArtifactQualifiedName(rootArtifactQualifiedName)
                    .timeTook(timeTook)
                    .totalTracePercent((totalTime == 0) ? 0d : timeTookMs / totalTime * 100.0d)

            //detect if operation name is really an artifact name
            if (QUALIFIED_NAME_PATTERN.matcher(span.endpointName()).matches()) {
                spanInfo.span(span = span.withArtifactQualifiedName(span.endpointName()))
            }
            if (span.artifactQualifiedName()) {
                spanInfo.operationName(removePackageAndClassName(removePackageNames(span.artifactQualifiedName())))
            } else {
                spanInfo.operationName(span.endpointName())
            }
            spanInfos.add(spanInfo.build())
        }
        return new JsonArray(Json.encode(spanInfos))
    }

    static String removePackageNames(String qualifiedMethodName) {
        if (!qualifiedMethodName || qualifiedMethodName.indexOf('.') == -1) return qualifiedMethodName
        def className = qualifiedMethodName.substring(0, qualifiedMethodName.substring(
                0, qualifiedMethodName.indexOf("(")).lastIndexOf("."))
        if (className.contains('$')) {
            className = className.substring(0, className.indexOf('$'))
        }

        def arguments = qualifiedMethodName.substring(qualifiedMethodName.indexOf("("))
        def argArray = arguments.substring(1, arguments.length() - 1).split(",")
        def argText = "("
        for (def i = 0; i < argArray.length; i++) {
            def qualifiedArgument = argArray[i]
            def newArgText = qualifiedArgument.substring(qualifiedArgument.lastIndexOf(".") + 1)
            if (qualifiedArgument.startsWith(className + '$')) {
                newArgText = qualifiedArgument.substring(qualifiedArgument.lastIndexOf('$') + 1)
            }
            argText += newArgText

            if ((i + 1) < argArray.length) {
                argText += ","
            }
        }
        argText += ")"

        def methodNameArr = qualifiedMethodName.substring(0, qualifiedMethodName.indexOf("(")).split('\\.')
        if (methodNameArr.length == 1) {
            return methodNameArr[0] + argText
        } else {
            return methodNameArr[methodNameArr.length - 2] + '.' + methodNameArr[methodNameArr.length - 1] + argText
        }
    }

    static removePackageAndClassName(String qualifiedMethodName) {
        if (!qualifiedMethodName || qualifiedMethodName.indexOf('.') == -1 || qualifiedMethodName.indexOf('(') == -1) {
            return qualifiedMethodName
        }
        return qualifiedMethodName.substring(qualifiedMethodName.substring(
                0, qualifiedMethodName.indexOf("(")).lastIndexOf(".") + 1)
    }

    static String humanReadableDuration(Duration duration) {
        if (duration.seconds < 1) {
            return duration.toMillis() + "ms"
        }
        return duration.toString().substring(2)
                .replaceAll('(\\d[HMS])(?!$)', '$1 ')
                .toLowerCase()
    }
}
