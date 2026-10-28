package com.springboot.service;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PoemInputServiceTests {
    private final GeminiService gemini = mock(GeminiService.class);
    private final PoemInputService service = new PoemInputService(gemini);

    @Test
    void recognizesThreeMeaningsInOneUnpunctuatedLine() {
        when(gemini.getGeminiResponse(anyString()))
                .thenReturn("나는 오늘 지쳤어\n기분이 안좋아\n밥을 먹고싶어");
        assertEquals(List.of("나는 오늘 지쳤어", "기분이 안좋아", "밥을 먹고싶어"),
                service.splitByMeaning("나는 오늘 지쳤어 기분이 안좋아 밥을 먹고싶어"));
    }

    @Test
    void doesNotTreatEachWordAsASentence() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("오늘은 정말 많이 지쳤어");
        assertEquals(List.of("오늘은 정말 많이 지쳤어"), service.splitByMeaning("오늘은 정말 많이 지쳤어"));
    }

    @Test
    void permitsLineBreakAndPunctuationDifferencesWithoutChangingTheWords() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("오늘은 지쳤어\n쉬고 싶어");
        assertEquals(List.of("오늘은 지쳤어", "쉬고 싶어"), service.splitByMeaning("오늘은 지쳤어.\r\n\r\n쉬고 싶어!"));
    }

    @Test
    void fallsBackWhenResponseFailsAddsInstructionsOrOmitsOriginalContent() {
        when(gemini.getGeminiResponse(anyString()))
                .thenReturn(null, "오류 발생: 연결 실패", "총 2문장입니다.\n오늘은 지쳤어\n쉬고 싶어", "오늘은 지쳤어");
        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(List.of("오늘은 지쳤어", "쉬고 싶어"), service.splitByMeaning("오늘은 지쳤어.\n쉬고 싶어!"));
        }
    }

    @Test
    void skipsAnalysisForEmptyInput() {
        assertEquals(List.of(), service.splitByMeaning(" \n\t"));
        assertEquals(List.of(), service.splitByMeaning(null));
        verifyNoInteractions(gemini);
    }
}
