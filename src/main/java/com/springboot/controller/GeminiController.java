package com.springboot.controller;

import com.springboot.service.GeminiService;
import com.springboot.service.MusicGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

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
    public String createPoemMusic(@RequestParam(value = "poem") String poem, Model model, HttpSession session) {
    	String mbti = (String) session.getAttribute("mbti");
    	
    	//로그인해서 mbti가 있을경우
    	String promptRequest = poem + "\n\n";

    	if (mbti != null && !mbti.isEmpty()) {
    	    promptRequest +=
    	            "사용자의 MBTI: " + mbti + "\n" +
    	            "사용자의 MBTI를 참고하여 시와 음악의 분위기를 결정하는 참고 자료로만 사용해.\n\n";
    	}
    	
    	//로그인 안했을경우
    	promptRequest +=
    	        "명령:\n" +
    	        "1. 위 문장의 감정을 바탕으로 짧고 감성적인 시를 작성해.\n" +
    	        "2. 마지막에는 음악 검색용 영어 키워드를 쉼표(,)로 구분해서 출력해.\n\n" +
    	        "출력 형식:\n" +
    	        "[시]\n" +
    	        "(여기에 시 작성)\n\n" +
    	        "[키워드]\n" +
    	        "Sad, Calm\n\n" +
    	        "제약사항:\n" +
    	        "1. 키워드는 영어만 사용\n" +
    	        "2. 키워드는 반드시 마지막 줄에만 작성\n" +
    	        "3. 설명이나 부가 문장은 절대 출력하지 마\n" +
    	        "4. 검색 가능한 음악이 많도록 매우 일반적이고 흔한 장르 및 분위기 키워드를 사용해\n " +
    	        "5. 지나치게 구체적인 키워드나 감정 표현은 사용하지 마.\n" +
    	        "6. 음악 검색에 적합한 영어 키워드를 2~3개만 사용해.\n" +
    	        "7. 장르(Genre), 분위기(Mood), 악기(Instrument) 중심으로 키워드를 작성해";

        //Gemini 응답
        String response = geminiService.getGeminiResponse(promptRequest);

        log.info("Gemini 전체 응답: {}", response);

        //시 / 키워드 분리
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

        // 검색용 키워드 정제
        String cleanStyle = keywords
                .replaceAll("\\r\\n|\\r|\\n|\\t", " ")
                .replace(".", "")
                .replace(",", " ")
                .trim();

        log.info("생성된 시: {}", generatedPoem);
        log.info("추출된 키워드: {}", keywords);
        log.info("정제된 검색 키워드: {}", cleanStyle);

        // 음악 검색
        String trackId = musicService.generateMusic(cleanStyle);

        // 곡 URL 가져오기
        String audioUrl = null;

        if (trackId != null) {
            audioUrl = musicService.getAudioUrl(trackId);
        }
        // 곡 제목 가지고 오기
        String trackTitle = null;

        if (trackId != null) {
            trackTitle = musicService.getTrackTitle(trackId);
        }

        // View 전달
        model.addAttribute("originalPoem", poem);
        model.addAttribute("generatedPoem", generatedPoem);
        model.addAttribute("style", keywords);
        model.addAttribute("audioUrl", audioUrl);
        model.addAttribute("hasMusic", audioUrl != null && !audioUrl.isEmpty());
        model.addAttribute("thumbnailUrl", musicService.getThumbnail(trackId));
        model.addAttribute("trackTitle", trackTitle);

        return "music_result";
    }
}