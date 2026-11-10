package com.springboot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MusicGenerationServiceTests {

    @ParameterizedTest(name = "{0}: repeats only after all compatible candidates are heard")
    @CsvSource({
            "Happy Upbeat,happy",
            "Sad Calm,sad",
            "Calm Peaceful,calm",
            "Calm Ambient,calm",
            "Sad Ambient,sad",
            "Powerful Dramatic,energetic",
            "Relaxing Calm,calm",
            "Uplifting Inspiring,happy"
    })
    void avoidsRepeatsForAllEightEmotionProfiles(String style, String tag) {
        String body = "{\"data\":[" + String.join(",", IntStream.range(0, 12)
                .mapToObj(i -> "{\"id\":\"track-" + i + "\",\"tags\":[\"" + tag + "\"]}")
                .toList()) + "]}";
        var service = serviceReturning(body);
        List<String> played = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String selected = service.generateMusic(style, played);
            assertNotNull(selected);
            assertFalse(played.contains(selected), "Must not repeat while an unheard compatible track exists");
            played.add(selected);
        }
        assertEquals(played.getFirst(), service.generateMusic(style, played));
    }

    @Test
    void searchesCoreMoodOnFirstRecommendationEvenWhenNarrowSearchAlreadyMatches() {
        List<String> queries = new ArrayList<>();
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            String query = request.url().getQuery();
            queries.add(query);
            String body = query.contains("query=Happy&")
                    ? "{\"data\":[{\"id\":\"another-happy\",\"tags\":[\"positive\"]}]}"
                    : "{\"data\":[{\"id\":\"coconut\",\"tags\":[\"happy\",\"uplifting\"]}]}";
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body).build());
        }));
        assertTrue(List.of("coconut", "another-happy")
                .contains(service.generateMusic("Happy Uplifting Acoustic", List.of())));
        assertEquals(2, queries.size());
        assertTrue(queries.get(1).contains("query=Happy&"));
    }

    @Test
    void happySynonymsDoNotAccumulateExtraScoreForTheSameEmotion() throws Exception {
        var service = serviceReturning("{\"data\":[]}");
        var json = new ObjectMapper();
        var manyAliases = json.readTree("{\"tags\":[\"happy\",\"uplifting\",\"upbeat\"]}");
        var positive = json.readTree("{\"tags\":[\"positive\"]}");
        assertEquals(service.scoreTrack(positive, "Happy Uplifting Upbeat"),
                service.scoreTrack(manyAliases, "Happy Uplifting Upbeat"));
        assertTrue(service.scoreTrack(positive, "Happy Uplifting Upbeat") >= 0);
    }

    @Test
    void stillPrefersRequestedEnergyAndGenreOverUnrelatedHappyTracks() throws Exception {
        var service = serviceReturning("{\"data\":[]}");
        var json = new ObjectMapper();
        var energetic = json.readTree("{\"tags\":[\"happy\",\"energetic\"],\"genre\":\"Acoustic\"}");
        var happyOnly = json.readTree("{\"tags\":[\"happy\",\"upbeat\",\"uplifting\"]}");
        assertTrue(service.scoreTrack(energetic, "Happy Energetic Acoustic")
                > service.scoreTrack(happyOnly, "Happy Energetic Acoustic"));
    }

    @Test
    void actualProviderJoyAndBrightTagsQualifyAsHappyInsteadOfBeingDiscarded() {
        var service = serviceReturning("""
                {"data":[{"id":"bright","categories":[[0,{"name":"Tropical"}]],"tags":[[1,"bright"]]},
                         {"id":"joy","tags":[[1,"joy"]]}]}
                """);
        assertEquals("joy", service.generateMusic("Happy Upbeat", List.of("bright")));
    }

    private MusicGenerationService serviceReturning(String body) {
        return new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            assertTrue(request.url().getQuery().contains("limit=50"));
            assertTrue(request.url().getQuery().contains("offset=0"));
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

    @Test
    void unplayedCompatibleTrackWinsOverPreviouslyPlayedHighestScore() {
        var service = serviceReturning("""
                {"data":[{"id":"highest","tags":["sad","calm"]},
                         {"id":"unplayed","tags":["sad"]}]}
                """);
        assertEquals("unplayed", service.generateMusic("Sad Calm", List.of("highest")));
    }

    @Test
    void broadensSearchBeforeRepeatingThreeDetailedMatches() {
        List<String> queries = new ArrayList<>();
        String detailed = "{\"data\":[" + String.join(",", IntStream.range(0, 3)
                .mapToObj(i -> "{\"id\":\"sad-" + i + "\",\"tags\":[\"sad\",\"calm\"]}").toList()) + "]}";
        String broad = "{\"data\":[" + String.join(",", IntStream.range(0, 12)
                .mapToObj(i -> "{\"id\":\"sad-" + i + "\",\"tags\":[\"sad\"]}").toList()) + "]}";
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            String query = request.url().getQuery();
            queries.add(query);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(query.contains("query=Sad&") ? broad : detailed).build());
        }));
        List<String> played = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String selected = service.generateMusic("Sad Calm", played);
            assertNotNull(selected);
            assertFalse(played.contains(selected), "Repeated before exhausting broader sad matches");
            played.add(selected);
        }
        assertTrue(queries.stream().anyMatch(query -> query.contains("query=Sad&")));
    }

    @Test
    void readsNextPageInsteadOfRepeatingExhaustedFirstPage() {
        List<String> requests = new ArrayList<>();
        List<String> oldIds = IntStream.range(0, 50).mapToObj(i -> "old-" + i).toList();
        String firstPage = "{\"pagination\":{\"count\":51},\"data\":[" + String.join(",", oldIds.stream()
                .map(id -> "{\"id\":\"" + id + "\",\"tags\":[\"sad\"]}").toList()) + "]}";
        String secondPage = "{\"pagination\":{\"count\":51},\"data\":[{\"id\":\"next-page\",\"tags\":[\"sad\"]}]}";
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            String query = request.url().getQuery();
            requests.add(query);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(query.contains("offset=50") ? secondPage : firstPage).build());
        }));
        assertEquals("next-page", service.generateMusic("Sad", oldIds));
        assertEquals(2, requests.size());
        assertTrue(requests.get(1).contains("offset=50"));
    }

    @Test
    void broaderSearchStillRequiresPrimarySadMood() {
        var service = serviceReturning("""
                {"data":[{"id":"calm-only","tags":["calm","peaceful"]},
                         {"id":"happy","tags":["happy","calm"]},
                         {"id":"sad","tags":["melancholic"]}]}
                """);
        assertEquals("sad", service.generateMusic("Sad Calm", List.of()));
    }

    @Test
    void repeatedProviderPageDoesNotCauseUnboundedPagination() {
        List<String> requests = new ArrayList<>();
        String page = "{\"data\":[" + String.join(",", IntStream.range(0, 50)
                .mapToObj(i -> "{\"id\":\"sad-" + i + "\",\"tags\":[\"sad\"]}").toList()) + "]}";
        var service = new MusicGenerationService(WebClient.builder().exchangeFunction(request -> {
            requests.add(request.url().getQuery());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(page).build());
        }));
        assertNotNull(service.generateMusic("Sad", List.of()));
        assertEquals(2, requests.size());
    }
}
