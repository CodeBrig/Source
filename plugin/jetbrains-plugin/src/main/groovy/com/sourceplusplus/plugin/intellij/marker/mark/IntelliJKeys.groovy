package com.sourceplusplus.plugin.intellij.marker.mark

import plus.sourceplus.marker.source.mark.api.key.SourceKey

import java.time.Instant

/**
 * todo: description
 *
 * @version 0.2.4
 * @since 0.2.4
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class IntelliJKeys {
    public static final SourceKey<Boolean> ArtifactSubscribed = new SourceKey<>("ArtifactSubscribed")
    public static final SourceKey<Boolean> ArtifactDataAvailable = new SourceKey<>("ArtifactDataAvailable")
    public static final SourceKey<Instant> ArtifactSubscribeTime = new SourceKey<>("ArtifactSubscribeTime")
    public static final SourceKey<Instant> ArtifactUnsubscribeTime = new SourceKey<>("ArtifactUnsubscribeTime")
    public static final SourceKey<String> PortalUUID = new SourceKey<>("PortalUUID")
}