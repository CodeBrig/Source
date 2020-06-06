package com.sourceplusplus.api.model.artifact;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.sourceplusplus.api.model.SourceMessage;
import com.sourceplusplus.api.model.SourceStyle;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * Used to hold temporary status indicators for source artifacts.
 *
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 * @version 0.3.0
 * @since 0.3.0
 */
@SourceStyle
@Value.Immutable
@JsonSerialize(as = SourceArtifactStatus.class)
@JsonDeserialize(as = SourceArtifactStatus.class)
public interface AbstractSourceArtifactStatus extends SourceMessage {

    @Nullable
    Boolean activelyFailing();

    @Nullable
    String latestFailedServiceInstance();
}