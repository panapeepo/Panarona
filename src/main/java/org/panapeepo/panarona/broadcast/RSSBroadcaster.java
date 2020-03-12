package org.panapeepo.panarona.broadcast;

import org.panapeepo.panarona.feed.SourcedFeed;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

public interface RSSBroadcaster extends Closeable {

    Collection<String> getFeedURLs();

    void init(Connection databaseConnection) throws SQLException;

    void addTarget(Connection databaseConnection, String targetUrl, String feedUrl) throws SQLException;

    void removeTarget(Connection databaseConnection, String targetUrl) throws SQLException;

    boolean isValidTarget(String targetUrl);

    void broadcastUpdate(SourcedFeed oldFeed, SourcedFeed newFeed);

}
