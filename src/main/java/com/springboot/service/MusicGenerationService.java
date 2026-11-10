package com.springboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class MusicGenerationService {

    private final WebClient webClient;
    private final String BASE_URL = "https://api.freetouse.com/v3";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int SEARCH_PAGE_SIZE = 50;
    private static final int MAX_SEARCH_PAGES = 3;
    private static final Map<String, Set<String>> MOODS = Map.of(
            "happy", Set.of("happy", "positive", "playful", "uplifting", "upbeat", "fun", "cheerful", "joyful", "joy", "bright", "lively"),
            "calm", Set.of("calm", "peaceful", "relaxing", "chill", "meditative", "soothing", "gentle", "quiet", "slow", "downtempo"),
            "sad", Set.of("sad", "melancholy", "melancholic", "lonely", "gloomy", "sorrowful", "sorrow", "somber", "sombre", "mournful"),
            "energetic", Set.of("energetic", "exciting", "hype", "powerful", "workout", "sports", "party", "fast"),
            "romantic", Set.of("romantic", "love", "tender"),
            "dramatic", Set.of("dramatic", "epic", "intense"),
            "mysterious", Set.of("mysterious", "mystery", "suspense"));
    private static final Map<String, String> OPPOSITES = Map.of(
            "happy", "sad", "sad", "happy", "calm", "energetic", "energetic", "calm");

    public MusicGenerationService(WebClient.Builder webClientBuilder) {
        // Config에서 설정한 10MB 버퍼 제한이 적용된 builder가 주입됩니다.
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    public String generateMusic(String stylePrompt, List<String> recentTrackIds) {
        String query = stylePrompt.trim().replaceAll("[\\s,]+", " ");
        if (query.isBlank()) return null;
        Set<String> searches = new LinkedHashSet<>();
        searches.add(query);
        String primaryMood = primaryMood(query);
        if (primaryMood != null) {
            String canonical = Character.toUpperCase(primaryMood.charAt(0)) + primaryMood.substring(1);
            if (!canonical.equalsIgnoreCase(query)) searches.add(canonical);
        }
        Map<String, Integer> allCandidates = new LinkedHashMap<>();
        for (String search : searches) {
            searchMusic(search, query).forEach((id, score) -> allCandidates.merge(id, score, Math::max));
        }
        // Include core-mood matches from the first recommendation, even when a
        // narrow multi-keyword search returned a single high-ranking track.
        String trackId = chooseUnplayedTrack(allCandidates, recentTrackIds);
        if (trackId != null) return trackId;
        log.info("음악 후보 {}곡이 모두 최근 추천에 포함되어 가장 오래된 곡을 다시 추천합니다.", allCandidates.size());
        // Repeat only after broader searches also have no unheard matching tracks.
        return allCandidates.keySet().stream()
                .min(Comparator.comparingInt(recentTrackIds::indexOf)).orElse(null);
    }

    private String chooseUnplayedTrack(Map<String, Integer> candidates, List<String> recentTrackIds) {
        List<Map.Entry<String, Integer>> fresh = candidates.entrySet().stream()
                .filter(entry -> !recentTrackIds.contains(entry.getKey())).toList();
        if (fresh.isEmpty()) return null;
        int bestFreshScore = fresh.stream().mapToInt(Map.Entry::getValue).max().orElse(0);
        List<String> bestFresh = fresh.stream()
                .filter(entry -> entry.getValue() >= Math.max(0, bestFreshScore - 2))
                .map(Map.Entry::getKey).toList();
        return bestFresh.get(ThreadLocalRandom.current().nextInt(bestFresh.size()));
    }

    private Map<String, Integer> searchMusic(String query, String requestedStyle) {
        Map<String, Integer> scoredTracks = new LinkedHashMap<>();
        Set<String> seenIds = new HashSet<>();
        for (int page = 0; page < MAX_SEARCH_PAGES; page++) {
            int offset = page * SEARCH_PAGE_SIZE;
            try {
                String response = webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/music/tracks/search")
                                .queryParam("query", query)
                                .queryParam("limit", SEARCH_PAGE_SIZE)
                                .queryParam("offset", offset).build())
                        .retrieve().bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(15)).block();
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                if (!data.isArray() || data.isEmpty()) break;
                boolean foundNewIds = false;
                for (JsonNode track : data) {
                    String id = track.path("id").asText("");
                    if (id.isBlank() || !seenIds.add(id)) continue;
                    foundNewIds = true;
                    int score = scoreTrack(track, requestedStyle);
                    if (score >= 0) scoredTracks.merge(id, score, Math::max);
                }
                int total = root.path("pagination").path("count").asInt(-1);
                if (!foundNewIds || data.size() < SEARCH_PAGE_SIZE
                        || (total >= 0 && offset + data.size() >= total)) break;
            } catch (Exception e) {
                log.warn("음악 검색 실패 - 검색어: {}, offset: {}", query, offset);
                break;
            }
        }
        log.info("음악 검색 - 검색어: {}, 조회 {}곡, 감정에 맞는 후보 {}곡", query, seenIds.size(), scoredTracks.size());
        return scoredTracks;
    }

    int scoreTrack(JsonNode track, String query) {
        Set<String> requested = new HashSet<>(Arrays.asList(query.toLowerCase(Locale.ROOT).split("[\\s,]+")));
        Set<String> metadata = new HashSet<>();
        collectMetadata(track.path("categories"), metadata);
        collectMetadata(track.path("tags"), metadata);
        collectMetadata(track.path("tags_categories"), metadata);
        collectMetadata(track.path("genre"), metadata);

        Set<String> requestedMoods = moodGroups(requested);
        Set<String> trackMoods = moodGroups(metadata);
        String primaryMood = primaryMood(query);
        if (primaryMood != null && !trackMoods.contains(primaryMood)) return -1;
        for (String mood : requestedMoods) {
            String opposite = OPPOSITES.get(mood);
            if (opposite != null && !requestedMoods.contains(opposite)
                    && trackMoods.contains(opposite) && !trackMoods.contains(mood)) {
                return -1;
            }
        }
        int score = 0;
        for (String mood : requestedMoods) {
            if (trackMoods.contains(mood)) score += 10;
        }
        if (!requestedMoods.isEmpty() && score == 0) return -1;
        for (String keyword : requested) {
            // Happy/Uplifting/Upbeat describe the same mood. Repeating synonyms
            // must not make one track outrank all other equally happy tracks.
            if (moodGroups(Set.of(keyword)).isEmpty() && metadata.contains(keyword)) score += 2;
        }
        return score;
    }

    private String primaryMood(String query) {
        for (String keyword : query.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
            Set<String> groups = moodGroups(Set.of(keyword));
            if (!groups.isEmpty()) return groups.iterator().next();
        }
        return null;
    }

    private Set<String> moodGroups(Set<String> words) {
        Set<String> groups = new HashSet<>();
        MOODS.forEach((group, aliases) -> {
            if (aliases.stream().anyMatch(words::contains)) groups.add(group);
        });
        return groups;
    }

    private void collectMetadata(JsonNode node, Set<String> words) {
        // 실제 API는 [순번, 값] 배열이며, 일반 문자열/객체 형식도 지원한다.
        if (node.isTextual()) {
            words.add(node.asText().trim().toLowerCase(Locale.ROOT));
        } else if (node.isArray()) {
            node.forEach(child -> collectMetadata(child, words));
        } else if (node.isObject()) {
            collectMetadata(node.path("name"), words);
        }
    }

    public String getAudioUrl(String trackId) {
        if (trackId == null || trackId.isEmpty()) return null;
       
        log.info("상세 정보 조회 시작 - ID: {}", trackId);
        try {
            String response = webClient.get()
                    .uri("/music/tracks/" + trackId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            String fileUrl = data.path("files").path("mp3").asText("");


            if (fileUrl.isEmpty()) {
                fileUrl = data.path("file_url").asText("");
            }

            log.info("최종 추출된 오디오 URL: [{}]", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("상세 조회 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
   
    public String getThumbnail(String trackId) {
        if (trackId == null || trackId.isEmpty()) return null;
       
        log.info("상세 정보 조회 시작 - ID: {}", trackId);
        try {
            String response = webClient.get()
                    .uri("/music/tracks/" + trackId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            String thumbnail = data.path("thumbnails").path("lg").asText("");
            log.info("썸네일 URL: {}", thumbnail);
            return thumbnail;
        } catch (Exception e) {
            return null;
        }
    }
    
    public String getTrackTitle(String trackId) {
        if (trackId == null || trackId.isEmpty()) return null;

        try {
            String response = webClient.get()
                    .uri("/music/tracks/" + trackId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            return data.path("title").asText("");

        } catch (Exception e) {
            log.error("곡 제목 조회 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
}
