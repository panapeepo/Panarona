package org.panapeepo.panarona.feed;

import com.rometools.rome.feed.synd.SyndFeed;

public class SourcedFeed {

    private String url;
    private SyndFeed feed;

    public SourcedFeed(String url, SyndFeed feed) {
        this.url = url;
        this.feed = feed;
    }

    public String getUrl() {
        return this.url;
    }

    public SyndFeed getFeed() {
        return this.feed;
    }
}
