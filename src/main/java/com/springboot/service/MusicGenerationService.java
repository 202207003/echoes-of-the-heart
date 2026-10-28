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
    private static final Map<String, Set<String>> MOODS = Map.of(
            "happy", Set.of("happy", "positive", "playful", "uplifting", "upbeat", "fun", "cheerful", "joyful"),
            "calm", Set.of("calm", "peaceful", "relaxing", "chill", "meditative", "soothing", "gentle", "quiet", "slow", "downtempo"),
            "sad", Set.of("sad", "melancholy", "melancholic", "lonely", "gloomy", "sorrowful"),
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
        // 세부 키워드 조합이 너무 좁으면 핵심 감정으로 넓혀 검색한다.
        for (String keyword : query.toLowerCase(Locale.ROOT).split(" ")) {
            for (String mood : moodGroups(Set.of(keyword))) {
                String canonical = Character.toUpperCase(mood.charAt(0)) + mood.substring(1);
                if (!canonical.equalsIgnoreCase(query)) searches.add(canonical);
            }
            if (searches.size() >= 3) break;
        }
        for (String search : searches) {
            String trackId = searchMusic(search, query, recentTrackIds);
            if (trackId != null) return trackId;
        }
        return null;
    }

    private String searchMusic(String query, String requestedStyle, List<String> recentTrackIds) {
        log.info("Free To Use 음악 검색 시작: {}", query);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/music/tracks/search")
                            .queryParam("query", query)
                            .queryParam("per_page", 10)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray() && data.size() > 0) {
                Map<String, Integer> scoredTracks = new LinkedHashMap<>();
                for (JsonNode track : data) {
                    String id = track.path("id").asText("");
                    // 재검색에서도 원래 요청의 감정 기준을 유지한다.
                    int score = scoreTrack(track, requestedStyle);
                    if (!id.isBlank() && score >= 0) {
                        scoredTracks.merge(id, score, Math::max);
                    }
                }
                if (scoredTracks.isEmpty()) {
                    log.info("감정 조건에 맞는 후보 없음, 핵심 감정 재검색 시도: {}", query);
                    return null;
                }
                int bestScore = scoredTracks.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                // 감정 일치(10점)가 우선이며 장르 등 세부 선호(2점) 차이는 허용한다.
                List<String> candidates = scoredTracks.entrySet().stream()
                        .filter(entry -> entry.getValue() >= Math.max(0, bestScore - 2))
                        .map(Map.Entry::getKey).toList();

                List<String> freshCandidates = candidates.stream()
                        .filter(id -> !recentTrackIds.contains(id))
                        .toList();
                // 모두 최근 추천 곡이면 가장 오래전에 추천한 곡을 선택한다.
                String trackId = freshCandidates.isEmpty()
                        ? candidates.stream().min(Comparator.comparingInt(recentTrackIds::indexOf)).orElseThrow()
                        : freshCandidates.get(ThreadLocalRandom.current().nextInt(freshCandidates.size()));

                log.info("음악 선택 - 검색 {}곡, 적합 후보 {}곡, 점수 {}, Track ID: {}",
                        data.size(), candidates.size(), scoredTracks.get(trackId), trackId);
                return trackId;
            } else {
                log.warn("검색 결과가 없습니다: {}", query);
            }
        } catch (Exception e) {
            log.error("검색 중 오류 발생: {}", e.getMessage());
        }
        return null;
    }

    private int scoreTrack(JsonNode track, String query) {
        Set<String> requested = new HashSet<>(Arrays.asList(query.toLowerCase(Locale.ROOT).split("[\\s,]+")));
        Set<String> metadata = new HashSet<>();
        collectMetadata(track.path("categories"), metadata);
        collectMetadata(track.path("tags"), metadata);
        collectMetadata(track.path("tags_categories"), metadata);
        collectMetadata(track.path("genre"), metadata);

        Set<String> requestedMoods = moodGroups(requested);
        Set<String> trackMoods = moodGroups(metadata);
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
            if (metadata.contains(keyword)) score += 2;
        }
        return score;
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
