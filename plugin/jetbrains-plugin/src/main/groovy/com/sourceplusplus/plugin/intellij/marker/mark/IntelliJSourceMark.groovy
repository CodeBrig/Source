package com.sourceplusplus.plugin.intellij.marker.mark

import com.sourceplusplus.marker.source.mark.api.SourceMark

/**
 * Extension of the SourceMark for handling IntelliJ.
 *
 * @version 0.2.5
 * @since 0.2.5
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
interface IntelliJSourceMark extends SourceMark {

    static final String SOURCE_MARK_CREATED = "SourceMarkCreated" //todo: ensure used only when necessary
    static final String SOURCE_MARK_APPLIED = "SourceMarkApplied"

    void markArtifactSubscribed()

    void markArtifactUnsubscribed()

    void markArtifactDataAvailable()

    boolean isArtifactSubscribed()

    boolean isArtifactDataAvailable()
}
