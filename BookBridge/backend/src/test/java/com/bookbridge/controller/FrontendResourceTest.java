package com.bookbridge.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfSystemProperty(named = "bookbridge.mongo-tests", matches = "true")
class FrontendResourceTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesFrontendAndAssetsWithoutAuthentication() {
        ResponseEntity<String> page = restTemplate.getForEntity(baseUrl() + "/", String.class);
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML));
        assertTrue(page.getBody().contains("BookBridge | Community Library"));

        ResponseEntity<String> script = restTemplate.getForEntity(baseUrl() + "/app.js", String.class);
        assertEquals(HttpStatus.OK, script.getStatusCode());
        assertTrue(script.getHeaders().getContentType().toString().contains("javascript"));
        assertTrue(script.getBody().contains("register"));

        ResponseEntity<String> styles = restTemplate.getForEntity(baseUrl() + "/styles.css", String.class);
        assertEquals(HttpStatus.OK, styles.getStatusCode());
        assertTrue(styles.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("text/css")));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
