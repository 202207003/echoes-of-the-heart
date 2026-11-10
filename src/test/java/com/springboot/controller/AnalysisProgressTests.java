package com.springboot.controller;

import com.springboot.repository.MemberRepository;
import com.springboot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalysisProgressTests {
    private GeminiController controller;
    private GeminiService gemini;
    private PoemInputService input;
    private MusicGenerationService music;
    private GuestResultService guest;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiService.class);
        input = mock(PoemInputService.class);
        music = mock(MusicGenerationService.class);
        guest = mock(GuestResultService.class);
        controller = new GeminiController(gemini, mock(EmotionImageService.class), music,
                mock(ChatHistoryService.class), mock(MemberRepository.class), input, guest);
        session = new MockHttpSession();
        when(input.splitByMeaning(anyString())).thenReturn(List.of("오늘은 편안해"));
        when(gemini.getGeminiResponse(anyString())).thenReturn("[시]\n편안한 마음\n쉬어가는 하루\n[키워드]\nCalm, Peaceful");
    }

    private int percent() {
        return controller.analysisProgress("request-1", session).getBody().percent();
    }

    @Test
    void reportsActualStagesAndOnlyCompletesAfterSavingResult() {
        when(input.splitByMeaning(anyString())).thenAnswer(invocation -> {
            assertEquals(10, percent());
            return List.of("오늘은 편안해");
        });
        when(gemini.getGeminiResponse(anyString())).thenAnswer(invocation -> {
            assertEquals(35, percent());
            return "[시]\n편안한 마음\n쉬어가는 하루\n[키워드]\nCalm, Peaceful";
        });
        when(music.generateMusic(anyString(), anyList())).thenAnswer(invocation -> {
            assertEquals(65, percent());
            return null;
        });
        doAnswer(invocation -> {
            assertEquals(90, percent());
            return null;
        }).when(guest).remember(anyString(), anyString(), anyString(), nullable(String.class),
                nullable(String.class), same(session));

        var response = controller.createPoemMusicWithProgress("오늘은 편안해", "NONE", "NONE", "NONE",
                "calm", "request-1", new ExtendedModelMap(), session);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().resultId());
        assertEquals(100, percent());
        assertFalse(controller.analysisProgress("request-1", session).getBody().failed());
    }

    @Test
    void failedGenerationDoesNotReportCompletionAndCanBeRetried() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("오류 발생");
        var response = controller.createPoemMusicWithProgress("오늘", "NONE", "NONE", "NONE", "calm",
                "request-1", new ExtendedModelMap(), session);
        assertEquals(500, response.getStatusCode().value());
        assertNull(response.getBody().resultId());
        assertTrue(controller.analysisProgress("request-1", session).getBody().failed());
        assertTrue(percent() < 100);
        verifyNoInteractions(music, guest);
        when(gemini.getGeminiResponse(anyString())).thenReturn("[시]\n다시 쉬는 마음\n편안한 하루\n[키워드]\nCalm");
        var retry = controller.createPoemMusicWithProgress("오늘", "NONE", "NONE", "NONE", "calm",
                "request-2", new ExtendedModelMap(), session);
        assertEquals(200, retry.getStatusCode().value());
        assertEquals(100, controller.analysisProgress("request-2", session).getBody().percent());
    }

    @Test
    void unexpectedFailureIsReportedAndProgressDoesNotLeakAcrossSessionsOrRequests() {
        when(input.splitByMeaning(anyString())).thenThrow(new IllegalStateException("Unavailable"));
        var response = controller.createPoemMusicWithProgress("오늘", "NONE", "NONE", "NONE", "calm",
                "request-1", new ExtendedModelMap(), session);
        assertEquals(500, response.getStatusCode().value());
        assertTrue(controller.analysisProgress("request-1", session).getBody().failed());
        assertEquals(0, controller.analysisProgress("other-request", session).getBody().percent());
        assertFalse(controller.analysisProgress("request-1", new MockHttpSession()).getBody().failed());
    }

    @Test
    void httpEndpointsReturnJsonAndPreserveTheOriginalHtmlRoute() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/create-poem-music-progress").session(session)
                .param("poem", "오늘은 편안해").param("house", "NONE").param("tree", "NONE")
                .param("person", "NONE").param("progressId", "request-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultId").isNotEmpty());
        mvc.perform(get("/analysis-progress").session(session).param("progressId", "request-1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.percent").value(100)).andExpect(jsonPath("$.failed").value(false));
        mvc.perform(get("/create-poem-music").session(session)
                .param("poem", "오늘").param("house", "NONE").param("tree", "NONE").param("person", "NONE"))
                .andExpect(status().isOk()).andExpect(view().name("music_result"));
    }
}
