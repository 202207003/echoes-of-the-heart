package com.springboot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.*;

@Service
public class GeminiService {

    private final String API_KEY = "AIzaSyDc-V_ReM6IVPy8H_ZkPnnv9cg_sDXtxl0";
 // 모델명 'gemini-2.5-flash'를 적용했습니다.
    private final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=" + API_KEY;

    public String getGeminiResponse(String message) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> requestBody = new HashMap<>();
        
        // 요청 데이터 구조 (Google API 규격)
        Map<String, Object> content = new HashMap<>();
        Map<String, String> part = new HashMap<>();
        
        part.put("text", message);
        content.put("parts", Collections.singletonList(part));
        requestBody.put("contents", Collections.singletonList(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(API_URL, entity, Map.class);
            Map<String, Object> body = response.getBody();
            
            // JSON 파싱 (candidates -> content -> parts -> text)
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) body.get("candidates");
            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> resContent = (Map<String, Object>) firstCandidate.get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) resContent.get("parts");
            
            return parts.get(0).get("text").toString();
        } catch (Exception e) {
            return "오류 발생: " + e.getMessage();
        }
    }
}