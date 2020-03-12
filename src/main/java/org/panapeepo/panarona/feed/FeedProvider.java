package org.panapeepo.panarona.feed;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.SyndFeedOutput;
import com.rometools.rome.io.XmlReader;
import org.jdom2.Document;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class FeedProvider {

    private SyndFeedInput feedInput;
    private SyndFeedOutput feedOutput;

    private Connection connection;

    private Map<String, SourcedFeed> lastFeeds = new HashMap<>();

    public FeedProvider() {
        this(new SyndFeedInput(), new SyndFeedOutput());
    }

    public FeedProvider(SyndFeedInput feedInput, SyndFeedOutput feedOutput) {
        this.feedInput = feedInput;
        this.feedOutput = feedOutput;
    }

    public void openDatabaseConnection(Connection connection) {
        try {
            this.connection = connection;

            try (PreparedStatement statement = this.connection.prepareStatement("CREATE TABLE IF NOT EXISTS `Feeds` (`url` TEXT, `value` TEXT, `creationTimestamp` DATE)")) {
                statement.executeUpdate();
            }

            this.loadLatestFeeds();
        } catch (SQLException | IOException exception) {
            exception.printStackTrace();
        }
    }

    private void loadLatestFeeds() throws SQLException, IOException {
        try (PreparedStatement statement = this.connection.prepareStatement("SELECT `url`, `value`, `creationTimestamp` FROM `Feeds` GROUP BY `url`, `value` ORDER BY `creationTimestamp` DESC LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String url = resultSet.getString("url");
                String feedXml = resultSet.getString("value");
                try (XmlReader reader = new XmlReader(new ByteArrayInputStream(feedXml.getBytes(StandardCharsets.UTF_8)))) {
                    SyndFeed feed = this.feedInput.build(reader);
                    this.lastFeeds.put(url, new SourcedFeed(url, feed));
                } catch (FeedException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public SourcedFeed readFeed(String url) {
        try (XmlReader reader = new XmlReader(new URL(url))) {
            SourcedFeed feed = new SourcedFeed(url, this.feedInput.build(reader));
            if (feed.getFeed() == null) {
                return null;
            }
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO `Feeds` (`url`, `value`, `creationTimestamp`) VALUES (?, ?, ?)")) {
                statement.setString(1, url);
                statement.setString(2, this.feedOutput.outputString(feed.getFeed()));
                statement.setDate(3, new Date(feed.getFeed().getPublishedDate().getTime()));
                statement.executeUpdate();
            }
            this.lastFeeds.put(url, feed);
            return feed;
        } catch (IOException | FeedException | SQLException exception) {
            exception.printStackTrace();
        }
        return null;
    }

    public SourcedFeed getLatestCachedFeed(String url) {
        return this.lastFeeds.get(url);
    }

}
