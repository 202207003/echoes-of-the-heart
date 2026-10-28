package com.springboot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.springboot.service.EmotionImageService;
import jakarta.servlet.http.HttpSession;

@Controller
public class HTPController {

    private final EmotionImageService emotionImageService;

    public HTPController(EmotionImageService emotionImageService) {
        this.emotionImageService = emotionImageService;
    }

    private record CachedMood(String poem, String mood) {}
	
	@GetMapping
	("/emotion-analysis")
	public String emotionAnalysis(@RequestParam("poem") String poem,
            @RequestParam(value = "mood", required = false) String mood, Model model, HttpSession session) {

        EmotionImageService.Mood profile;
        if (emotionImageService.isValidMood(mood)) {
            profile = emotionImageService.profile(mood);
        } else if (session.getAttribute("imageMood") instanceof CachedMood cached && cached.poem().equals(poem)) {
            profile = emotionImageService.profile(cached.mood());
        } else {
            profile = emotionImageService.classify(poem);
            session.setAttribute("imageMood", new CachedMood(poem, profile.id()));
        }
	    model.addAttribute("poem", poem);
        model.addAttribute("imageMood", profile);
        model.addAttribute("imageMoods", emotionImageService.moods());
        model.addAttribute("imageCategories", emotionImageService.categories(profile));

	    return "emotion-analysis";
	}
}
