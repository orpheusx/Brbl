package com.enoughisasgoodasafeast;

import java.net.http.HttpClient;
import java.time.Duration;

public class Sndr {

    private HttpClient client;

    public void init() {
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(1))
                .build()) {

        }
    }
}
