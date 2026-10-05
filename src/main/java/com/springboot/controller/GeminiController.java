package com.springboot.controller;

import com.springboot.entity.Member;
import com.springboot.repository.MemberRepository;
import com.springboot.service.ChatHistoryService;
import com.springboot.service.GeminiService;
import com.springboot.service.MusicGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

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
    private final ChatHistoryService chatHistoryService;
    private final MemberRepository memberRepository;

    @GetMapping("/chat")
    public String showChatForm() {
        return "chat_form";
    }

    @GetMapping("/create-poem-music")

    public String createPoemMusic(@RequestParam(value = "poem") String poem, @RequestParam(value = "house") String house, @RequestParam(value = "tree") String tree, @RequestParam(value = "person") String person, Model model, HttpSession session) {
    	String mbti = (String) session.getAttribute("mbti");
    	
    	String houseText = "";
    	String treeText = "";
    	String personText = "";

    	if (house.equals("H1")) {
    		houseText = "외부 자극이나 타인과의 관계에서 다소 경계심이 있으며, 나만의 온전한 공간과 보호를 원함";
    	} else if (house.equals("H2")) {
    		houseText = "타인과의 소통과 교류에 열려 있으며, 정서적으로 밝고 개방적인 상태";
    	} else if (house.equals("H3")) {
    		houseText = "내면의 불안을 누르고 안정감과 단단한 지지 기반을 필요로 하는 상태";
    	}

    	if (tree.equals("T1")) {
    		treeText = "자아 존중감이 높고 내면의 중심이 잘 잡혀 있어 현실에 안정적으로 적응함";
    	} else if (tree.equals("T2")) {
    		treeText = "외부 환경에 민감하고 섬세하며, 현재 감정적 유연함이나 조심스러운 위로가 필요한 상태";
    	} else if (tree.equals("T3")) {
    		treeText = "무의식적 자아 에너지가 활발하며, 무언가를 표현하고 성장하려는 의욕이 높음";
    	}

    	if (person.equals("P1")) {
    		personText = "현재 피로감이 있거나 내면의 자아 성찰을 위해 조용한 휴식과 위로를 원하는 상태";
    	} else if (person.equals("P2")) {
    		personText = "억눌린 정서에서 벗어나 자유롭고 긍정적인 변화를 갈망하는 상태";
    	} else if (person.equals("P3")) {
    		personText = "신중하고 정돈된 상태로, 자신을 보호하면서 객관적인 시각을 유지함";
    	}
    	
    	//로그인해서 mbti가 있을경우
    	String promptRequest = poem + "\n\n";

    	if (mbti != null && !mbti.isEmpty()) {
    	    promptRequest +=
    	            "사용자의 MBTI: " + mbti + "\n" +
    	            "사용자의 MBTI를 참고하여 시와 음악의 분위기를 결정하는 참고 자료로만 사용해.\n\n";
    	}
    	//HTP 검사 결과
    	promptRequest +=
    	        "HTP 선택 결과:\n" +
    	        "집: " + houseText + "\n" +
    	        "나무: " + treeText + "\n" +
    	        "사람: " + personText + "\n\n";

    	//로그인 안했을경우
    	promptRequest +=
    	        "명령:\n" +
        		"1. 위 문장의 감정을 바탕으로 짧고 감성적인 시를 작성해. 4~6줄 이내로 작성해.\n" +
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