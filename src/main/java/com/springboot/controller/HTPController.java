package com.springboot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HTPController {
	
	@GetMapping
	("/emotion-analysis")
	public String emotionAnalysis(@RequestParam("poem") String poem,  Model model) {
	    model.addAttribute("poem", poem);

	    return "emotion-analysis";
	}
}
