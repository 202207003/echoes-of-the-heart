package com.springboot.controller;

import com.springboot.entity.Member;
import com.springboot.repository.MemberRepository;
import com.springboot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GuestLoginResultTests {
    private MemberRepository members;
    private ChatHistoryService history;
    private GeminiService gemini;
    private MusicGenerationService music;
    private GeminiController results;
    private MockHttpSession session;
    private MockMvc mvc;
    private Member member;
    private static final String POEM = "고요히 쉬어 가는 마음\n내일의 빛을 기다린다";

    @BeforeEach
    void setUp() {
        members = mock(MemberRepository.class);
        history = mock(ChatHistoryService.class);
        gemini = mock(GeminiService.class);
        music = mock(MusicGenerationService.class);
        PoemInputService inputService = mock(PoemInputService.class);
        when(inputService.splitByMeaning(anyString())).thenReturn(List.of("오늘은 지쳤어"));
        when(gemini.getGeminiResponse(anyString())).thenReturn("[시]\n" + POEM + "\n\n[키워드]\nCalm, Peaceful");
        when(music.generateMusic(anyString(), anyList())).thenReturn("track");
        when(music.getAudioUrl("track")).thenReturn("https://example.test/music.mp3");
        when(music.getTrackTitle("track")).thenReturn("기존 음악");
        when(music.getThumbnail("track")).thenReturn("https://example.test/image.png");
        GuestResultService guests = new GuestResultService(history);
        results = new GeminiController(gemini, mock(EmotionImageService.class), music, history, members, inputService, guests);
        mvc = MockMvcBuilders.standaloneSetup(results, new MemberContorller(members, guests)).build();
        session = new MockHttpSession();
        member = new Member();
        member.setId(1L);
        member.setUsername("member-id");
        member.setPassword("password");
        member.setMbti("INFP");
        when(members.findByUsername("member-id")).thenReturn(Optional.of(member));
    }

    private String create(String input) {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("music_result", results.createPoemMusic(input, "NONE", "NONE", "NONE", "tired", model, session));
        return (String) model.get("resultId");
    }

    private ExtendedModelMap read(String id) {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("music_result", results.showResult(id, session, model));
        return model;
    }

    @Test
    void loginRestoresExactSelectedGuestResultAndSavesWithoutRegeneration() throws Exception {
        String first = create("첫 번째 감정");
        create("두 번째 감정");
        ExtendedModelMap before = read(first);
        verifyNoInteractions(history);
        clearInvocations(gemini, music);

        mvc.perform(get("/login").session(session).param("resultId", first))
                .andExpect(view().name("login_form")).andExpect(model().attribute("resultId", first));
        mvc.perform(post("/login").session(session).param("username", "member-id")
                        .param("password", "password").param("resultId", first))
                .andExpect(redirectedUrl("/music-result?resultId=" + first))
                .andExpect(flash().attributeExists("resultNotice"));

        assertEquals(before, read(first));
        assertSame(member, session.getAttribute("member"));
        assertEquals("INFP", session.getAttribute("mbti"));
        verify(history).save(member, "첫 번째 감정", POEM, "기존 음악", "https://example.test/music.mp3");
        verify(history).save(member, "두 번째 감정", POEM, "기존 음악", "https://example.test/music.mp3");
        mvc.perform(get("/music-result").session(session).param("resultId", first)).andExpect(status().isOk());
        verifyNoMoreInteractions(history);
        verifyNoInteractions(gemini, music);
    }

    @Test
    void loginFailureKeepsResultAndReturnsToTheSameLoginFlow() throws Exception {
        String id = create("오늘은 지쳤어");
        mvc.perform(post("/login").session(session).param("username", "member-id")
                        .param("password", "wrong-password").param("resultId", id))
                .andExpect(redirectedUrl("/login?resultId=" + id))
                .andExpect(flash().attributeExists("loginError"));
        assertNull(session.getAttribute("username"));
        assertEquals(POEM, read(id).get("generatedPoem"));
        verifyNoInteractions(history);
        mvc.perform(post("/login").session(session).param("username", "member-id")
                        .param("password", "password").param("resultId", id))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        verify(history, times(1)).save(member, "오늘은 지쳤어", POEM, "기존 음악", "https://example.test/music.mp3");
    }

    @Test
    void loginFromOtherPagesFallsBackToLatestGuestResult() throws Exception {
        create("첫 번째 감정");
        String latest = create("마지막 감정");
        mvc.perform(get("/login").session(session))
                .andExpect(model().attribute("resultId", latest));
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + latest));
    }

    @Test
    void ordinaryLoginAndRepeatedLoginDoNotCreateDuplicateHistory() throws Exception {
        String id = create("오늘은 지쳤어");
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/chat"));
        verify(history, times(1)).save(any(), anyString(), anyString(), any(), any());
        mvc.perform(post("/login").session(new MockHttpSession()).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/chat"));
        verifyNoMoreInteractions(history);
    }

    @Test
    void guestRegenerationSavesLatestVersionInsteadOfOriginalVersion() throws Exception {
        String id = create("오늘은 지쳤어");
        String latestPoem = "새롭게 숨을 고르고\n조용한 밤을 건넌다";
        when(gemini.getGeminiResponse(anyString())).thenReturn("[시]\n" + latestPoem + "\n[키워드]\nCalm, Peaceful");
        mvc.perform(post("/regenerate-poem").session(session).param("resultId", id))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(history);
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        verify(history).save(member, "오늘은 지쳤어", latestPoem, "기존 음악", "https://example.test/music.mp3");
        verifyNoMoreInteractions(history);
    }

    @Test
    void guestWithoutMusicCanStillSaveAndResumePoem() throws Exception {
        when(music.generateMusic(anyString(), anyList())).thenReturn(null);
        String id = create("오늘은 지쳤어");
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        verify(history).save(member, "오늘은 지쳤어", POEM, null, null);
        assertEquals(false, read(id).get("hasMusic"));
    }

    @Test
    void unrelatedSessionsCannotClaimGuestResultsOrExternalReturnUrls() throws Exception {
        String id = create("오늘은 지쳤어");
        mvc.perform(post("/login").session(new MockHttpSession()).param("username", "member-id")
                        .param("password", "password").param("resultId", id))
                .andExpect(redirectedUrl("/chat"));
        verifyNoInteractions(history);
        mvc.perform(post("/login").session(session).param("username", "member-id")
                        .param("password", "password").param("resultId", "https://untrusted.example"))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
    }

    @Test
    void signupPreservesResultUntilUserLogsIn() throws Exception {
        String id = create("오늘은 지쳤어");
        mvc.perform(get("/sign").session(session).param("resultId", id))
                .andExpect(model().attribute("resultId", id));
        mvc.perform(post("/sign").session(session).param("username", "new-user").param("password", "password")
                        .param("mbti", "INFP").param("resultId", id))
                .andExpect(redirectedUrl("/login?resultId=" + id))
                .andExpect(flash().attributeExists("loginNotice"));
        verifyNoInteractions(history);
        when(members.findByUsername("new-user")).thenReturn(Optional.of(member));
        mvc.perform(post("/login").session(session).param("username", "new-user")
                        .param("password", "password").param("resultId", id))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        verify(history).save(member, "오늘은 지쳤어", POEM, "기존 음악", "https://example.test/music.mp3");
    }

    @Test
    void savesOnlyFiveRetainedResultsAndDoesNotReturnToExpiredResult() throws Exception {
        String expired = create("오래된 감정");
        String latest = expired;
        for (int index = 0; index < 5; index++) latest = create("감정 " + index);
        mvc.perform(post("/login").session(session).param("username", "member-id")
                        .param("password", "password").param("resultId", expired))
                .andExpect(redirectedUrl("/music-result?resultId=" + latest));
        verify(history, times(5)).save(eq(member), anyString(), eq(POEM), any(), any());
        verify(history, never()).save(eq(member), eq("오래된 감정"), anyString(), any(), any());
    }

    @Test
    void failedHistoryImportKeepsResultAndRetriesWithoutDuplicatingAlreadySavedItems() throws Exception {
        create("첫 번째 감정");
        String second = create("두 번째 감정");
        doThrow(new IllegalStateException("Database unavailable")).doNothing().when(history)
                .save(member, "두 번째 감정", POEM, "기존 음악", "https://example.test/music.mp3");
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + second))
                .andExpect(flash().attributeExists("resultError"));
        assertEquals(POEM, read(second).get("generatedPoem"));
        mvc.perform(post("/login").session(session).param("username", "member-id").param("password", "password"))
                .andExpect(redirectedUrl("/music-result?resultId=" + second));
        verify(history, times(1)).save(member, "첫 번째 감정", POEM, "기존 음악", "https://example.test/music.mp3");
        verify(history, times(2)).save(member, "두 번째 감정", POEM, "기존 음악", "https://example.test/music.mp3");
    }
}
