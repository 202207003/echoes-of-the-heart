package com.springboot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MusicGenerationServiceTests {

    private MusicGenerationService serviceReturning(String body) {
        return new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            assertTrue(request.url().getQuery().contains("per_page=10"));
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build());
        }));
    }

    @Test
    void avoidsRecentTracksWhenAnotherMatchingTrackExists() {
        var service = serviceReturning("""
                {"data":[{"id":"old","tags":["happy"]},
                         {"id":"recent","tags":["happy"]},
                         {"id":"new","tags":["happy"]}]}
                """);
        assertEquals("new", service.generateMusic("Happy Upbeat", List.of("old", "recent")));
    }

    @Test
    void selectsOldestRecommendationWhenAllCandidatesAreRecent() {
        var service = serviceReturning("""
                {"data":[{"id":"recent","tags":["happy"]},{"id":"old","tags":["happy"]}]}
                """);
        assertEquals("old", service.generateMusic("Happy", List.of("old", "recent")));
    }

    @Test
    void canRepeatWhenOnlyOneMatchingTrackExists() {
        var service = serviceReturning("""
                {"data":[{"id":"only","tags":["happy"]}]}
                """);
        assertEquals("only", service.generateMusic("Happy", List.of("only")));
    }

    @Test
    void prefersMatchingMoodOverUnplayedTrackWithMatchingGenre() {
        var service = serviceReturning("""
                {"data":[
                  {"id":"happy","categories":[[0,{"name":"Happy"}]]},
                  {"id":"genre-only","genre":"Acoustic"},
                  {"id":"sad","genre":"Acoustic","categories":[[0,{"name":"Sad"}]]}
                ]}
                """);
        assertEquals("happy", service.generateMusic("Happy Acoustic", List.of("happy")));
    }

    @Test
    void understandsPositiveSynonymInApiPairArrays() {
        var service = serviceReturning("""
                {"data":[{"id":"positive","tags":[[0,"positive"],[1,"guitar"]]},
                         {"id":"unknown"}]}
                """);
        assertEquals("positive", service.generateMusic("Happy Uplifting", List.of()));
    }

    @Test
    void distinguishesQuietHappinessFromEnergeticHappiness() {
        var service = serviceReturning("""
                {"data":[
                  {"id":"quiet","categories":[{"name":"Happy"},{"name":"Calm"}]},
                  {"id":"loud","categories":[{"name":"Happy"},{"name":"Energetic"}]}
                ]}
                """);
        assertEquals("quiet", service.generateMusic("Happy Calm", List.of()));
        assertEquals("loud", service.generateMusic("Happy Energetic", List.of()));
    }

    @Test
    void doesNotRecommendOppositeMoodJustToReturnMusic() {
        var service = serviceReturning("""
                {"data":[{"id":"sad","tags":["melancholic"]}]}
                """);
        assertNull(service.generateMusic("Happy", List.of()));
    }

    @Test
    void doesNotTreatGenreAloneAsProofOfMood() {
        var service = serviceReturning("""
                {"data":[{"id":"guitar","genre":"Acoustic"},{"id":"unknown"}]}
                """);
        assertNull(service.generateMusic("Happy Acoustic", List.of()));
    }

    @Test
    void retriesWithCoreMoodWhenDetailedSearchHasNoResults() {
        List<String> queries = new ArrayList<>();
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            String query = request.url().getQuery();
            queries.add(query);
            String body = query.contains("query=Happy&")
                    ? "{\"data\":[{\"id\":\"happy-track\",\"tags\":[\"positive\"]}]}"
                    : "{\"data\":[]}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }));
        assertEquals("happy-track", service.generateMusic("Happy Romantic Acoustic", List.of()));
        assertEquals(2, queries.size());
        assertTrue(queries.get(1).contains("query=Happy&"));
    }

    @Test
    void retriesWhenFirstResultsHaveWrongMoodAndKeepsOriginalMoodOnRetry() {
        List<String> queries = new ArrayList<>();
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            queries.add(request.url().getQuery());
            String body = queries.size() == 1
                    ? "{\"data\":[{\"id\":\"sad\",\"tags\":[\"sad\"]}]}"
                    : "{\"data\":[{\"id\":\"sad\",\"tags\":[\"sad\"]},{\"id\":\"happy\",\"tags\":[\"happy\"]}]}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }));
        assertEquals("happy", service.generateMusic("Uplifting Acoustic", List.of()));
        assertEquals(2, queries.size());
    }

    @Test
    void acceptsMatchingMoodEvenWhenProviderAlsoListsContrastingTags() {
        var service = serviceReturning("""
                {"data":[{"id":"mixed","tags":["happy","calm","energetic"]}]}
                """);
        assertEquals("mixed", service.generateMusic("Happy Calm", List.of()));
    }

    @Test
    void returnsNoTrackForEmptyOrMissingIds() {
        assertNull(serviceReturning("{\"data\":[]}").generateMusic("Happy", List.of()));
        assertNull(serviceReturning("{\"data\":[{}]}").generateMusic("Happy", List.of()));
    }
}
