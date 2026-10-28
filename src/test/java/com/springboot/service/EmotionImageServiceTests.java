package com.springboot.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmotionImageServiceTests {
    private final GeminiService gemini = mock(GeminiService.class);
    private final EmotionImageService service = new EmotionImageService(gemini);

    @Test
    void everyMoodHasThreeDistinctChoicesForEachCategory() {
        assertEquals(8, service.moods().size());
        for (var mood : service.moods()) {
            assertEquals(9, mood.captions().stream().distinct().count());
            var categories = service.categories(mood);
            assertEquals(3, categories.size());
            for (var category : categories) {
                assertEquals(3, category.choices().size());
                assertEquals(3, category.choices().stream().map(EmotionImageService.Choice::imageStyle).distinct().count());
                for (var choice : category.choices()) {
                    assertTrue(choice.imageStyle().contains("/images/emotions/" + mood.id() + ".png"));
                }
            }
        }
    }

    @Test
    void usesSemanticClassificationRatherThanMatchingNegativeWords() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("happy");
        assertEquals("happy", service.classify("슬프지 않고 오늘은 행복해").id());
    }

    @Test
    void fallsBackToCalmWhenClassifierFailsOrReturnsInvalidContent() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("오류 발생", "../invalid", null);
        assertEquals("calm", service.classify("복잡한 마음").id());
        assertEquals("calm", service.classify("모르겠어").id());
        assertEquals("calm", service.classify("그냥 그래").id());
    }

    @Test
    void musicPromptReflectsMoodAndSpecificChoice() {
        String sadHouse = service.describe("sad", "H1", "house");
        String happyHouse = service.describe("happy", "H1", "house");
        assertTrue(sadHouse.contains("비 내리는 작은 집"));
        assertTrue(sadHouse.contains("Sad"));
        assertTrue(happyHouse.contains("꽃이 핀 작은 집"));
        assertTrue(happyHouse.contains("Happy"));
        assertNotEquals(sadHouse, happyHouse);
        assertTrue(service.describe("sad", "NONE", "house").contains("선택 없음"));
        assertTrue(service.describe("sad", "P1", "house").contains("선택 없음"));
    }

    @Test
    void validatesUserSelectedMoodBeforeCreatingImagePaths() {
        assertFalse(service.isValidMood("../../secrets"));
        assertFalse(service.isValidMood(null));
        assertTrue(service.isValidMood("lonely"));
        assertEquals("calm", service.profile("../../secrets").id());
    }
}
