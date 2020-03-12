package org.panapeepo.panarona.broadcast.discord;

import club.minnced.discord.webhook.WebhookClient;

public class DiscordWebhook {

    private String feedUrl;
    private WebhookClient webhookClient;

    public DiscordWebhook(String feedUrl, WebhookClient webhookClient) {
        this.feedUrl = feedUrl;
        this.webhookClient = webhookClient;
    }

    public String getFeedUrl() {
        return this.feedUrl;
    }

    public WebhookClient getWebhookClient() {
        return this.webhookClient;
    }
}
