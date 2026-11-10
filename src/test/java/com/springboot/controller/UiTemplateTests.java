package com.springboot.controller;

import com.springboot.entity.ChatHistory;
import com.springboot.service.EmotionImageService;
import com.springboot.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class UiTemplateTests {
    private static final String INPUT = "바쁘게 달려온 하루, 오늘은 잠시 쉬어가고 싶다.";
    private static final String POEM = "분주한 발걸음이 멈춘 자리\n작은 숨 하나가 마음을 감싼다\n오늘의 무게를 가만히 내려놓고\n내일의 빛을 천천히 기다린다";

    private String render(String template, String fixture, Map<String, Object> model, boolean signedIn) throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var request = new MockHttpServletRequest();
        var session = new MockHttpSession();
        if (signedIn) session.setAttribute("username", "마음기록");
        request.setSession(session);
        var app = JakartaServletWebApplication.buildApplication(request.getServletContext());
        var context = new WebContext(app.buildExchange(request, new MockHttpServletResponse()), Locale.KOREAN, model);
        String html = engine.process(template, context);
        Path directory = Path.of("build", "ui-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(fixture + ".html"), html);
        assertTrue(html.contains("/css/app.css"));
        assertFalse(html.contains("th:action="));
        return html;
    }

    @Test
    void rendersIndependentQuickAnalysisFormForEachMood() throws Exception {
        var images = new EmotionImageService(mock(GeminiService.class));
        for (var mood : images.moods()) {
            var model = new HashMap<String, Object>();
            model.put("poem", INPUT);
            model.put("imageMood", mood);
            model.put("imageMoods", images.moods());
            model.put("imageCategories", images.categories(mood));
            var previews = images.recommendations(mood, new MockHttpSession());
            model.put("imagePreviews", previews);
            String html = render("emotion-analysis", "mood-" + mood.id(), model, false);
            String quick = html.substring(html.indexOf("<form id=\"quick-analysis-form\""));
            quick = quick.substring(0, quick.indexOf("</form>"));
            for (var preview : previews) {
                assertTrue(quick.contains("name=\"" + preview.categoryId() + "\" value=\"" + preview.choice().id() + "\""));
                assertTrue(html.contains("aria-label=\"" + preview.choice().label() + "\""));
            }
            assertFalse(quick.contains("required"), "Quick analysis must not depend on custom radio selections");
            assertTrue(quick.contains("value=\"" + mood.id() + "\""));
            assertTrue(html.contains("/images/emotions/" + mood.id() + ".png"));
        }
    }

    @Test
    void resultMusicRetryStaysInsideMusicCardWithAndWithoutAudio() throws Exception {
        var model = new HashMap<String, Object>();
        model.put("resultId", "demo-result");
        model.put("generatedPoem", POEM);
        model.put("style", "Calm, Peaceful");
        model.put("hasMusic", true);
        model.put("trackTitle", "A Quiet Moment");
        model.put("audioUrl", "/preview-audio.wav");
        model.put("thumbnailUrl", "/images/emotions/calm.png");
        String html = render("music_result", "result", model, false);
        int musicStart = html.indexOf("<section class=\"card music-card\"");
        int musicEnd = html.indexOf("</section>", musicStart);
        assertTrue(html.indexOf("action=\"/recommend-music\"") < musicEnd);
        assertTrue(html.indexOf("action=\"/recommend-music\"") > musicStart);
        assertFalse(html.contains("Free To Use Music"));
        model.put("hasMusic", false);
        String missing = render("music_result", "result-no-music", model, false);
        assertFalse(missing.contains("<audio"));
        assertTrue(missing.contains("action=\"/recommend-music\""));
        model.put("hasMusic", true);
        assertFalse(render("music_result", "result-member", model, true).contains("로그인하고 결과 저장하기"));
    }

    @Test
    void rendersEntryAndAccountPagesWithEscapedInput() throws Exception {
        render("chat_form", "chat", Map.of(), false);
        render("login_form", "login", Map.of("resultId", "demo-result"), false);
        render("sign_form", "signup", Map.of("resultId", "demo-result"), false);
        String error = render("login_form", "login-error", Map.of("loginError", "<script>오류</script>"), false);
        assertFalse(error.contains("<script>오류</script>"));
        assertTrue(error.contains("&lt;script&gt;오류&lt;/script&gt;"));
    }

    @Test
    void sundayCalendarHasNoLeadingEmptyCellsAndMarksSelectedDate() throws Exception {
        var item = new ChatHistory();
        item.setId(1L);
        item.setQuestion(INPUT);
        item.setPoem(POEM);
        item.setMusicTitle("A Quiet Moment");
        item.setMusicUrl("/preview-audio.wav");
        item.setCreatedAt(LocalDateTime.of(2026, 11, 4, 14, 30));
        Map<String, Object> model = Map.of("selectedDate", LocalDate.of(2026, 11, 4),
                "histories", List.of(item), "historyDays", Set.of(4, 12, 18));
        String html = render("history", "history", model, true);
        assertFalse(html.contains("class=\"calendar-day empty\""));
        assertTrue(html.contains("aria-current=\"date\""));
        render("history-detail", "detail", Map.of("history", item), true);
        render("history", "history-empty", Map.of("selectedDate", LocalDate.of(2026, 12, 4),
                "histories", List.of(), "historyDays", Set.of()), true);
    }
}
