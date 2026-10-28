package com.springboot.controller;

import com.springboot.entity.Member;
import com.springboot.repository.MemberRepository;
import com.springboot.service.ChatHistoryService;
import com.springboot.service.GeminiService;
import com.springboot.service.EmotionImageService;
import com.springboot.service.MusicGenerationService;
import com.springboot.service.PoemInputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

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
    private final EmotionImageService emotionImageService;
    private final MusicGenerationService musicService;
    private final ChatHistoryService chatHistoryService;
    private final MemberRepository memberRepository;
    private final PoemInputService poemInputService;

    @GetMapping("/chat")
    public String showChatForm() {
        return "chat_form";
    }

    @GetMapping("/create-poem-music")

    public String createPoemMusic(@RequestParam(value = "poem") String poem, @RequestParam(value = "house") String house, @RequestParam(value = "tree") String tree, @RequestParam(value = "person") String person, @RequestParam(value = "mood", defaultValue = "calm") String mood, Model model, HttpSession session) {
    	String mbti = (String) session.getAttribute("mbti");
        List<String> meaningUnits = poemInputService.splitByMeaning(poem);
        int poemLineCount = Math.max(1, meaningUnits.size()) * 2;
    	
        String houseText = emotionImageService.describe(mood, house, "house");
        String treeText = emotionImageService.describe(mood, tree, "tree");
        String personText = emotionImageService.describe(mood, person, "person");
    	//로그인해서 mbti가 있을경우
    	String promptRequest = poem + "\n\n";

    	if (mbti != null && !mbti.isEmpty()) {
    	    promptRequest +=
    	            "사용자의 MBTI: " + mbti + "\n" +
    	            "사용자의 MBTI를 참고하여 시와 음악의 분위기를 결정하는 참고 자료로만 사용해.\n\n";
    	}
	// 이미지 선택은 분위기 참고로만 사용
    	promptRequest +=
	        "분위기 참고용 이미지 선택:\n" +
                "집: " + houseText + "\n" +
                "나무: " + treeText + "\n" +
	        "사람: " + personText + "\n\n" +
                "반영 원칙:\n" +
                "사용자가 입력한 문장에 드러난 감정과 맥락을 가장 우선하여 시와 음악 키워드를 정해.\n" +
                "이미지와 MBTI는 문장의 감정과 어울리는 경우에만 보조적인 분위기 참고로 사용해.\n" +
                "이미지 분위기와 문장의 감정이 다르면 문장의 감정을 따라.\n" +
                "이미지 선택으로 사용자의 성격, 자존감, 불안 또는 심리 상태를 단정하거나 진단하지 마.\n" +
                "선택 없는 항목은 무시하고, 모두 선택 없으면 입력한 문장만으로 작성해.\n\n";

    	//로그인 안했을경우
    	promptRequest +=
                "명령:\n" +
                "1. 위 문장의 감정을 바탕으로 감성적인 시를 정확히 " + poemLineCount + "줄로 작성해.\n" +
                "사용자 원문을 의미 기준으로 나눈 내용은 다음 " + meaningUnits.size() + "개야:\n" +
                String.join("\n", meaningUnits) + "\n" +
                "위 각 의미 단위의 감정과 맥락을 순서대로 시 2줄씩으로 표현해.\n" +
                "원문의 줄바꿈이나 단어 수로 시의 길이를 바꾸지 마. 이미지 설명과 MBTI는 의미 단위 수에 포함하지 마.\n" +
                "시의 각 행은 줄바꿈으로 구분하고, 제목, 행 번호, 빈 줄 없이 작성해. [시]와 [키워드] 태그 및 음악 키워드는 시의 줄 수에서 제외해.\n" +
                "2. 마지막에는 음악 검색용 영어 키워드를 쉼표(,)로 구분해서 출력해.\n\n" +
                "출력 형식:\n" +
                "[시]\n" +
                "(여기에 시 작성)\n\n" +
                "[키워드]\n" +
                "Sad, Calm\n\n" +
                "제약사항:\n" +
                "1. 키워드는 반드시 아래 [전체 허용 목록] 중에서만 2~3개 골라 쉼표(,)로 구분해서 작성해:\n" +
                "   [Trap, Tropical, EDM, House, Funky, Retro, Modern, Lofi, Cinematic, Acoustic, " +
                "Ambient, Electronic, Corporate, Hip Hop, Jazz, Pop, Rock, Classical, Dance, " +
                "Phonk, Calm, Cool, Sad, Uplifting, Exciting, Hype, Fun, Romantic, Chill, " +
                "Relaxing, Upbeat, Happy, Inspiring, Emotional, Energetic, Powerful, Dramatic, " +
                "Epic, Peaceful, Aesthetic, Mysterious, Vocal, Drone, Party, Halloween, Sports, " +
                "Workout, Kids, Adventure, Wedding, Movie, Travel, Nature, Vlog, Summer, " +
                "Night, Meditative, Cooking, Advertising, Timelapse, Gaming, Technology, Action, " +
                "Morning, Medieval, Christmas, How To, Intro]\n" +
                "2. 위 목록에 없는 단어(예: Melancholy 등)는 절대로 출력하지 마.\n" +
                "3. 키워드는 반드시 마지막 [키워드] 태그 아래에만 작성해.\n" +
                "4. 설명이나 부가 문장은 절대 출력하지 마.";
    	
        //Gemini 응답
        String response = geminiService.getGeminiResponse(promptRequest);
        
        System.out.println(promptRequest);
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
        // 오래된 곡부터 최대 5곡을 세션에 보관하여 사용자별 반복 추천을 줄인다.
        List<String> recentTrackIds = new ArrayList<>();
        if (session.getAttribute("recentMusicTrackIds") instanceof List<?> savedTracks) {
            for (Object savedTrack : savedTracks) {
                if (savedTrack instanceof String id) recentTrackIds.add(id);
            }
        }
        String trackId = musicService.generateMusic(cleanStyle, recentTrackIds);
        if (trackId != null) {
            recentTrackIds.remove(trackId);
            recentTrackIds.add(trackId);
            while (recentTrackIds.size() > 5) recentTrackIds.remove(0);
            session.setAttribute("recentMusicTrackIds", recentTrackIds);
        }

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
        
        String username = (String) session.getAttribute("username");

        if (username != null) {
            Optional<Member> member = memberRepository.findByUsername(username);

            if (member.isPresent()) {
                chatHistoryService.save(
                        member.get(),
                        poem,
                        generatedPoem,
                        trackTitle,
                        audioUrl
                );
            }
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
