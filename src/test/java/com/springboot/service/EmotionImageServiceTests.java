package com.springboot.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmotionImageServiceTests {
    private final GeminiService gemini = mock(GeminiService.class);
    private final EmotionImageService service = new EmotionImageService(gemini);

    @Test
    void everyEmotionRotatesAllThreeChoicesWithoutImmediateRepeats() {
        var session = new MockHttpSession();
        for (var mood : service.moods()) {
            Map<String, String> previous = new HashMap<>();
            for (int cycle = 0; cycle < 12; cycle++) {
                Map<String, Set<String>> seen = new HashMap<>();
                for (int visit = 0; visit < 3; visit++) {
                    var previews = service.recommendations(mood, session);
                    assertEquals(3, previews.size());
                    for (var preview : previews) {
                        assertNotEquals(previous.put(preview.categoryId(), preview.choice().id()), preview.choice().id());
                        assertTrue(seen.computeIfAbsent(preview.categoryId(), ignored -> new HashSet<>()).add(preview.choice().id()));
                        assertTrue(preview.choice().imageStyle().contains("/" + mood.id() + ".png"));
                    }
                }
                assertTrue(seen.values().stream().allMatch(choices -> choices.size() == 3));
            }
        }
    }

    @Test
    void recommendationsDoNotChangeManualChoiceOrderAndUseSessionLocalHistory() {
        var mood = service.profile("angry");
        var firstSession = new MockHttpSession();
        var secondSession = new MockHttpSession();
        var categories = service.categories(mood);
        service.recommendations(mood, firstSession);
        service.recommendations(mood, secondSession);
        assertNotSame(firstSession.getAttribute("emotionPreviewHistory"), secondSession.getAttribute("emotionPreviewHistory"));
        assertEquals(categories, service.categories(mood));
        assertEquals("H1", categories.getFirst().choices().getFirst().id());
    }

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
