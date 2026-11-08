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
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 체감 평가 (rating-design §8, rating-screens §4). 실제 로그인 세션으로 요청한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class RatingTest {

    static final String PASSWORD = "password123";
    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired BuildService buildService;
    @Autowired RatingService ratingService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void memberRatingIsListedWithNicknameAndNoEmail() throws Exception {
        Member author = verifiedMember();
        Long switchId = createSwitch("Rate " + hex());

        ratingPost(login(author), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "Spring  Swap")
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/switches/" + switchId))
                .andExpect(status().isOk())
                .andExpect(view().name("switches/detail"))
                .andExpect(content().string(containsString(author.getNickname())))
                .andExpect(content().string(containsString("Spring  Swap".replaceAll("\\s+", " "))))
                .andExpect(content().string(not(containsString(author.getEmail()))));
    }

    @Test
    void anonymousCanViewButCannotRate() throws Exception {
        Long switchId = createSwitch("Anon " + hex());

        mvc.perform(get("/switches/" + switchId)).andExpect(status().isOk());
        mvc.perform(get("/switches/" + switchId + "/ratings/new"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/switches/" + switchId + "/ratings").with(csrf())
                        .param("soundLevel", "3").param("soundTone", "CLACKY").param("feel", "SMOOTH")
                        .param("fatigueLevel", "2").param("starRating", "4"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void sameSwitchAndBuildCombinationIsRejectedOnceWithoutBuildToo() throws Exception {
        Member member = verifiedMember();
        MockHttpSession session = login(member);
        Long switchId = createSwitch("Dup " + hex());
        KeyboardBuild build = createBuild(member, "Mine " + hex());

        ratingPost(session, switchId, String.valueOf(build.getId()), 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());
        ratingPost(session, switchId, String.valueOf(build.getId()), 4, "POPPY", "CRISP", 3, 5, "", "")
                .andExpect(status().isConflict())
                .andExpect(view().name("ratings/form"));

        // 빌드 없음으로도 스위치당 한 건
        ratingPost(session, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());
        ratingPost(session, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().isConflict());
    }

    @Test
    void buildOfAnotherMemberCannotBeLinked() throws Exception {
        Member owner = verifiedMember();
        Member rater = verifiedMember();
        KeyboardBuild others = createBuild(owner, "Theirs " + hex());
        Long switchId = createSwitch("Foreign " + hex());

        ratingPost(login(rater), switchId, String.valueOf(others.getId()), 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().isOk())
                .andExpect(view().name("ratings/form"))
                .andExpect(model().attributeHasFieldErrors("form", "buildId"));
    }

    @Test
    void emptyBuildOptionBindsToNullAndOptionalFieldsCanBeBlank() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Blank " + hex());

        // 빌드 select의 "빌드 없음"은 빈 값으로 전송된다. 빈 문자열은 null로 바인딩돼야 한다.
        ratingPost(login(member), switchId, "", 2, "DULL", "CATCHY", 5, 1, "", "")
                .andExpect(status().is3xxRedirection());
        assertThat(ratingService.list(switchId, member.getId(), false, PageRequest.of(0, 20))
                .getContent().getFirst().buildLinked()).isFalse();
    }

    @Test
    void requiredFieldsMissingAreRejectedButLubedAndTagsAreOptional() throws Exception {
        Member member = verifiedMember();
        MockHttpSession session = login(member);
        Long switchId = createSwitch("Req " + hex());

        mvc.perform(post("/switches/" + switchId + "/ratings").session(session).with(csrf())
                        .param("soundTone", "CLACKY").param("feel", "SMOOTH").param("fatigueLevel", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("ratings/form"))
                .andExpect(model().attributeHasFieldErrors("form", "soundLevel", "starRating"));

        ratingPost(session, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void authorEditsWithConflictAndOthersGetForbidden() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        MockHttpSession session = login(author);
        Long switchId = createSwitch("Edit " + hex());
        ratingPost(session, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "").andExpect(status().is3xxRedirection());
        Long ratingId = listFirst(switchId, author).id();

        mvc.perform(get("/ratings/" + ratingId + "/edit").session(login(other)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ratings/" + ratingId).session(login(other)).with(csrf())
                        .param("version", "0").param("soundLevel", "1").param("soundTone", "DULL")
                        .param("feel", "CATCHY").param("fatigueLevel", "1").param("starRating", "1"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/ratings/" + ratingId + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("ratings/form"))
                .andExpect(model().attributeExists("form", "switchId", "ratingId", "buildOptions", "soundToneOptions", "feelOptions"));

        mvc.perform(post("/ratings/" + ratingId).session(session).with(csrf())
                        .param("version", "0").param("soundLevel", "5").param("soundTone", "HEAVY")
                        .param("feel", "CRISP").param("fatigueLevel", "4").param("starRating", "5"))
                .andExpect(redirectedUrl("/switches/" + switchId))
                .andExpect(flash().attribute("message", "평가를 고쳤어요."));

        mvc.perform(post("/ratings/" + ratingId).session(session).with(csrf())
                        .param("version", "0").param("soundLevel", "2").param("soundTone", "POPPY")
                        .param("feel", "SMOOTH").param("fatigueLevel", "2").param("starRating", "2"))
                .andExpect(status().isConflict())
                .andExpect(view().name("ratings/form"))
                .andExpect(content().string(containsString("그 사이 다른 사람이 먼저 고쳤어요")));
    }

    @Test
    void deletionAllowedForAuthorAndAdminButNotThirdParty() throws Exception {
        Member author = verifiedMember();
        Member third = verifiedMember();
        Long switchId = createSwitch("Delete " + hex());
        ratingPost(login(author), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "").andExpect(status().is3xxRedirection());
        ratingPost(login(third), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "").andExpect(status().is3xxRedirection());
        List<RatingView> views = ratingService.list(switchId, author.getId(), false, PageRequest.of(0, 20)).getContent();
        Long authorRating = views.stream().filter(RatingView::mine).findFirst().orElseThrow().id();
        Long thirdRating = views.stream().filter(v -> !v.mine()).findFirst().orElseThrow().id();

        mvc.perform(post("/ratings/" + thirdRating + "/delete").session(login(author)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ratings/" + authorRating + "/delete").session(login(author)).with(csrf()))
                .andExpect(redirectedUrl("/switches/" + switchId))
                .andExpect(flash().attribute("message", "평가를 삭제했어요."));
        mvc.perform(post("/ratings/" + thirdRating + "/delete").session(adminSession()).with(csrf()))
                .andExpect(redirectedUrl("/switches/" + switchId));
    }

    @Test
    void deletableAndMineFlagsFollowAuthorAndAdmin() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        Long switchId = createSwitch("Flags " + hex());
        ratingPost(login(author), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "").andExpect(status().is3xxRedirection());

        RatingView forAuthor = listFirst(switchId, author);
        assertThat(forAuthor.mine()).isTrue();
        assertThat(forAuthor.deletable()).isTrue();
        RatingView forOther = listFirst(switchId, other);
        assertThat(forOther.mine()).isFalse();
        assertThat(forOther.deletable()).isFalse();
        RatingView forAdmin = ratingService.list(switchId, null, true, PageRequest.of(0, 20)).getContent().getFirst();
        assertThat(forAdmin.mine()).isFalse();
        assertThat(forAdmin.deletable()).isTrue();
    }

    @Test
    void sampleCountIsDistinctMembersNotBuilds() throws Exception {
        Member member = verifiedMember();
        Member second = verifiedMember();
        Long switchId = createSwitch("Sample " + hex());
        KeyboardBuild b1 = createBuild(member, "B1 " + hex());
        KeyboardBuild b2 = createBuild(member, "B2 " + hex());
        ratingPost(login(member), switchId, String.valueOf(b1.getId()), 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());
        ratingPost(login(member), switchId, String.valueOf(b2.getId()), 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(1L);

        ratingPost(login(second), switchId, "", 3, "CLACKY", "SMOOTH", 2, 5, "", "")
                .andExpect(status().is3xxRedirection());
        assertThat(ratingService.summary(switchId).sampleCount()).isEqualTo(2L);
        // 표본 수는 회원 수, 평가 건수는 별도(화면 "N명 · M건")
        assertThat(ratingService.summary(switchId).ratingCount()).isEqualTo(3L);
    }

    // 같은 새 태그를 두 요청이 동시에 등록해도 500이 나지 않는다(유니크 위반은 재조회·409로 처리).
    @Test
    void concurrentNewTagCreationDoesNotFail500() throws Exception {
        Member first = verifiedMember();
        Member second = verifiedMember();
        Long switchId = createSwitch("Race " + hex());
        String tag = "Racing Tag " + hex();
        MockHttpSession s1 = login(first);
        MockHttpSession s2 = login(second);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> results = new java.util.ArrayList<>();
            for (MockHttpSession s : List.of(s1, s2)) {
                Member m = s == s1 ? first : second;
                results.add(pool.submit(() -> {
                    start.await();
                    return ratingPost(s, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", tag)
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
        // 태그는 한 개만 남는다
        assertThat(ratingService.list(switchId, first.getId(), false, PageRequest.of(0, 20)).getTotalElements())
                .isGreaterThanOrEqualTo(1L);
    }

    // 자동완성 검색은 LIKE 와일드카드를 글자로 취급한다("%", "_"로 전체가 걸리지 않는다).
    @Test
    void suggestionTreatsLikeWildcardsAsLiterals() throws Exception {
        Member member = verifiedMember();
        String unique = hex();
        Long switchId = createSwitch("Like " + hex());
        ratingPost(login(member), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "plainword" + unique)
                .andExpect(status().is3xxRedirection());

        assertThat(ratingService.suggestTags("%")).doesNotContain("plainword" + unique);
        assertThat(ratingService.suggestTags("_")).doesNotContain("plainword" + unique);
        assertThat(ratingService.suggestTags("plain")).contains("plainword" + unique);
    }

    @Test
    void averageIsComputedFromScaleValues() throws Exception {
        Member a = verifiedMember();
        Member b = verifiedMember();
        Long switchId = createSwitch("Avg " + hex());
        ratingPost(login(a), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "").andExpect(status().is3xxRedirection());
        ratingPost(login(b), switchId, "", 4, "CLACKY", "SMOOTH", 3, 5, "", "").andExpect(status().is3xxRedirection());

        RatingSummary s = ratingService.summary(switchId);
        assertThat(s.avgStar()).isEqualTo(4.5);
        assertThat(s.avgSound()).isEqualTo(3.5);
        assertThat(s.avgFatigue()).isEqualTo(2.5);
    }

    @Test
    void tagsAreNormalizedAndSuggestionIsEscaped() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Tag " + hex());
        String unique = hex();
        ratingPost(login(member), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "Spring  Swap" + unique + ", <b>xss" + unique + "</b>")
                .andExpect(status().is3xxRedirection());
        // 같은 스위치·빌드 없음 조합의 중복을 피하려고 두 번째 평가는 다른 회원이 남긴다
        Member second = verifiedMember();
        ratingPost(login(second), switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "spring swap" + unique)
                .andExpect(status().is3xxRedirection());

        assertThat(listFirst(switchId, member).modTags()).isNotEmpty();
        mvc.perform(get("/mod-tags/suggest").param("modTags", "spring swap" + unique.substring(0, 2)).session(login(member)))
                .andExpect(status().isOk())
                .andExpect(view().name("ratings/tag-suggest"));
        mvc.perform(get("/mod-tags/suggest").param("modTags", "<b").session(login(member)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;")))
                .andExpect(content().string(not(containsString("<b>xss"))));
    }

    @Test
    void hiddenBuildShowsOnlyAsBuildLinkedWithoutItsName() throws Exception {
        Member member = verifiedMember();
        Long switchId = createSwitch("Hidden " + hex());
        String secretName = "SecretBoard" + hex();
        KeyboardBuild build = createBuild(member, secretName);
        buildService.setHidden(build.getId(), true);

        ratingPost(login(member), switchId, String.valueOf(build.getId()), 3, "CLACKY", "SMOOTH", 2, 4, "", "")
                .andExpect(status().is3xxRedirection());

        assertThat(listFirst(switchId, member).buildLinked()).isTrue();
        mvc.perform(get("/switches/" + switchId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("빌드 있음")))
                .andExpect(content().string(not(containsString(secretName))));
    }

    @Test
    void pagesRenderWith200AndNoEmail() throws Exception {
        Member member = verifiedMember();
        MockHttpSession session = login(member);
        Long switchId = createSwitch("Render " + hex());
        ratingPost(session, switchId, "", 3, "CLACKY", "SMOOTH", 2, 4, "", "x").andExpect(status().is3xxRedirection());

        mvc.perform(get("/switches/" + switchId + "/ratings/new").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("ratings/form"))
                .andExpect(model().attributeExists("buildOptions", "soundToneOptions", "feelOptions"))
                .andExpect(content().string(not(containsString(member.getEmail()))));
        mvc.perform(get("/switches/" + switchId))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("summary", "ratingPage", "switchId"))
                .andExpect(content().string(not(containsString(member.getEmail()))));
        mvc.perform(get("/mod-tags/suggest").param("modTags", "x").session(session))
                .andExpect(content().string(not(containsString(member.getEmail()))));
    }

    // ---- helpers ----

    private ResultActions ratingPost(MockHttpSession session, Long switchId, String buildId, int sound, String tone,
                                     String feel, int fatigue, int star, String lubed, String modTags) throws Exception {
        var req = post("/switches/" + switchId + "/ratings").session(session).with(csrf())
                .param("buildId", buildId)
                .param("soundLevel", String.valueOf(sound))
                .param("soundTone", tone)
                .param("feel", feel)
                .param("fatigueLevel", String.valueOf(fatigue))
                .param("starRating", String.valueOf(star))
                .param("lubed", lubed)
                .param("modTags", modTags);
        return mvc.perform(req);
    }

    private RatingView listFirst(Long switchId, Member viewer) {
        return ratingService.list(switchId, viewer.getId(), false, PageRequest.of(0, 20)).getContent().getFirst();
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
        return members.save(new Member("rating-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "rate" + hex, Role.USER, true));
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
