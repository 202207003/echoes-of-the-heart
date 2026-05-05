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

    /**
     * 1. 음악 검색 후 가장 적합한 트랙의 ID를 반환합니다.
     */
    public String generateMusic(String stylePrompt) {
        String query = stylePrompt;
        log.info("Free To Use 음악 검색 시작: {}", query);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/music/tracks/search")
                            .queryParam("q", query)
                            .queryParam("per_page", 1) // 가장 유사한 1개만 요청
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray() && data.size() > 0) {
                String trackId = data.get(0).path("id").asText();
                log.info("검색 성공 - Track ID: {}", trackId);
                return trackId;
            }
        } catch (Exception e) {
            log.error("검색 중 오류 발생: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 2. 트랙 ID를 사용하여 실제 스트리밍 가능한 오디오 URL을 가져옵니다.
     */
    public String getAudioUrl(String trackId) {
        if (trackId == null) return null;
        
        log.info("상세 정보 조회 시작 - ID: {}", trackId);
        try {
            String response = webClient.get()
                    .uri("/music/tracks/" + trackId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            // ✅ 수정된 경로: data -> files -> mp3
            String fileUrl = data.path("files").path("mp3").asText("");
            
            // 만약 mp3 필드가 비어있을 경우를 대비한 방어 로직 (선택 사항)
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
}