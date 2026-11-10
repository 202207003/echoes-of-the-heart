package com.springboot.controller;

import com.springboot.entity.Member;
import com.springboot.repository.MemberRepository;
import com.springboot.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.List;
import java.util.Optional;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResultRegenerationTests {
    private static final String INPUT = "나는 오늘 지쳤어 기분이 안좋아 밥을 먹고싶어";
    private static final String OLD_POEM = "기존 시 1\n기존 시 2\n기존 시 3\n기존 시 4\n기존 시 5\n기존 시 6";
    private static final String NEW_POEM = "새로운 시 1\n새로운 시 2\n새로운 시 3\n새로운 시 4\n새로운 시 5\n새로운 시 6";
    private GeminiService gemini;
    private MusicGenerationService music;
    private PoemInputService inputService;
    private MemberRepository members;
    private ChatHistoryService history;
    private GeminiController controller;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        gemini = mock(GeminiService.class);
        music = mock(MusicGenerationService.class);
        inputService = mock(PoemInputService.class);
        members = mock(MemberRepository.class);
        history = mock(ChatHistoryService.class);
        controller = new GeminiController(gemini, mock(EmotionImageService.class), music, history, members,
                inputService, new GuestResultService(history));
        session = new MockHttpSession();
        when(inputService.splitByMeaning(anyString())).thenReturn(List.of("나는 오늘 지쳤어", "기분이 안좋아", "밥을 먹고싶어"));
        when(gemini.getGeminiResponse(anyString())).thenReturn(response(OLD_POEM, "Calm, Peaceful"));
        when(music.generateMusic(anyString(), anyList())).thenReturn("old-track");
        when(music.getAudioUrl("old-track")).thenReturn("https://example.test/old.mp3");
        when(music.getTrackTitle("old-track")).thenReturn("기존 곡");
        when(music.getThumbnail("old-track")).thenReturn("https://example.test/old.png");
        when(music.getAudioUrl("new-track")).thenReturn("https://example.test/new.mp3");
        when(music.getTrackTitle("new-track")).thenReturn("새로운 곡");
        when(music.getThumbnail("new-track")).thenReturn("https://example.test/new.png");
    }

    private String response(String poem, String keywords) {
        return "[시]\n" + poem + "\n\n[키워드]\n" + keywords;
    }

    private String createResult() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("music_result", controller.createPoemMusic(INPUT, "NONE", "NONE", "NONE", "tired", model, session));
        return (String) model.get("resultId");
    }

    private ExtendedModelMap readResult(String id) {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("music_result", controller.showResult(id, session, model));
        return model;
    }

    @Test
    void poemRegenerationKeepsAllMusicFieldsAndReusesMeaningAnalysis() {
        String id = createResult();
        ExtendedModelMap before = readResult(id);
        clearInvocations(gemini, music, inputService);
        when(gemini.getGeminiResponse(anyString())).thenReturn(response(NEW_POEM, "Happy, Upbeat"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/music-result?resultId=" + id, controller.regeneratePoem(id, session, redirect));

        ExtendedModelMap after = readResult(id);
        assertEquals(NEW_POEM, after.get("generatedPoem"));
        for (String key : List.of("originalPoem", "style", "audioUrl", "hasMusic", "thumbnailUrl", "trackTitle")) {
            assertEquals(before.get(key), after.get(key), key);
        }
        verifyNoInteractions(music, inputService);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).getGeminiResponse(prompt.capture());
        assertTrue(prompt.getValue().contains(OLD_POEM));
        assertTrue(prompt.getValue().contains("시를 정확히 6줄로 작성해."));
        assertTrue(redirect.getFlashAttributes().containsKey("resultNotice"));
    }

    @Test
    void musicRegenerationKeepsPoemAndNeverCallsGemini() {
        String id = createResult();
        clearInvocations(gemini, inputService);
        when(music.generateMusic(eq("Calm Peaceful"), anyList())).thenAnswer(call -> {
            assertTrue(((List<?>) call.getArgument(1)).contains("old-track"));
            return "new-track";
        });
        controller.recommendAnotherMusic(id, session, new RedirectAttributesModelMap());

        ExtendedModelMap result = readResult(id);
        assertEquals(OLD_POEM, result.get("generatedPoem"));
        assertEquals("Calm, Peaceful", result.get("style"));
        assertEquals("https://example.test/new.mp3", result.get("audioUrl"));
        assertEquals("새로운 곡", result.get("trackTitle"));
        assertEquals("https://example.test/new.png", result.get("thumbnailUrl"));
        verifyNoInteractions(gemini, inputService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"오류 발생: 연결 실패", "[시]\n\n[키워드]\nCalm", "[시]\n새로운 시\n[키워드]\n"})
    void invalidPoemResponsesKeepExistingResult(String response) {
        String id = createResult();
        ExtendedModelMap before = readResult(id);
        when(gemini.getGeminiResponse(anyString())).thenReturn(response);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        controller.regeneratePoem(id, session, redirect);
        assertEquals(before, readResult(id));
        assertTrue(redirect.getFlashAttributes().containsKey("resultError"));
        verifyNoInteractions(history);
    }

    @Test
    void samePoemAndThrownApiErrorsKeepExistingResult() {
        String id = createResult();
        ExtendedModelMap before = readResult(id);
        RedirectAttributesModelMap same = new RedirectAttributesModelMap();
        controller.regeneratePoem(id, session, same);
        assertTrue(same.getFlashAttributes().containsKey("resultError"));
        when(gemini.getGeminiResponse(anyString())).thenThrow(new IllegalStateException("offline"));
        RedirectAttributesModelMap failed = new RedirectAttributesModelMap();
        controller.regeneratePoem(id, session, failed);
        assertTrue(failed.getFlashAttributes().containsKey("resultError"));
        assertEquals(before, readResult(id));
    }

    @Test
    void missingSameAndUnplayableMusicKeepExistingTrack() {
        String id = createResult();
        ExtendedModelMap before = readResult(id);
        when(music.generateMusic(anyString(), anyList())).thenReturn(null, "old-track", "broken-track");
        when(music.getAudioUrl("broken-track")).thenReturn("");
        for (int attempt = 0; attempt < 3; attempt++) {
            RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
            controller.recommendAnotherMusic(id, session, redirect);
            assertEquals(before, readResult(id));
            assertTrue(redirect.getFlashAttributes().containsKey("resultError"));
        }
    }

    @Test
    void musicExceptionDoesNotDestroyExistingResult() {
        String id = createResult();
        ExtendedModelMap before = readResult(id);
        when(music.generateMusic(anyString(), anyList())).thenThrow(new IllegalStateException("offline"));
        controller.recommendAnotherMusic(id, session, new RedirectAttributesModelMap());
        assertEquals(before, readResult(id));
    }

    @Test
    void musicCanBeRetriedWhenInitialSearchFoundNothing() {
        when(music.generateMusic(anyString(), anyList())).thenReturn(null);
        String id = createResult();
        assertEquals(false, readResult(id).get("hasMusic"));
        when(music.generateMusic(anyString(), anyList())).thenReturn("new-track");
        controller.recommendAnotherMusic(id, session, new RedirectAttributesModelMap());
        assertEquals(true, readResult(id).get("hasMusic"));
    }

    @Test
    void eachTabHasItsOwnResultAndOtherSessionsCannotUseIt() {
        String first = createResult();
        String second = createResult();
        assertNotEquals(first, second);
        when(gemini.getGeminiResponse(anyString())).thenReturn(response(NEW_POEM, "Calm, Peaceful"));
        controller.regeneratePoem(first, session, new RedirectAttributesModelMap());
        assertEquals(NEW_POEM, readResult(first).get("generatedPoem"));
        assertEquals(OLD_POEM, readResult(second).get("generatedPoem"));

        clearInvocations(gemini, music);
        MockHttpSession otherSession = new MockHttpSession();
        assertEquals("redirect:/chat", controller.showResult(first, otherSession, new ExtendedModelMap()));
        assertEquals("redirect:/chat", controller.regeneratePoem(first, otherSession, new RedirectAttributesModelMap()));
        assertEquals("redirect:/chat", controller.recommendAnotherMusic(first, otherSession, new RedirectAttributesModelMap()));
        verifyNoInteractions(gemini, music);
    }

    @Test
    void keepsOnlyFiveRecentResultsAndReadOnlyRefreshDoesNotGenerateAgain() {
        String first = createResult();
        String latest = first;
        for (int index = 0; index < 5; index++) latest = createResult();
        assertEquals("redirect:/chat", controller.showResult(first, session, new ExtendedModelMap()));
        clearInvocations(gemini, music, history);
        readResult(latest);
        readResult(latest);
        verifyNoInteractions(gemini, music, history);
        assertTrue(((List<?>) session.getAttribute("recentMusicTrackIds")).size() <= 5);
    }

    @Test
    void remembersFiftyMusicChoicesInsteadOfCyclingAfterFive() {
        String id = createResult();
        when(music.getAudioUrl(anyString())).thenAnswer(call -> "https://example.test/" + call.getArgument(0) + ".mp3");
        for (int index = 0; index < 55; index++) {
            when(music.generateMusic(anyString(), anyList())).thenReturn("track-" + index);
            controller.recommendAnotherMusic(id, session, new RedirectAttributesModelMap());
        }
        List<?> recent = (List<?>) session.getAttribute("recentMusicTrackIds");
        assertEquals(50, recent.size());
        assertFalse(recent.contains("old-track"));
        assertTrue(recent.contains("track-54"));
        assertEquals(OLD_POEM, readResult(id).get("generatedPoem"));
    }

    @Test
    void successfulRegenerationsAreSavedForLoggedInMemberOnly() {
        Member member = new Member();
        member.setId(10L);
        session.setAttribute("username", "member-id");
        when(members.findByUsername("member-id")).thenReturn(Optional.of(member));
        String id = createResult();
        verify(history).save(member, INPUT, OLD_POEM, "기존 곡", "https://example.test/old.mp3");
        when(gemini.getGeminiResponse(anyString())).thenReturn(response(NEW_POEM, "Calm, Peaceful"));
        controller.regeneratePoem(id, session, new RedirectAttributesModelMap());
        verify(history).save(member, INPUT, NEW_POEM, "기존 곡", "https://example.test/old.mp3");
        when(music.generateMusic(anyString(), anyList())).thenReturn("new-track");
        controller.recommendAnotherMusic(id, session, new RedirectAttributesModelMap());
        verify(history).save(member, INPUT, NEW_POEM, "새로운 곡", "https://example.test/new.mp3");
    }

    @Test
    void initialPoemFailurePreservesUserInputAndDoesNotSearchMusic() {
        when(gemini.getGeminiResponse(anyString())).thenReturn("오류 발생: 연결 실패");
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("chat_form", controller.createPoemMusic(INPUT, "NONE", "NONE", "NONE", "tired", model, session));
        assertEquals(INPUT, model.get("originalPoem"));
        assertNotNull(model.get("generationError"));
        verifyNoInteractions(music, history);
    }

    @Test
    void buttonsUsePostAndRedirectToResultWithoutRepeatedGenerationOnRefresh() throws Exception {
        String id = createResult();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(gemini.getGeminiResponse(anyString())).thenReturn(response(NEW_POEM, "Calm, Peaceful"));
        mvc.perform(post("/regenerate-poem").session(session).param("resultId", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/music-result?resultId=" + id))
                .andExpect(flash().attributeExists("resultNotice"));
        when(music.generateMusic(anyString(), anyList())).thenReturn("new-track");
        mvc.perform(post("/recommend-music").session(session).param("resultId", id))
                .andExpect(redirectedUrl("/music-result?resultId=" + id));
        mvc.perform(get("/regenerate-poem").session(session).param("resultId", id))
                .andExpect(status().isMethodNotAllowed());
        clearInvocations(gemini, music);
        mvc.perform(get("/music-result").session(session).param("resultId", id))
                .andExpect(status().isOk()).andExpect(view().name("music_result"))
                .andExpect(model().attribute("generatedPoem", NEW_POEM));
        mvc.perform(post("/recommend-music").session(new MockHttpSession()).param("resultId", id))
                .andExpect(redirectedUrl("/chat"));
        verifyNoInteractions(gemini, music);
    }

    private String renderTemplate(String name, ExtendedModelMap model) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        var application = JakartaServletWebApplication.buildApplication(request.getServletContext());
        var exchange = application.buildExchange(request, new MockHttpServletResponse());
        return engine.process(name, new WebContext(exchange, Locale.KOREAN, model));
    }

    @Test
    void rendersBothButtonsWithAndWithoutMusicAndEscapesErrors() {
        String id = createResult();
        ExtendedModelMap model = readResult(id);
        model.addAttribute("resultError", "<script>alert('error')</script>");
        String html = renderTemplate("music_result", model);
        assertTrue(html.contains("action=\"/regenerate-poem\""));
        assertTrue(html.contains("action=\"/recommend-music\""));
        assertTrue(html.contains("value=\"" + id + "\""));
        assertTrue(html.contains("시 다시 생성"));
        assertTrue(html.contains("음악만 다시 추천"));
        assertTrue(html.contains("https://example.test/old.mp3"));
        assertFalse(html.contains("<script>alert('error')</script>"));
        model.addAttribute("hasMusic", false);
        String withoutMusic = renderTemplate("music_result", model);
        assertTrue(withoutMusic.contains("action=\"/recommend-music\""));
        assertFalse(withoutMusic.contains("<audio"));
    }

    @Test
    void rendersInitialFailureAndPreservesEscapedInput() {
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("generationError", "다시 시도해주세요.");
        model.addAttribute("originalPoem", "<script>입력</script>");
        String html = renderTemplate("chat_form", model);
        assertTrue(html.contains("다시 시도해주세요."));
        assertTrue(html.contains("&lt;script&gt;입력&lt;/script&gt;"));
    }

    @Test
    void resultLoginAndSignupTemplatesCarryTheSameResultId() {
        String id = createResult();
        ExtendedModelMap model = readResult(id);
        String result = renderTemplate("music_result", model);
        assertTrue(result.contains("/login?resultId=" + id));
        assertTrue(result.contains("로그인하고 결과 저장하기"));
        for (String template : List.of("login_form", "sign_form")) {
            String html = renderTemplate(template, model);
            assertTrue(html.contains("name=\"resultId\" value=\"" + id + "\""));
            assertTrue(html.contains("?resultId=" + id));
        }
        session.setAttribute("username", "member-id");
        assertFalse(renderTemplate("music_result", model).contains("로그인하고 결과 저장하기"));
    }
}
