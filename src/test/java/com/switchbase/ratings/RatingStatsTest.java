package com.switchbase.ratings;

import com.switchbase.TestcontainersConfiguration;
import com.switchbase.builds.BuildInput;
import com.switchbase.builds.BuildService;
import com.switchbase.builds.KeyboardBuild;
import com.switchbase.keyswitch.KeyboardSwitch;
import com.switchbase.keyswitch.SwitchService;
import com.switchbase.keyswitch.SwitchSpec;
import com.switchbase.keyswitch.SwitchType;
import com.switchbase.member.Member;
import com.switchbase.member.MemberRepository;
import com.switchbase.member.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 집계 캐시 (stats-design §7). 행이 rating 전체 재계산 결과와 같은지, 동시 저장·재계산·요약 읽기를 확인한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class RatingStatsTest {

    static final String PASSWORD = "password123";
    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired BuildService buildService;
    @Autowired RatingService ratingService;
    @Autowired RatingRepository ratings;
    @Autowired SwitchRatingStatsRepository statsRepo;
    @Autowired RatingStatsService statsService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void rowMatchesFullRecomputeAfterCreateUpdateAndDelete() throws Exception {
        Member a = verifiedMember();
        Member b = verifiedMember();
        Long switchId = createSwitch("Stats " + hex());

        ratingPost(login(a), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4).andExpect(status().is3xxRedirection());
        ratingPost(login(b), switchId, "", 4, "POPPY", "CRISP", 3, 5).andExpect(status().is3xxRedirection());
        assertRowMatchesRatings(switchId);
        assertThat(statsRepo.findById(switchId).orElseThrow().getRatingCount()).isEqualTo(2);
        assertThat(statsRepo.findById(switchId).orElseThrow().getMemberCount()).isEqualTo(2);

        Long aRating = ratingService.list(switchId, a.getId(), false,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
                .filter(RatingView::mine).findFirst().orElseThrow().id();
        mvc.perform(post("/ratings/" + aRating).session(login(a)).with(csrf())
                        .param("version", "0").param("soundLevel", "3").param("soundTone", "CLACKY")
                        .param("feel", "SMOOTH").param("fatigueLevel", "2").param("starRating", "2"))
                .andExpect(status().is3xxRedirection());
        assertRowMatchesRatings(switchId);
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(3.5);

        mvc.perform(post("/ratings/" + aRating + "/delete").session(login(a)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertRowMatchesRatings(switchId);
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);
    }

    @Test
    void sameMemberWithTwoBuildsCountsOneMemberTwoRatings() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Two " + hex());
        KeyboardBuild b1 = createBuild(member, "One " + hex());
        KeyboardBuild b2 = createBuild(member, "Two " + hex());
        ratingPost(login(member), switchId, String.valueOf(b1.getId()), 3, "CLACKY", "SMOOTH", 2, 4)
                .andExpect(status().is3xxRedirection());
        ratingPost(login(member), switchId, String.valueOf(b2.getId()), 3, "CLACKY", "SMOOTH", 2, 4)
                .andExpect(status().is3xxRedirection());

        var row = statsRepo.findById(switchId).orElseThrow();
        assertThat(row.getMemberCount()).isEqualTo(1);
        assertThat(row.getRatingCount()).isEqualTo(2);
        assertRowMatchesRatings(switchId);
    }

    @Test
    void lastRatingDeletedLeavesNullAveragesAndZeroCounts() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Empty " + hex());
        ratingPost(login(member), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4).andExpect(status().is3xxRedirection());
        Long ratingId = ratings.findAll().stream()
                .filter(r -> r.getKeyboardSwitch().getId().equals(switchId)).findFirst().orElseThrow().getId();

        mvc.perform(post("/ratings/" + ratingId + "/delete").session(login(member)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        RatingSummary s = ratingService.summary(switchId);
        assertThat(s.avgSound()).isNull();
        assertThat(s.avgFatigue()).isNull();
        assertThat(s.avgStar()).isNull();
        assertThat(s.sampleCount()).isEqualTo(0L);
        assertThat(s.ratingCount()).isEqualTo(0L);
    }

    @Test
    void switchWithoutRowShowsEmptySummary() {
        Long switchId = createSwitch("NoRow " + hex());
        RatingSummary s = ratingService.summary(switchId);
        assertThat(s).isEqualTo(new RatingSummary(null, null, null, 0L, 0L));
    }

    @Test
    void concurrentSavesBothAreReflectedInTheRow() throws Exception {
        Member first = verifiedMember();
        Member second = verifiedMember();
        Long switchId = createSwitch("Race " + hex());
        MockHttpSession s1 = login(first);
        MockHttpSession s2 = login(second);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new java.util.ArrayList<>();
            results.add(pool.submit(() -> {
                start.await();
                return ratingPost(s1, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4).andReturn().getResponse().getStatus();
            }));
            results.add(pool.submit(() -> {
                start.await();
                return ratingPost(s2, switchId, "", 4, "POPPY", "CRISP", 3, 5).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get(60, TimeUnit.SECONDS)).isEqualTo(302);
            }
        } finally {
            pool.shutdownNow();
        }
        var row = statsRepo.findById(switchId).orElseThrow();
        assertThat(row.getRatingCount()).isEqualTo(2);
        assertThat(row.getMemberCount()).isEqualTo(2);
        assertRowMatchesRatings(switchId);
    }

    @Test
    void summaryIsReadFromRowAndFullRecomputeIsIdempotent() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Read " + hex());
        ratingPost(login(member), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4).andExpect(status().is3xxRedirection());

        // 행을 직접 바꾸면 요약도 그 값을 보인다 = 요약은 행에서 읽는다
        jdbc.update("UPDATE switch_rating_stats SET avg_star = 1.23 WHERE switch_id = ?", switchId);
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(1.23);

        MockHttpSession admin = adminSession();
        mvc.perform(post("/admin/stats/recompute").session(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/switches/import"))
                .andExpect(flash().attribute("message", org.hamcrest.Matchers.startsWith("집계를 다시 계산했어요")));
        assertRowMatchesRatings(switchId);
        double once = ratingService.summary(switchId).avgStar();

        mvc.perform(post("/admin/stats/recompute").session(admin).with(csrf()))
                .andExpect(redirectedUrl("/admin/switches/import"));
        assertThat(ratingService.summary(switchId).avgStar()).isEqualTo(once);
        assertThat(once).isEqualTo(4.0);
    }

    @Test
    void recomputeIsAdminOnly() throws Exception {
        Member member = verifiedMember();
        mvc.perform(post("/admin/stats/recompute").session(login(member)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/stats/recompute").with(csrf()))
                .andExpect(redirectedUrl("/login"));
    }

    // ---- helpers ----

    /** 행 값이 rating 전체 집계와 같다는 것을 확인한다. */
    private void assertRowMatchesRatings(Long switchId) {
        RatingSummary live = ratings.summaryOf(switchId);
        var row = statsRepo.findById(switchId).orElseThrow();
        assertThat(row.getRatingCount()).isEqualTo(live.ratingCount().intValue());
        assertThat(row.getMemberCount()).isEqualTo(live.sampleCount().intValue());
        assertThat(row.toSummary().avgStar()).isEqualTo(live.avgStar() == null ? null : round2(live.avgStar()));
        assertThat(row.toSummary().avgSound()).isEqualTo(live.avgSound() == null ? null : round2(live.avgSound()));
        assertThat(row.toSummary().avgFatigue()).isEqualTo(live.avgFatigue() == null ? null : round2(live.avgFatigue()));
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private ResultActions ratingPost(MockHttpSession session, Long switchId, String buildId, int sound, String tone,
                                     String feel, int fatigue, int star) throws Exception {
        return mvc.perform(post("/switches/" + switchId + "/ratings").session(session).with(csrf())
                .param("buildId", buildId)
                .param("soundLevel", String.valueOf(sound))
                .param("soundTone", tone)
                .param("feel", feel)
                .param("fatigueLevel", String.valueOf(fatigue))
                .param("starRating", String.valueOf(star))
                .param("lubed", "")
                .param("modTags", ""));
    }

    private Long createSwitch(String model) {
        SwitchSpec spec = new SwitchSpec("Gateron", null, model, null, SwitchType.LINEAR, new BigDecimal("50"),
                null, null, null, null, null);
        KeyboardSwitch sw = switchService.create(spec, List.of(), "등록", null, "tester", null);
        return sw.getId();
    }

    private KeyboardBuild createBuild(Member owner, String name) {
        return buildService.create(owner.getId(), new BuildInput(name, "ABS", null, null, null, null));
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("stats-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "stat" + hex, Role.USER, true));
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
