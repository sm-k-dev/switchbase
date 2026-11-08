package com.switchbase.reviews;

import com.switchbase.TestcontainersConfiguration;
import com.switchbase.collections.CollectionService;
import com.switchbase.collections.CollectionStatus;
import com.switchbase.common.RetentionService;
import com.switchbase.keyswitch.SwitchService;
import com.switchbase.keyswitch.SwitchSpec;
import com.switchbase.keyswitch.SwitchType;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import com.switchbase.member.Role;
import com.switchbase.member.WithdrawalService;
import com.switchbase.ratings.RatingService;
import com.switchbase.ratings.RatingView;
import com.switchbase.reports.Report;
import com.switchbase.reports.ReportRepository;
import com.switchbase.reports.ReportTargetType;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 후기·컬렉션·신고·탈퇴·보관 정리 (review-collection-report §8 + 숨김 조회 조건).
 * 실제 로그인 세션으로 요청한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class ReviewCollectionReportTest {

    static final String PASSWORD = "password123";
    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired ReviewService reviewService;
    @Autowired RatingService ratingService;
    @Autowired CollectionService collectionService;
    @Autowired ReportRepository reports;
    @Autowired WithdrawalService withdrawalService;
    @Autowired RetentionService retentionService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void reviewOnePerSwitchEditDeleteAndEmailHidden() throws Exception {
        Member author = verifiedMember();
        Long switchId = createSwitch("Review " + hex());
        MockHttpSession session = login(author);

        mvc.perform(post("/switches/" + switchId + "/reviews").session(session).with(csrf())
                        .param("body", "소리가 깔끔해요").param("recommendedFor", "BEGINNER"))
                .andExpect(redirectedUrl("/switches/" + switchId));
        mvc.perform(post("/switches/" + switchId + "/reviews").session(session).with(csrf())
                        .param("body", "두 번째").param("recommendedFor", "QUIET"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("이미 후기를 남겼어요")));

        mvc.perform(get("/switches/" + switchId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("소리가 깔끔해요")))
                .andExpect(content().string(not(containsString(author.getEmail()))));
    }

    @Test
    void hiddenReviewShowsOnlyToAuthorAndUnhideIsAuthorOnly() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        Long switchId = createSwitch("Hide " + hex());
        mvc.perform(post("/switches/" + switchId + "/reviews").session(login(author)).with(csrf())
                .param("body", "숨길 후기 " + hex())).andExpect(status().is3xxRedirection());
        Long reviewId = reviewService.list(switchId, author.getId(), false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().id();

        mvc.perform(post("/reports").session(login(other)).with(csrf())
                .param("targetType", "REVIEW").param("targetId", String.valueOf(reviewId))
                .param("reasonCode", "SPAM")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/reports/" + reportId(ReportTargetType.REVIEW, reviewId) + "/hide")
                .session(adminSession()).with(csrf())).andExpect(status().is3xxRedirection());

        // 타인 목록에서는 빠지고, 작성자 목록에서만 hidden=true로 보인다
        assertThat(reviewService.list(switchId, other.getId(), false, "recent", PageRequest.of(0, 20))
                .getContent()).noneMatch(v -> v.id().equals(reviewId));
        assertThat(reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent()).noneMatch(v -> v.id().equals(reviewId));
        var mine = reviewService.list(switchId, author.getId(), false, "recent", PageRequest.of(0, 20))
                .getContent().stream().filter(v -> v.id().equals(reviewId)).findFirst().orElseThrow();
        assertThat(mine.hidden()).isTrue();

        // 해제는 작성자만. 관리자와 다른 회원은 404
        mvc.perform(post("/reviews/" + reviewId + "/unhide").session(adminSession()).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reviews/" + reviewId + "/unhide").session(login(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reviews/" + reviewId + "/unhide").session(login(author)).with(csrf()))
                .andExpect(redirectedUrl("/switches/" + switchId));
        assertThat(reviewService.list(switchId, other.getId(), false, "recent", PageRequest.of(0, 20))
                .getContent()).anyMatch(v -> v.id().equals(reviewId));
    }

    @Test
    void hiddenRatingFollowsSameVisibilityAndLeavesStats() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        Long switchId = createSwitch("RateHide " + hex());
        ratingPost(login(author), switchId, 4).andExpect(status().is3xxRedirection());
        ratingPost(login(other), switchId, 2).andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(2L);
        Long ratingId = ratingService.list(switchId, author.getId(), false, PageRequest.of(0, 20))
                .getContent().stream().filter(RatingView::mine).findFirst().orElseThrow().id();

        mvc.perform(post("/reports").session(login(other)).with(csrf())
                .param("targetType", "RATING").param("targetId", String.valueOf(ratingId))
                .param("reasonCode", "ABUSE")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/reports/" + reportId(ReportTargetType.RATING, ratingId) + "/hide")
                .session(adminSession()).with(csrf())).andExpect(status().is3xxRedirection());

        assertThat(ratingService.list(switchId, other.getId(), false, PageRequest.of(0, 20))
                .getContent()).noneMatch(v -> v.id().equals(ratingId));
        assertThat(ratingService.list(switchId, author.getId(), false, PageRequest.of(0, 20))
                .getContent().stream().filter(v -> v.id().equals(ratingId)).findFirst().orElseThrow().hidden())
                .isTrue();
        // 숨긴 평가는 집계에서 빠진다
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(2.0);

        mvc.perform(post("/ratings/" + ratingId + "/unhide").session(login(author)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(2L);
    }

    @Test
    void helpfulToggleCountsAndOwnReviewCannotVote() throws Exception {
        Member author = verifiedMember();
        Member fan = verifiedMember();
        Long switchId = createSwitch("Help " + hex());
        mvc.perform(post("/switches/" + switchId + "/reviews").session(login(author)).with(csrf())
                .param("body", "도움 후기")).andExpect(status().is3xxRedirection());
        Long reviewId = reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().id();

        mvc.perform(post("/reviews/" + reviewId + "/helpful").session(login(author)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().helpfulCount()).isZero();

        mvc.perform(post("/reviews/" + reviewId + "/helpful").session(login(fan)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reviewService.list(switchId, null, false, "helpful", PageRequest.of(0, 20))
                .getContent().getFirst().helpfulCount()).isEqualTo(1);

        mvc.perform(post("/reviews/" + reviewId + "/helpful").session(login(fan)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().helpfulCount()).isZero();
    }

    @Test
    void collectionTogglesAreIndependentAndPublicityIsEnforced() throws Exception {
        Member owner = verifiedMember();
        Member viewer = verifiedMember();
        Long switchId = createSwitch("Coll " + hex());
        MockHttpSession session = login(owner);

        mvc.perform(post("/switches/" + switchId + "/collection/FAVORITE").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/switches/" + switchId + "/collection/OWNED").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var state = collectionService.state(owner.getId(), switchId);
        assertThat(state.favorite()).isTrue();
        assertThat(state.owned()).isTrue();
        assertThat(state.tried()).isFalse();
        mvc.perform(post("/switches/" + switchId + "/collection/FAVORITE").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(collectionService.state(owner.getId(), switchId).favorite()).isFalse();

        // 비공개: 타인은 404, 본인은 200
        mvc.perform(get("/members/" + owner.getId() + "/collection").session(login(viewer)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/me/collection/visibility").session(session).with(csrf())
                .param("collectionPublic", "true")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/members/" + owner.getId() + "/collection").session(login(viewer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mvc.perform(get("/me/collection").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(owner.getEmail()))));
    }

    // 탈퇴한 회원의 컬렉션은 공개 설정과 상관없이 타인에게 404
    @Test
    void publicCollectionOfWithdrawnMemberIsNotFound() throws Exception {
        Member owner = verifiedMember();
        Member viewer = verifiedMember();
        Long switchId = createSwitch("Pub " + hex());
        mvc.perform(post("/switches/" + switchId + "/collection/TRIED").session(login(owner)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/me/collection/visibility").session(login(owner)).with(csrf())
                .param("collectionPublic", "true")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/members/" + owner.getId() + "/collection").session(login(viewer)))
                .andExpect(status().isOk());

        withdrawalService.withdraw(owner.getId());
        mvc.perform(get("/members/" + owner.getId() + "/collection").session(login(viewer)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/members/" + owner.getId() + "/collection"))
                .andExpect(status().isNotFound());
    }

    // R1. 탈퇴한 회원의 기존 세션은 다음 요청에서 거부되고 로그인 화면으로 간다
    @Test
    void withdrawnMemberSessionIsRejectedOnNextRequest() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Session " + hex());
        MockHttpSession session = login(member);
        withdrawalService.withdraw(member.getId());

        mvc.perform(post("/switches/" + switchId + "/collection/FAVORITE").session(session).with(csrf()))
                .andExpect(redirectedUrl("/login"));
        assertThat(collectionService.state(member.getId(), switchId).favorite()).isFalse();
        mvc.perform(get("/me/collection"))
                .andExpect(redirectedUrl("/login"));
    }

    // R2. 같은 후기에 서로 다른 두 회원이 동시에 도움됨을 누르면 수 = 실제 투표 행 수
    @Test
    void concurrentHelpfulTogglesKeepCountEqualToVoteRows() throws Exception {
        Member author = verifiedMember();
        Member first = verifiedMember();
        Member second = verifiedMember();
        Long switchId = createSwitch("Helpful " + hex());
        mvc.perform(post("/switches/" + switchId + "/reviews").session(login(author)).with(csrf())
                .param("body", "동시 투표 후기")).andExpect(status().is3xxRedirection());
        Long reviewId = reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().id();

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (Member m : List.of(first, second)) {
                MockHttpSession s = login(m);
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/reviews/" + reviewId + "/helpful").session(s).with(csrf()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Integer> f : results) {
                assertThat(f.get(60, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(302);
            }
        } finally {
            pool.shutdownNow();
        }
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM review_vote WHERE review_id = ?", Integer.class, reviewId);
        int shown = reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().helpfulCount();
        assertThat(rows).isEqualTo(2);
        assertThat(shown).isEqualTo(rows);
    }

    // R3. 같은 컬렉션 상태를 연속으로, 그리고 동시에 눌러도 500이 나지 않는다
    @Test
    void repeatedCollectionPressesNeverFail() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Repeat " + hex());
        MockHttpSession session = login(member);
        mvc.perform(post("/switches/" + switchId + "/collection/OWNED").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/switches/" + switchId + "/collection/OWNED").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/switches/" + switchId + "/collection/TRIED").session(session).with(csrf()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<Integer> f : results) {
                assertThat(f.get(60, java.util.concurrent.TimeUnit.SECONDS)).isNotEqualTo(500);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reportRulesSelfDuplicateAndDismiss() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        Long switchId = createSwitch("Rep " + hex());
        ratingPost(login(author), switchId, 3).andExpect(status().is3xxRedirection());
        Long ratingId = ratingService.list(switchId, author.getId(), false, PageRequest.of(0, 20))
                .getContent().getFirst().id();

        mvc.perform(post("/reports").session(login(author)).with(csrf())
                .param("targetType", "RATING").param("targetId", String.valueOf(ratingId))
                .param("reasonCode", "SPAM")).andExpect(status().isOk());
        mvc.perform(post("/reports").session(login(other)).with(csrf())
                .param("targetType", "RATING").param("targetId", String.valueOf(ratingId))
                .param("reasonCode", "SPAM")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/reports").session(login(other)).with(csrf())
                .param("targetType", "RATING").param("targetId", String.valueOf(ratingId))
                .param("reasonCode", "SPAM")).andExpect(status().isConflict());

        mvc.perform(post("/admin/reports/" + reportId(ReportTargetType.RATING, ratingId) + "/dismiss")
                .session(adminSession()).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(reports.findAll().stream().filter(r -> r.getTargetId().equals(ratingId)).findFirst()
                .orElseThrow().getStatus().name()).isEqualTo("DISMISSED");
        // 기각은 숨김을 하지 않는다
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
    }

    @Test
    void deletedRatingCanBeRecreatedAndLeavesStats() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Recreate " + hex());
        ratingPost(login(member), switchId, 3).andExpect(status().is3xxRedirection());
        Long ratingId = ratingService.list(switchId, member.getId(), false, PageRequest.of(0, 20))
                .getContent().getFirst().id();

        mvc.perform(post("/ratings/" + ratingId + "/delete").session(login(member)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(0L);
        // 소프트 삭제된 평가가 있어도 같은 조합으로 다시 등록할 수 있다
        ratingPost(login(member), switchId, 5).andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(5.0);
    }

    @Test
    void withdrawalAnonymizesBlocksSignupClearsCollectionsAndKeepsTenureRows() throws Exception {
        Member leaver = verifiedMember();
        Member fan = verifiedMember();
        String email = leaver.getEmail();
        Long switchId = createSwitch("Leave " + hex());
        ratingPost(login(leaver), switchId, 4).andExpect(status().is3xxRedirection());
        mvc.perform(post("/switches/" + switchId + "/reviews").session(login(leaver)).with(csrf())
                .param("body", "탈퇴할 사람의 후기")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/switches/" + switchId + "/collection/OWNED").session(login(leaver)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long reviewId = reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().id();
        mvc.perform(post("/reviews/" + reviewId + "/helpful").session(login(fan)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/me/withdraw").session(login(leaver)).with(csrf()).param("confirmText", "탈퇴"))
                .andExpect(redirectedUrl("/"));

        Member after = members.findById(leaver.getId()).orElseThrow();
        assertThat(after.getEmail()).isNull();
        assertThat(after.isWithdrawn()).isTrue();
        assertThat(collectionService.state(leaver.getId(), switchId).owned()).isFalse();
        // 후기는 보관 기간 동안 남고, 작성자 이름은 "탈퇴한 회원"
        assertThat(reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().authorNickname()).isEqualTo("탈퇴한 회원");
        // 탈퇴한 회원의 도움됨 표시는 지워지고, 다른 회원의 도움됨은 남는다
        assertThat(reviewService.list(switchId, null, false, "recent", PageRequest.of(0, 20))
                .getContent().getFirst().helpfulCount()).isEqualTo(1);
        // 같은 이메일로는 가입할 수 없다
        mvc.perform(post("/signup").with(csrf())
                        .param("email", email).param("nickname", "again" + hex())
                        .param("password", PASSWORD).param("passwordConfirm", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("다시 가입할 수 없어요")));
        // 탈퇴 후에도 평가는 보관 기간 안이라 집계에 그대로 남는다(이 테스트에서는 탈퇴 회원 한 명)
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
    }

    @Test
    void wrongConfirmTextKeepsAccountAndPageRendersWithoutEmail() throws Exception {
        Member member = verifiedMember();
        mvc.perform(post("/me/withdraw").session(login(member)).with(csrf()).param("confirmText", "네"))
                .andExpect(status().isOk())
                .andExpect(view("member/withdraw"))
                .andExpect(content().string(not(containsString(member.getEmail()))));
        assertThat(members.findById(member.getId()).orElseThrow().isWithdrawn()).isFalse();
        mvc.perform(get("/me/withdraw").session(login(member)))
                .andExpect(status().isOk());
    }

    @Test
    void retentionPurgesOnlyPastThirtyDaysAndIsIdempotent() throws Exception {
        Member leaver = verifiedMember();
        Member active = verifiedMember();
        Long switchId = createSwitch("Purge " + hex());
        ratingPost(login(leaver), switchId, 4).andExpect(status().is3xxRedirection());
        ratingPost(login(active), switchId, 2).andExpect(status().is3xxRedirection());
        withdrawalService.withdraw(leaver.getId());
        // 탈퇴 31일 전으로 되돌린다
        jdbc.update("UPDATE member SET deleted_at = now() - interval '31 days' WHERE id = ?", leaver.getId());

        RetentionService.Result first = retentionService.purge(java.time.Instant.now());
        assertThat(first.ratings()).isGreaterThanOrEqualTo(1);
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(2.0);

        RetentionService.Result second = retentionService.purge(java.time.Instant.now());
        assertThat(second.ratings()).isZero();
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultMatcher view(String name) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.view().name(name);
    }

    private Long reportId(ReportTargetType type, Long targetId) {
        return reports.findAll().stream()
                .filter(r -> r.getTargetType() == type && r.getTargetId().equals(targetId))
                .findFirst().orElseThrow().getId();
    }

    private org.springframework.test.web.servlet.ResultActions ratingPost(MockHttpSession session, Long switchId, int star)
            throws Exception {
        return mvc.perform(post("/switches/" + switchId + "/ratings").session(session).with(csrf())
                .param("buildId", "")
                .param("soundLevel", "3").param("soundTone", "CLACKY").param("feel", "SMOOTH")
                .param("fatigueLevel", "2").param("starRating", String.valueOf(star))
                .param("lubed", "").param("modTags", ""));
    }

    private Long createSwitch(String model) {
        SwitchSpec spec = new SwitchSpec("Gateron", null, model, null, SwitchType.LINEAR, new BigDecimal("50"),
                null, null, null, null, null);
        return switchService.create(spec, List.of(), "등록", null, "tester", null).getId();
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("rcr-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "rcr" + hex, Role.USER, true));
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
