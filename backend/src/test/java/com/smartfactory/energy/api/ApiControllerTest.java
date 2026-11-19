package com.smartfactory.energy.api;

import static com.smartfactory.energy.TestData.recommendation;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartfactory.energy.analysis.AnalysisController;
import com.smartfactory.energy.analysis.AnalysisService;
import com.smartfactory.energy.analysis.PeakAnalysisService;
import com.smartfactory.energy.event.EventController;
import com.smartfactory.energy.event.EventLog;
import com.smartfactory.energy.event.EventService;
import com.smartfactory.energy.event.EventStage;
import com.smartfactory.energy.event.EventType;
import com.smartfactory.energy.event.dto.EventResponse;
import com.smartfactory.energy.global.exception.NotFoundException;
import com.smartfactory.energy.recommendation.RecommendationAction;
import com.smartfactory.energy.recommendation.RecommendationController;
import com.smartfactory.energy.recommendation.RecommendationReasonType;
import com.smartfactory.energy.recommendation.RecommendationService;
import com.smartfactory.energy.recommendation.RecommendationStatus;
import com.smartfactory.energy.recommendation.dto.RecommendationResponse;
import com.smartfactory.energy.report.ReportController;
import com.smartfactory.energy.report.ReportService;
import com.smartfactory.energy.report.dto.ReportResponse;
import com.smartfactory.energy.setting.SettingController;
import com.smartfactory.energy.setting.SettingHistoryController;
import com.smartfactory.energy.setting.SettingService;
import com.smartfactory.energy.sitepower.SitePowerController;
import com.smartfactory.energy.sitepower.SitePowerService;
import com.smartfactory.energy.tariff.TariffPeriodController;
import com.smartfactory.energy.tariff.TariffPeriodService;
import com.smartfactory.energy.zone.ZoneDetailController;
import com.smartfactory.energy.zone.ZoneDetailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 컨트롤러 테스트 (DB 없이 주소·요청값·응답 코드·응답 모양만 확인).
 * Service는 가짜(MockitoBean)로 바꾸고, 실제 HTTP 요청처럼 MockMvc로 부름.
 * 응답 필드 이름은 API 명세서 v0.2와 같은지 확인함.
 */
@WebMvcTest(controllers = {
        AnalysisController.class, ReportController.class, RecommendationController.class, EventController.class,
        SettingController.class, SettingHistoryController.class, TariffPeriodController.class,
        SitePowerController.class, ZoneDetailController.class
})
class ApiControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AnalysisService analysisService;
    @MockitoBean PeakAnalysisService peakAnalysisService;
    @MockitoBean ReportService reportService;
    @MockitoBean RecommendationService recommendationService;
    @MockitoBean EventService eventService;
    @MockitoBean SettingService settingService;
    @MockitoBean TariffPeriodService tariffPeriodService;
    @MockitoBean SitePowerService sitePowerService;
    @MockitoBean ZoneDetailService zoneDetailService;

    // ---------- 요청값이 틀리면 400 + { message } ----------

    @Test
    void 날짜_형식이_틀리면_400과_message() throws Exception {
        mockMvc.perform(get("/api/analyses/trend?from=2026-11-10&to=2026/11/16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("to")));
    }

    @Test
    void 필수_파라미터가_없으면_400() throws Exception {
        mockMvc.perform(get("/api/recommendations?from=2026-11-10")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/setting-histories?to=2026-11-10")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports")).andExpect(status().isBadRequest());
    }

    @Test
    void limit이_숫자가_아니면_400() throws Exception {
        mockMvc.perform(get("/api/events?limit=abc")).andExpect(status().isBadRequest());
    }

    @Test
    void 서비스가_IllegalArgumentException을_던지면_400과_그_문장() throws Exception {
        when(reportService.getReport("2026-13")).thenThrow(new IllegalArgumentException("month 형식이 올바르지 않습니다. (예: 2026-11)"));

        mockMvc.perform(get("/api/reports?month=2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month 형식이 올바르지 않습니다. (예: 2026-11)"));
    }

    @Test
    void 없는_구역이면_404() throws Exception {
        when(zoneDetailService.getDetail("Z9")).thenThrow(new NotFoundException("없는 구역입니다: Z9"));

        mockMvc.perform(get("/api/zones/Z9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("없는 구역입니다: Z9"));
    }

    // ---------- 추천 (명세 2·3번) ----------

    @Test
    void 추천이_없으면_204() throws Exception {
        when(recommendationService.findLatest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/recommendations/latest"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void 추천_1건은_명세의_필드_이름으로_응답() throws Exception {
        LocalDateTime window = LocalDateTime.of(2026, 11, 16, 14, 0);
        RecommendationResponse response = RecommendationResponse.from(recommendation(12, window.plusMinutes(3), window,
                RecommendationAction.DISCHARGE, RecommendationReasonType.PEAK_DEFENSE, RecommendationStatus.PENDING, 1800), null);
        when(recommendationService.findLatest()).thenReturn(Optional.of(response));

        ResultActions result = mockMvc.perform(get("/api/recommendations/latest")).andExpect(status().isOk());

        for (String field : List.of("id", "decidedAt", "windowStart", "windowEnd", "ruleLabel", "action", "powerKw",
                "durationMinutes", "predictedKw", "targetPeakKw", "reason", "usedValues", "status", "appliedBy",
                "appliedAt", "resultPeakKw", "resultStatus")) {
            result.andExpect(jsonPath("$." + field).hasJsonPath());
        }
        // 시각은 "2026-11-16T14:03:00" 모양 (명세: LocalDateTime, 한국 시간)
        result.andExpect(jsonPath("$.decidedAt").value("2026-11-16T14:03:00"))
                .andExpect(jsonPath("$.windowEnd").value("2026-11-16T14:15:00"))
                .andExpect(jsonPath("$.status").value("RECOMMENDED"));
    }

    @Test
    void 추천_처리_본문이_비었거나_JSON이_아니면_400() throws Exception {
        mockMvc.perform(patch("/api/recommendations/12").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/recommendations/12").contentType(MediaType.APPLICATION_JSON).content("APPLIED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지난_추천을_처리하면_400과_명세의_문장() throws Exception {
        when(recommendationService.handle(anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("이미 지난 추천입니다. 최신 추천을 확인해 주세요."));

        mockMvc.perform(patch("/api/recommendations/11").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이미 지난 추천입니다. 최신 추천을 확인해 주세요."));
    }

    // ---------- 이벤트 (명세 4번) ----------

    @Test
    void 이벤트는_limit_기본값_6이고_명세의_필드_이름으로_응답() throws Exception {
        EventResponse event = EventResponse.from(new EventLog(LocalDateTime.of(2026, 11, 16, 14, 0),
                EventType.STAGE_CHANGE, EventStage.WARNING, "경고 단계", null));
        when(eventService.findRecent(6)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].occurredAt").value("2026-11-16T14:00:00"))
                .andExpect(jsonPath("$[0].level").value("CRITICAL"))
                .andExpect(jsonPath("$[0].message").value("경고 단계"))
                .andExpect(jsonPath("$[0].eventType").value("STAGE_CHANGE"))
                .andExpect(jsonPath("$[0].category").value("STAGE"))
                .andExpect(jsonPath("$[0].recommendationId").hasJsonPath());
    }

    // ---------- 성적표 (명세 9번) ----------

    @Test
    void 기록_없는_달은_month_hasData_targetPeakKw만_의미있음() throws Exception {
        when(reportService.getReport("2026-12")).thenReturn(ReportResponse.empty("2026-12", 1800.0));

        mockMvc.perform(get("/api/reports?month=2026-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-12"))
                .andExpect(jsonPath("$.hasData").value(false))
                .andExpect(jsonPath("$.targetPeakKw").value(1800.0));
    }

    // ---------- 환경 설정 (명세 12번) ----------

    @Test
    void 설정_저장_본문이_배열이_아니면_400() throws Exception {
        mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"peak.target_kw\",\"value\":\"1750\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 설정_저장은_배열을_받아_서비스로_넘김() throws Exception {
        when(settingService.update(any())).thenReturn(List.of());

        mockMvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"key\":\"peak.target_kw\",\"value\":\"1750\"}]"))
                .andExpect(status().isOk());
    }
}
