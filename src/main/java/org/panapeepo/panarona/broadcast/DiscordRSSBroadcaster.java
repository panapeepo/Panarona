package org.panapeepo.panarona.broadcast;

import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessage;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndPerson;
import org.panapeepo.panarona.broadcast.discord.DiscordWebhook;
import org.panapeepo.panarona.feed.SourcedFeed;

import java.awt.*;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class DiscordRSSBroadcaster implements RSSBroadcaster {

    private static final String DISCORD_WEB_HOOK_URL_PREFIX = "https://canary.discordapp.com/api/webhooks/";

    private Collection<DiscordWebhook> webhooks = new ArrayList<>();

    @Override
    public Collection<String> getFeedURLs() {
        return this.webhooks.stream().map(DiscordWebhook::getFeedUrl).distinct().collect(Collectors.toList());
    }

    @Override
    public void init(Connection databaseConnection) throws SQLException {
        try (PreparedStatement statement = databaseConnection.prepareStatement("CREATE TABLE IF NOT EXISTS `discord_webhooks` (`url` TEXT, `feedUrl` TEXT)")) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = databaseConnection.prepareStatement("SELECT * FROM `discord_webhooks`");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String targetUrl = resultSet.getString("url");
                String feedUrl = resultSet.getString("feedUrl");

                this.webhooks.add(new DiscordWebhook(feedUrl, new WebhookClientBuilder(targetUrl).build()));
            }
        }
    }

    @Override
    public void addTarget(Connection databaseConnection, String targetUrl, String feedUrl) throws SQLException {
        try (PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO `discord_webhooks` (`url`, `feedUrl`) VALUES (?, ?)")) {
            statement.setString(1, targetUrl);
            statement.setString(2, feedUrl);
            statement.executeUpdate();
        }
    }

    @Override
    public void removeTarget(Connection databaseConnection, String targetUrl) throws SQLException {
        try (PreparedStatement statement = databaseConnection.prepareStatement("DELETE FROM `discord_webhooks` WHERE `url` = ?")) {
            statement.setString(1, targetUrl);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean isValidTarget(String targetUrl) {
        return targetUrl.startsWith(DISCORD_WEB_HOOK_URL_PREFIX);
    }

    @Override
    public void broadcastUpdate(SourcedFeed oldFeed, SourcedFeed newFeed) {
        this.webhooks.stream().filter(discordWebhook -> discordWebhook.getFeedUrl().equals(newFeed.getUrl())).forEach(discordWebhook -> {

            List<SyndEntry> entries;
            if (oldFeed == null) {
                entries = newFeed.getFeed().getEntries();
            } else {
                entries = new ArrayList<>();
                for (SyndEntry entry : newFeed.getFeed().getEntries()) {
                    if (oldFeed.getFeed().getEntries().stream().noneMatch(oldEntry -> oldEntry.getTitle().equals(entry.getTitle()) && oldEntry.getDescription().equals(entry.getDescription()))) {
                        entries.add(entry);
                    }
                }
            }

            if (entries.isEmpty()) {
                return;
            }

            entries.sort(Comparator.comparing(SyndEntry::getPublishedDate));

            this.sendEmbed(discordWebhook, new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle("Neue Benachrichtigungen: " + entries.size(), null))
                    .setColor(Color.GREEN.getRGB())
                    .setTimestamp(Instant.now())
                    .build()
            );

            for (SyndEntry entry : entries) {
                WebhookEmbedBuilder builder = new WebhookEmbedBuilder()
                        .setTitle(new WebhookEmbed.EmbedTitle(entry.getTitle(), entry.getLink()))
                        .setColor(Color.YELLOW.getRGB())
                        .setDescription(entry.getDescription().getValue());

                if (entry.getAuthor() != null) {
                    builder.setAuthor(new WebhookEmbed.EmbedAuthor(entry.getAuthor(), null, null));
                }
                if (newFeed.getFeed().getCopyright() != null) {
                    builder.setFooter(new WebhookEmbed.EmbedFooter(newFeed.getFeed().getCopyright(), null));
                }

                if (entry.getPublishedDate() != null) {
                    builder.setTimestamp(entry.getPublishedDate().toInstant());
                }

                this.sendEmbed(discordWebhook, builder.build());
            }
        });
    }

    private void sendEmbed(DiscordWebhook discordWebhook, WebhookEmbed embed) {
        try {
            discordWebhook.getWebhookClient().send(embed).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
    }
}
