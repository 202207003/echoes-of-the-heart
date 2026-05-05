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
        
    	String promptRequest = poem + " \n\n" +
    	        "명령: 위 문장의 감정을 분석해서 음악 검색용 영어 키워드만 딱 2개 출력해.\n" +
    	        "제약사항:\n" +
    	        "1. 한국어는 절대 포함하지 마.\n" +
    	        "2. 위로의 말이나 설명도 절대 하지 마.\n" +
    	        "\"분석된 감정에 어울리는 검색어를 만들되, Relaxing, Chill, Lofi, Piano 같은 너무 흔한 단어는 제외하고, 훨씬 구체적인 장르나 악기 위주로 2개만 뽑아줘. (예: Cinematic, Dark, Jazz, Guitar, Synth)\n" +
    	        "4. 이 명령을 어기면 안 돼.";
        
        String musicStyle = geminiService.getGeminiResponse(promptRequest);
        log.info("분석된 스타일: {}", musicStyle);
        
        // 2. MusicService: 트랙 검색 (ID 가져오기)
        String trackId = musicService.generateMusic(musicStyle);
        
        // 3. MusicService: 상세 URL 가져오기
        String audioUrl = null;
        if (trackId != null) {
            audioUrl = musicService.getAudioUrl(trackId);
        }

        // 4. 데이터 전달
        model.addAttribute("poem", poem);
        model.addAttribute("style", musicStyle);
        model.addAttribute("audioUrl", audioUrl);
        model.addAttribute("hasMusic", audioUrl != null && !audioUrl.isEmpty());

        return "music_result";
    }
}