package com.springboot.controller;

import com.springboot.repository.MemberRepository;
import com.springboot.service.ChatHistoryService;
import com.springboot.service.EmotionImageService;
import com.springboot.service.GeminiService;
import com.springboot.service.MusicGenerationService;
import com.springboot.service.PoemInputService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GeminiControllerTests {

    static Stream<Arguments> poemInputs() {
        return Stream.of(
                Arguments.of("오늘은 마음이 편안해.", "오늘은 마음이 편안해.", 1),
                Arguments.of("오늘은 마음이 편안해. 내일도 그랬으면 좋겠어.", "오늘은 마음이 편안해.\n내일도 그랬으면 좋겠어.", 2),
                Arguments.of("오늘은 조금 지쳤어.\n잠시 쉬어가고 싶어.\n내일은 다시 걸어갈래.", "오늘은 조금 지쳤어.\n잠시 쉬어가고 싶어.\n내일은 다시 걸어갈래.", 3),
                Arguments.of("\r\n오늘은 조금 지쳤어.\r\n  \r\n\t\r\n잠시 쉬어가고 싶어.\r\n", "오늘은 조금 지쳤어.\n잠시 쉬어가고 싶어.", 2),
                Arguments.of("나는 오늘 지쳤어 기분이 안좋아 밥을 먹고싶어", "나는 오늘 지쳤어\n기분이 안좋아\n밥을 먹고싶어", 3),
                Arguments.of("오늘은 정말 많이 지쳤어", "오늘은 정말 많이 지쳤어", 1));
    }

    @ParameterizedTest
    @MethodSource("poemInputs")
    void requestsTwoPoemLinesPerMeaningUnit(String input, String analysis, int unitCount) {
        GeminiService gemini = mock(GeminiService.class);
        MusicGenerationService music = mock(MusicGenerationService.class);
        GeminiController controller = new GeminiController(
                gemini, mock(EmotionImageService.class), music,
                mock(ChatHistoryService.class), mock(MemberRepository.class), new PoemInputService(gemini));
        int expectedLines = unitCount * 2;
        String generatedPoem = String.join("\n", IntStream.rangeClosed(1, expectedLines)
                .mapToObj(i -> "마음을 담은 시 " + i).toList());
        when(gemini.getGeminiResponse(anyString()))
                .thenReturn(analysis, "[시]\n" + generatedPoem + "\n\n[키워드]\nCalm, Peaceful");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("mbti", "INFP");
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.createPoemMusic(input, "NONE", "NONE", "NONE", "calm", model, session);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(2)).getGeminiResponse(prompt.capture());
        String generationPrompt = prompt.getAllValues().get(1);
        assertTrue(generationPrompt.contains("시를 정확히 " + expectedLines + "줄로 작성해."));
        assertTrue(generationPrompt.contains("의미 기준으로 나눈 내용은 다음 " + unitCount + "개야:"));
        assertTrue(generationPrompt.contains(analysis));
        assertEquals("music_result", view);
        assertEquals(generatedPoem, model.get("generatedPoem"));
        assertEquals("Calm, Peaceful", model.get("style"));
        assertEquals(input, model.get("originalPoem"));
    }
}
