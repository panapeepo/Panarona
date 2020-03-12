package org.panapeepo.panarona;

import org.panapeepo.panarona.broadcast.DiscordRSSBroadcaster;
import org.panapeepo.panarona.broadcast.RSSBroadcaster;
import org.panapeepo.panarona.feed.FeedProvider;
import org.panapeepo.panarona.feed.SourcedFeed;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Panarona {

    private static final long FEED_CHECK_INTERVAL_MS = 300_000;

    private FeedProvider feedProvider;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    private final Collection<RSSBroadcaster> broadcasters = Collections.unmodifiableCollection(Collections.singletonList(
            new DiscordRSSBroadcaster()
    ));

    private final Connection databaseConnection;

    Panarona() throws SQLException {
        this.databaseConnection = DriverManager.getConnection("jdbc:h2:" + new File("database").getAbsolutePath());

        this.feedProvider = new FeedProvider();
        this.feedProvider.openDatabaseConnection(this.databaseConnection);

        for (RSSBroadcaster broadcaster : this.broadcasters) {
            broadcaster.init(this.databaseConnection);
        }

        this.startFeedChecker();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Shutdown Thread"));
    }

    private void startFeedChecker() {
        this.executorService.execute(() -> {
            while (!Thread.interrupted()) {
                for (RSSBroadcaster broadcaster : this.broadcasters) {
                    broadcaster.getFeedURLs().stream().distinct()
                            .forEach(feedURL -> {
                                SourcedFeed oldFeed = this.feedProvider.getLatestCachedFeed(feedURL);
                                SourcedFeed newFeed = this.feedProvider.readFeed(feedURL);
                                broadcaster.broadcastUpdate(oldFeed, newFeed);
                            });
                }
                try {
                    Thread.sleep(FEED_CHECK_INTERVAL_MS);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        });
    }

    public void shutdown() {
        this.executorService.shutdownNow();
        for (RSSBroadcaster broadcaster : this.broadcasters) {
            try {
                broadcaster.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        try {
            this.databaseConnection.close();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }

        if (!Thread.currentThread().getName().equals("Shutdown Thread")) {
            System.exit(0);
        }
    }

    public FeedProvider getFeedProvider() {
        return this.feedProvider;
    }

}
