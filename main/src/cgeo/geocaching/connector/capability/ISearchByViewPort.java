package cgeo.geocaching.connector.capability;

import cgeo.geocaching.SearchResult;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.connector.gc.MapTokens;
import cgeo.geocaching.location.Viewport;

import org.eclipse.jdt.annotation.NonNull;

public interface ISearchByViewPort extends IConnector {
    @NonNull
    SearchResult searchByViewport(@NonNull final Viewport viewport, @NonNull final MapTokens tokens);
}
