package com.springboot.controller;

import com.springboot.service.GeminiService;
import com.springboot.service.MusicGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;
    private final MusicGenerationService musicService;

    @GetMapping("/chat")
    public String showChatForm() {
        return "chat_form";
    }

    @GetMapping("/create-poem-music")
    public String createPoemMusic(@RequestParam(value = "poem") String poem, Model model) {

        // 1. Gemini 프롬프트
        String promptRequest = poem + "\n\n" +
                "명령:\n" +
                "1. 위 문장의 감정을 바탕으로 짧고 감성적인 시를 작성해.\n" +
                "2. 마지막에는 음악 검색용 영어 키워드 2개를 쉼표(,)로 구분해서 출력해.\n\n" +

                "출력 형식:\n" +
                "[시]\n" +
                "(여기에 시 작성)\n\n" +
                "[키워드]\n" +
                "Sad, Calm\n\n" +

                "제약사항:\n" +
                "1. 키워드는 영어만 사용\n" +
                "2. 키워드는 반드시 마지막 줄에만 작성\n" +
                "3. 설명이나 부가 문장은 절대 출력하지 마\n" +
                "4. 검색이 잘 되도록 흔한 음악 감정 키워드 사용\n" +
                "5. 키워드는 정확히 2개만 출력";

        // 2. Gemini 응답
        String response = geminiService.getGeminiResponse(promptRequest);

        log.info("Gemini 전체 응답: {}", response);

        // 3. 시 / 키워드 분리
        String generatedPoem = "";
        String keywords = "";

        try {
            String[] parts = response.split("\\[키워드\\]");

            generatedPoem = parts[0]
                    .replace("[시]", "")
                    .trim();

            if (parts.length > 1) {
                keywords = parts[1].trim();
            }

        } catch (Exception e) {
            log.error("응답 파싱 실패", e);

            generatedPoem = response;
            keywords = "Calm Relaxing";
        }

        // 4. 검색용 키워드 정제
        String cleanStyle = keywords
                .replaceAll("\\r\\n|\\r|\\n|\\t", " ")
                .replace(".", "")
                .replace(",", " ")
                .trim();

        log.info("생성된 시: {}", generatedPoem);
        log.info("추출된 키워드: {}", keywords);
        log.info("정제된 검색 키워드: {}", cleanStyle);

        // 5. 음악 검색
        String trackId = musicService.generateMusic(cleanStyle);

        // 6. 오디오 URL 가져오기
        String audioUrl = null;

        if (trackId != null) {
            audioUrl = musicService.getAudioUrl(trackId);
        }

        // 7. View 전달
        model.addAttribute("originalPoem", poem);
        model.addAttribute("generatedPoem", generatedPoem);
        model.addAttribute("style", keywords);
        model.addAttribute("audioUrl", audioUrl);
        model.addAttribute("hasMusic", audioUrl != null && !audioUrl.isEmpty());

        return "music_result";
    }
}