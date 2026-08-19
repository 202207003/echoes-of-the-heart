package com.springboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class MusicGenerationService {

    private final WebClient webClient;
    private final String BASE_URL = "https://api.freetouse.com/v3";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MusicGenerationService(WebClient.Builder webClientBuilder) {
        // Config에서 설정한 10MB 버퍼 제한이 적용된 builder가 주입됩니다.
        this.webClient = webClientBuilder.baseUrl(BASE_URL).build();
    }

    public String generateMusic(String stylePrompt) {
        // 검색어에서 불필요한 앞뒤 공백 제거
        String query = stylePrompt.trim();
        log.info("Free To Use 음악 검색 시작: {}", query);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/music/tracks/search")
                            .queryParam("query", query)
                            .queryParam("per_page", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray() && data.size() > 0) {
                JsonNode firstTrack = data.get(0);
                String trackId = firstTrack.path("id").asText();
                String title = firstTrack.path("title").asText(""); // 디버깅용 제목 추출

                log.info("검색 성공 - 곡 제목: [{}], Track ID: {}", title, trackId);
                return trackId;
            } else {
                log.warn("검색 결과가 없습니다: {}", query);
            }
        } catch (Exception e) {
            log.error("검색 중 오류 발생: {}", e.getMessage());
        }
        return null;
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
}