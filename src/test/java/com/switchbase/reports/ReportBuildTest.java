package com.switchbase.reports;

import com.switchbase.TestcontainersConfiguration;
import com.switchbase.builds.BuildInput;
import com.switchbase.builds.BuildService;
import com.switchbase.builds.KeyboardBuild;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import com.switchbase.member.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 빌드 신고 규칙과 빌드 상세의 신고 버튼 조건 (review-collection-report §4.3, 후속 요청). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class ReportBuildTest {

    static final String PASSWORD = "password123";
    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    @Autowired MockMvc mvc;
    @Autowired BuildService buildService;
    @Autowired com.switchbase.keyswitch.SwitchService switchService;
    @Autowired com.switchbase.reviews.ReviewService reviewService;
    @Autowired com.switchbase.ratings.RatingService ratingService;
    @Autowired ReportService reportService;
    @Autowired ReportRepository reports;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reportingPublicBuildSucceeds() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);

        reportPost(login(reporter), build.getId()).andExpect(redirectedUrl("/builds/" + build.getId()));
        assertThat(reportsFor(build.getId())).hasSize(1);
    }

    @Test
    void hiddenBuildCannotBeReportedAndNoRecordIsCreated() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        buildService.setHidden(build.getId(), true);

        reportPost(login(reporter), build.getId()).andExpect(status().isNotFound());
        assertThat(reportsFor(build.getId())).isEmpty();
    }

    @Test
    void deletedBuildCannotBeReported() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        jdbc.update("UPDATE keyboard_build SET deleted_at = now() WHERE id = ?", build.getId());

        reportPost(login(reporter), build.getId()).andExpect(status().isNotFound());
        assertThat(reportsFor(build.getId())).isEmpty();
    }

    @Test
    void ownBuildCannotBeReported() throws Exception {
        Member owner = verifiedMember();
        KeyboardBuild build = createBuild(owner);

        reportPost(login(owner), build.getId()).andExpect(status().isOk());
        assertThat(reportsFor(build.getId())).isEmpty();
    }

    @Test
    void sameBuildCannotBeReportedTwiceByOneMember() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        MockHttpSession session = login(reporter);

        reportPost(session, build.getId()).andExpect(redirectedUrl("/builds/" + build.getId()));
        reportPost(session, build.getId()).andExpect(status().isConflict());
        assertThat(reportsFor(build.getId())).hasSize(1);
    }

    @Test
    void hiddenBuildAfterReportShowsHiddenStateInAdminList() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        reportPost(login(reporter), build.getId()).andExpect(redirectedUrl("/builds/" + build.getId()));
        Long reportId = reportsFor(build.getId()).getFirst().getId();

        mvc.perform(post("/admin/reports/" + reportId + "/hide").session(adminSession()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ReportView view = reportService.list(ReportStatus.RESOLVED, PageRequest.of(0, 500)).getContent().stream()
                .filter(v -> ("/builds/" + build.getId()).equals(v.targetHref()))
                .findFirst().orElseThrow();
        assertThat(view.targetState()).isEqualTo("숨김");
        assertThat(view.status()).isEqualTo("RESOLVED");
    }

    @Test
    void alreadyHiddenBuildCannotBeHiddenAgainByAdmin() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        reportPost(login(reporter), build.getId()).andExpect(redirectedUrl("/builds/" + build.getId()));
        Long reportId = reportsFor(build.getId()).getFirst().getId();
        buildService.setHidden(build.getId(), true);

        mvc.perform(post("/admin/reports/" + reportId + "/hide").session(adminSession()).with(csrf()))
                .andExpect(redirectedUrl("/admin/reports"));
        assertThat(reportsFor(build.getId()).getFirst().getStatus()).isEqualTo(ReportStatus.RECEIVED);
    }

    // 상세: 200, 신고 버튼은 로그인 회원이 타인의 공개 빌드를 볼 때만 보인다
    @Test
    void detailShowsReportButtonOnlyForOthersPublicBuild() throws Exception {
        Member owner = verifiedMember();
        Member other = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        String reportLink = "/reports/new?targetType=BUILD";

        mvc.perform(get("/builds/" + build.getId()).session(login(other)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(reportLink)));
        mvc.perform(get("/builds/" + build.getId()).session(login(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(reportLink))));
        mvc.perform(get("/builds/" + build.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(reportLink))));
        mvc.perform(get("/builds/" + build.getId()).session(login(owner)))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
    }

    @Test
    void hiddenBuildDetailIsNotFoundForOthersButOkForOwner() throws Exception {
        Member owner = verifiedMember();
        Member other = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        buildService.setHidden(build.getId(), true);

        mvc.perform(get("/builds/" + build.getId()).session(login(other))).andExpect(status().isNotFound());
        mvc.perform(get("/builds/" + build.getId()).session(login(owner))).andExpect(status().isOk());
    }

    // 숨김 후기·평가도 신고 대상에서 뺀다(빌드와 같은 기준). 레코드를 만들지 않고 404
    @Test
    void hiddenReviewCannotBeReportedAndNoRecordIsCreated() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        Long switchId = createSwitch();
        Long reviewId = reviewService.create(switchId, owner.getId(), "숨길 후기", java.util.List.of()).getId();
        reviewService.hideByReport(reviewId);

        mvc.perform(post("/reports").session(login(reporter)).with(csrf())
                        .param("targetType", "REVIEW").param("targetId", String.valueOf(reviewId))
                        .param("reasonCode", "SPAM"))
                .andExpect(status().isNotFound());
        assertThat(reports.findAll().stream()
                .filter(r -> r.getTargetType() == ReportTargetType.REVIEW && r.getTargetId().equals(reviewId))
                .toList()).isEmpty();
    }

    @Test
    void hiddenRatingCannotBeReportedAndNoRecordIsCreated() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        Long switchId = createSwitch();
        Long ratingId = ratingService.create(switchId, owner.getId(), new com.switchbase.ratings.RatingInput(
                null, (short) 3, com.switchbase.ratings.SoundTone.CLACKY, com.switchbase.ratings.Feel.SMOOTH,
                (short) 2, (short) 4, null, java.util.List.of())).getId();
        ratingService.hideByReport(ratingId);

        mvc.perform(post("/reports").session(login(reporter)).with(csrf())
                        .param("targetType", "RATING").param("targetId", String.valueOf(ratingId))
                        .param("reasonCode", "SPAM"))
                .andExpect(status().isNotFound());
        assertThat(reports.findAll().stream()
                .filter(r -> r.getTargetType() == ReportTargetType.RATING && r.getTargetId().equals(ratingId))
                .toList()).isEmpty();
    }

    private Long createSwitch() {
        com.switchbase.keyswitch.SwitchSpec spec = new com.switchbase.keyswitch.SwitchSpec("Gateron", null,
                "Rep " + hex(), null, com.switchbase.keyswitch.SwitchType.LINEAR, new java.math.BigDecimal("50"),
                null, null, null, null, null);
        return switchService.create(spec, java.util.List.of(), "등록", null, "tester", null).getId();
    }

    // 관리자 신고 목록: 200으로 렌더링되고, 숨긴 빌드는 '숨김' 상태로 보인다
    @Test
    void adminReportListRendersWithHiddenState() throws Exception {
        Member owner = verifiedMember();
        Member reporter = verifiedMember();
        KeyboardBuild build = createBuild(owner);
        reportPost(login(reporter), build.getId()).andExpect(redirectedUrl("/builds/" + build.getId()));
        Long reportId = reportsFor(build.getId()).getFirst().getId();
        mvc.perform(post("/admin/reports/" + reportId + "/hide").session(adminSession()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/admin/reports").param("status", "RESOLVED").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/reports"))
                .andExpect(content().string(containsString("/builds/" + build.getId())))
                .andExpect(content().string(containsString("숨김")));
        mvc.perform(get("/admin/reports").param("status", "RECEIVED").session(adminSession()))
                .andExpect(status().isOk());
        // 관리자가 아니면 목록에 들어갈 수 없다
        mvc.perform(get("/admin/reports").session(login(reporter)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private List<Report> reportsFor(Long buildId) {
        return reports.findAll().stream()
                .filter(r -> r.getTargetType() == ReportTargetType.BUILD && r.getTargetId().equals(buildId))
                .toList();
    }

    private ResultActions reportPost(MockHttpSession session, Long buildId) throws Exception {
        return mvc.perform(post("/reports").session(session).with(csrf())
                .param("targetType", "BUILD").param("targetId", String.valueOf(buildId))
                .param("reasonCode", "SPAM"));
    }

    private KeyboardBuild createBuild(Member owner) {
        return buildService.create(owner.getId(), new BuildInput("Report " + hex(), "ABS", null, null, null, null));
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("rbt-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "rbt" + hex, Role.USER, true));
    }

    private MockHttpSession login(Member member) throws Exception {
        return loginWith(member.getEmail(), PASSWORD);
    }

    private MockHttpSession adminSession() throws Exception {
        return loginWith(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private MockHttpSession loginWith(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", email).param("password", password).with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String hex() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
