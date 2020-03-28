package com.sourceplusplus.plugin

import com.intellij.psi.PsiFile
import com.sourceplusplus.api.client.SourceCoreClient
import com.sourceplusplus.api.model.artifact.SourceArtifactUnsubscribeRequest
import com.sourceplusplus.api.model.config.SourcePluginConfig
import com.sourceplusplus.api.model.config.SourcePortalConfig
import com.sourceplusplus.plugin.intellij.marker.IntelliJSourceFileMarker
import com.sourceplusplus.plugin.intellij.marker.mark.IntelliJSourceMark
import groovy.util.logging.Slf4j
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import plus.sourceplus.marker.plugin.SourceMarkerPlugin

import static com.sourceplusplus.plugin.coordinate.artifact.track.PluginArtifactSubscriptionTracker.UNSUBSCRIBE_FROM_ARTIFACT

/**
 * todo: description
 *
 * @version 0.2.3
 * @since 0.1.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
@Slf4j
class SourcePlugin {

    //todo: replace with SourceMark listeners
    public static final String SOURCE_FILE_MARKER_ACTIVATED = "SourceFileMarkerActivated"
    public static final String SOURCE_ENVIRONMENT_UPDATED = "SourceEnvironmentUpdated"

    private final Vertx vertx
    private PluginBootstrap pluginBootstrap

    SourcePlugin(SourceCoreClient coreClient) {
        vertx = Vertx.vertx()
        System.addShutdownHook {
            vertx.close()
        }
        updateEnvironment(Objects.requireNonNull(coreClient))
        vertx.deployVerticle(pluginBootstrap = new PluginBootstrap(this))

        //start plugin bridge for portal
        startPortalUIBridge({
            if (it.failed()) {
                log.error("Failed to start portal ui bridge", it.cause())
                throw new RuntimeException(it.cause())
            } else {
                log.info("PluginBootstrap started")
                SourcePortalConfig.current.pluginUIPort = it.result().actualPort()
                log.info("Using portal ui bridge port: " + SourcePortalConfig.current.pluginUIPort)
            }
        })
    }

    void updateEnvironment(SourceCoreClient coreClient) {
        SourcePluginConfig.current.activeEnvironment.coreClient = coreClient
        coreClient.attachBridge(vertx)
        if (SourcePluginConfig.current.activeEnvironment.appUuid) {
            SourcePortalConfig.current.addCoreClient(SourcePluginConfig.current.activeEnvironment.appUuid, coreClient)
        }
        vertx.eventBus().publish(SOURCE_ENVIRONMENT_UPDATED, SourcePluginConfig.current.activeEnvironment.environmentName)
    }

    private void startPortalUIBridge(Handler<AsyncResult<HttpServer>> listenHandler) {
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx)
        BridgeOptions portalBridgeOptions = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex(".+"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(".+"))
        sockJSHandler.bridge(portalBridgeOptions)

        Router router = Router.router(vertx)
        router.route("/eventbus/*").handler(sockJSHandler)
        vertx.createHttpServer().requestHandler(router).listen(0, listenHandler)
    }

    void clearActiveSourceFileMarkers() {
        availableSourceFileMarkers.each {
            deactivateSourceFileMarker(it)
        }
        availableSourceFileMarkers.clear()
    }

    void refreshActiveSourceFileMarkers() {
        availableSourceFileMarkers.each {
            it.refresh()
        }
    }

//    void activateSourceFileMarker(IntelliJSourceFileMarker sourceFileMarker) {
//        if (availableSourceFileMarkers.add(Objects.requireNonNull(sourceFileMarker))) {
//            def sourceMarks = sourceFileMarker.createSourceMarks()
//            sourceFileMarker.setSourceMarks(sourceMarks)
//            log.info("Activated source file marker: {} - Mark count: {}", sourceFileMarker, sourceMarks.size())
//            vertx.eventBus().publish(SOURCE_FILE_MARKER_ACTIVATED, sourceFileMarker.sourceFile.qualifiedClassName)
//        }
//    }

    void deactivateSourceFileMarker(IntelliJSourceFileMarker sourceFileMarker) {
        if (availableSourceFileMarkers.remove(Objects.requireNonNull(sourceFileMarker))) {
            def sourceMarks = sourceFileMarker.getSourceMarks()

            log.info("Deactivated source file marker: {} - Mark count: {}", sourceFileMarker, sourceMarks.size())
            sourceMarks.each {
                if (it.artifactSubscribed) {
                    def unsubscribeRequest = SourceArtifactUnsubscribeRequest.builder()
                            .appUuid(SourcePluginConfig.current.activeEnvironment.appUuid)
                            .artifactQualifiedName(it.artifactQualifiedName)
                            .removeAllArtifactSubscriptions(true)
                            .build()
                    vertx.eventBus().send(UNSUBSCRIBE_FROM_ARTIFACT, unsubscribeRequest)
                }

                sourceFileMarker.removeSourceMark(it)
                log.trace("Removed source mark: {}", it)
            }
            sourceFileMarker.refresh()
        }
    }

    @Nullable
    IntelliJSourceFileMarker getSourceFileMarker(String classQualifiedName) {
        return SourceMarkerPlugin.INSTANCE.getSourceFileMarker(classQualifiedName) as IntelliJSourceFileMarker
    }

    @NotNull
    Set<IntelliJSourceFileMarker> getAvailableSourceFileMarkers() {
        return SourceMarkerPlugin.INSTANCE.getAvailableSourceFileMarkers().toSet() as Set<IntelliJSourceFileMarker>
    }

    IntelliJSourceFileMarker getSourceFileMarker(PsiFile psiFile) {
        return SourceMarkerPlugin.INSTANCE.getSourceFileMarker(psiFile) as IntelliJSourceFileMarker
    }

    @Nullable
    IntelliJSourceMark getSourceMark(String artifactQualifiedName) {
        return SourceMarkerPlugin.INSTANCE.getSourceMark(artifactQualifiedName) as IntelliJSourceMark
    }

    @NotNull
    Vertx getVertx() {
        return vertx
    }
}