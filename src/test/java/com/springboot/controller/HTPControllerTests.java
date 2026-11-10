package com.springboot.controller;

import com.springboot.service.EmotionImageService;
import com.springboot.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HTPControllerTests {
    @Test
    void repeatedAngryInputKeepsClassificationButRotatesActualPreviewChoices() {
        var gemini = mock(GeminiService.class);
        when(gemini.getGeminiResponse(anyString())).thenReturn("angry");
        var images = new EmotionImageService(gemini);
        var controller = new HTPController(images);
        var session = new MockHttpSession();
        Set<Object> combinations = new HashSet<>();
        for (int visit = 0; visit < 3; visit++) {
            var model = new ExtendedModelMap();
            assertEquals("emotion-analysis", controller.emotionAnalysis("화가 나요", null, model, session));
            assertEquals("angry", ((EmotionImageService.Mood) model.get("imageMood")).id());
            assertEquals(images.categories(images.profile("angry")), model.get("imageCategories"));
            assertTrue(combinations.add(model.get("imagePreviews")));
        }
        verify(gemini, times(1)).getGeminiResponse(anyString());
    }

    @Test
    void explicitMoodChangeUsesOnlyThatMoodsPictures() {
        var gemini = mock(GeminiService.class);
        var images = new EmotionImageService(gemini);
        var controller = new HTPController(images);
        var session = new MockHttpSession();
        for (var mood : images.moods()) {
            var model = new ExtendedModelMap();
            controller.emotionAnalysis("오늘의 마음", mood.id(), model, session);
            assertEquals(mood, model.get("imageMood"));
            assertNotNull(model.get("imagePreviews"));
        }
        verifyNoInteractions(gemini);
    }
}
