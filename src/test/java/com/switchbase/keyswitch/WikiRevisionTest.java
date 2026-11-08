package com.switchbase.keyswitch;

import com.switchbase.TestcontainersConfiguration;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** 위키 수정·되돌리기·비교·최근 변경 (wiki-design §8). 실제 로그인 세션으로 요청한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class WikiRevisionTest {

    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";
    static final String PASSWORD = "password123";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    // ---- 수정 ----

    @Test
    void memberEditIsPublishedImmediatelyWithEditorRecorded() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Edit " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);

        editPost(id, session, "55", "입력압 수정", 0L)
                .andExpect(redirectedUrl("/switches/" + id));

        SwitchRevision latest = switchService.revisionsOf(id).getFirst();
        assertThat(latest.getRevisionNo()).isEqualTo(2);
        assertThat(latest.getEditorMemberId()).isEqualTo(author.getId());
        assertThat(latest.getEditorDisplay()).isEqualTo(author.getNickname());
        assertThat(switchService.get(id).getSpec().actuationForceG()).isEqualByComparingTo("55");
    }

    @Test
    void anonymousEditIsSentToLogin() throws Exception {
        Long id = createSwitch(verifiedMember(), "Anon " + hex());

        mvc.perform(post("/switches/" + id).with(csrf())
                        .param("manufacturer", "Gateron").param("modelName", "x").param("switchType", "LINEAR")
                        .param("editSummary", "x").param("version", "0"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/switches/" + id + "/edit"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void staleVersionShowsConflictAndAddsNoRevision() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Stale " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "먼저 고침", 0L).andExpect(redirectedUrl("/switches/" + id));

        editPost(id, session, "60", "늦게 고침", 0L)
                .andExpect(status().isConflict())
                .andExpect(view().name("switches/edit"))
                .andExpect(model().attributeExists("form", "switchId", "types"))
                .andExpect(content().string(containsString("그 사이 다른 사람이 먼저 고쳤어요")))
                .andExpect(content().string(containsString("리니어")));
        assertThat(switchService.revisionsOf(id)).hasSize(2);
    }

    // ---- 되돌리기 ----

    @Test
    void authorRevertsLatestRestoresSpecKeepsAliasesAndAddsRevision() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Revert " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "입력압 수정", 0L).andExpect(redirectedUrl("/switches/" + id));
        long version = switchService.get(id).getVersion();

        revertPost(id, 2, session, version, "")
                .andExpect(redirectedUrl("/switches/" + id));

        assertThat(switchService.get(id).getSpec().actuationForceG()).isEqualByComparingTo("50");
        List<SwitchRevision> revisions = switchService.revisionsOf(id);
        assertThat(revisions.getFirst().getRevisionNo()).isEqualTo(3);
        assertThat(revisions.getFirst().getEditSummary()).isEqualTo("#2 수정 취소");
        assertThat(revisions.getFirst().getEditorMemberId()).isEqualTo(author.getId());
        // 별칭은 되돌리지 않는다: 수정 때 넣은 별칭 "Y"가 그대로 남는다
        assertThat(switchService.aliasesOf(id)).extracting(SwitchAlias::getAlias).containsExactly("Y");
    }

    @Test
    void otherMemberCannotRevertIsForbidden() throws Exception {
        Member author = verifiedMember();
        Member other = verifiedMember();
        Long id = createSwitch(author, "Other " + hex());
        editPost(id, login(author.getEmail(), PASSWORD), "55", "수정", 0L);
        long version = switchService.get(id).getVersion();

        revertPost(id, 2, login(other.getEmail(), PASSWORD), version, "")
                .andExpect(status().isForbidden());
        assertThat(switchService.revisionsOf(id)).hasSize(2);
    }

    @Test
    void notLatestRevisionCannotBeReverted() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "NotLatest " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "두 번째", 0L);
        editPost(id, session, "60", "세 번째", 1L);
        long version = switchService.get(id).getVersion();

        revertPost(id, 2, session, version, "")
                .andExpect(status().isConflict())
                .andExpect(view().name("switches/revert"))
                .andExpect(model().attribute("canRevert", false))
                .andExpect(model().attribute("denyReason", "뒤에 다른 수정이 있어서 취소할 수 없어요."))
                .andExpect(model().attributeExists("sw", "target"));
        assertThat(switchService.revisionsOf(id)).hasSize(3);
    }

    @Test
    void adminCanRevertLatestByOthers() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "AdminRevert " + hex());
        editPost(id, login(author.getEmail(), PASSWORD), "55", "수정", 0L);
        long version = switchService.get(id).getVersion();

        revertPost(id, 2, adminSession(), version, "관리자 취소")
                .andExpect(redirectedUrl("/switches/" + id));

        assertThat(switchService.get(id).getSpec().actuationForceG()).isEqualByComparingTo("50");
        assertThat(switchService.revisionsOf(id).getFirst().getEditSummary()).isEqualTo("관리자 취소");
    }

    @Test
    void firstRevisionCannotBeReverted() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "First " + hex());

        revertPost(id, 1, adminSession(), 0L, "")
                .andExpect(status().isConflict())
                .andExpect(view().name("switches/revert"))
                .andExpect(model().attribute("denyReason", "처음 등록은 취소할 수 없어요."))
                .andExpect(model().attributeExists("sw", "target"));
        assertThat(switchService.revisionsOf(id)).hasSize(1);
    }

    // ---- 비교 ----

    @Test
    void compareMarksOnlyChangedFields() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Compare " + hex());
        editPost(id, login(author.getEmail(), PASSWORD), "55", "수정", 0L);

        SwitchRevision from = switchService.revisionAt(id, 1);
        SwitchRevision to = switchService.revisionAt(id, 2);
        List<CompareRow> rows = switchService.compareRows(from, to);

        assertThat(rows).filteredOn(CompareRow::changed).extracting(CompareRow::label)
                .containsExactly("작동압 (g)");
        assertThat(rows).filteredOn(r -> !r.changed()).extracting(CompareRow::label)
                .contains("제조사", "모델명");
    }

    // ---- 최근 변경 ----

    @Test
    void recentChangesAreNewestFirstAndPagedBy20() throws Exception {
        Member author = verifiedMember();
        Long firstId = createSwitch(author, "Recent " + hex());
        for (int i = 0; i < 20; i++) {
            createSwitch(author, "Recent " + hex());
        }

        var page = switchService.recentChanges(PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getContent().getFirst().getCreatedAt())
                .isAfterOrEqualTo(page.getContent().get(1).getCreatedAt());
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(21);
        assertThat(firstId).isNotNull();
    }

    // ---- 모든 화면 200 렌더링 (디자이너 템플릿 검증) ----

    @Test
    void everyWikiPageRendersWith200() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Render " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "수정", 0L);

        mvc.perform(get("/switches/" + id + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("switches/edit"))
                .andExpect(model().attributeExists("form", "switchId", "types"))
                .andExpect(content().string(containsString("리니어")));
        mvc.perform(get("/switches/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("switches/history"))
                .andExpect(model().attributeExists("revisions", "latestNo"));
        mvc.perform(get("/switches/" + id + "/compare").param("from", "1").param("to", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("switches/compare"))
                .andExpect(model().attributeExists("rows", "from", "to"));
        mvc.perform(get("/switches/" + id + "/revisions/2/revert").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("switches/revert"))
                .andExpect(model().attribute("canRevert", true));
        mvc.perform(get("/switches/" + id + "/revisions/1/revert").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canRevert", false))
                .andExpect(model().attribute("previous", (Object) null));
        mvc.perform(get("/changes"))
                .andExpect(status().isOk())
                .andExpect(view().name("changes/list"));
        mvc.perform(get("/admin/switches/new").session(adminSession()))
                .andExpect(status().isOk());
    }

    // R1-a. 값이 같은 수정(no-op)은 version을 올리지 않는다. 그 뒤 옛 화면에서 되돌리기는 최신 검사에서 거부된다.
    @Test
    void noOpEditThenStaleRevertIsRejected() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "NoOp " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "두 번째", 0L);
        long staleVersion = switchService.get(id).getVersion();
        editPost(id, session, "55", "값은 그대로", staleVersion)
                .andExpect(redirectedUrl("/switches/" + id));
        assertThat(switchService.get(id).getVersion()).isEqualTo(staleVersion);

        revertPost(id, 2, session, staleVersion, "")
                .andExpect(status().isConflict())
                .andExpect(view().name("switches/revert"));
        assertThat(switchService.revisionsOf(id)).hasSize(3);
    }

    // R1-b. 같은 version으로 되돌리기 두 건이 동시에 들어오면 한 건만 통과하고 나머지는 409 (500 금지).
    @Test
    void concurrentRevertsWithSameVersionLetOnlyOneThrough() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Race " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "수정", 0L);
        long version = switchService.get(id).getVersion();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return revertPost(id, 2, session, version, "").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(302, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(switchService.revisionsOf(id)).hasSize(3);
        assertThat(switchService.get(id).getSpec().actuationForceG()).isEqualByComparingTo("50");
    }

    // R2. 위키 화면 응답 본문에 회원 이메일이 없어야 한다.
    @Test
    void wikiPagesNeverShowMemberEmail() throws Exception {
        Member author = verifiedMember();
        Long id = createSwitch(author, "Mail " + hex());
        MockHttpSession session = login(author.getEmail(), PASSWORD);
        editPost(id, session, "55", "수정", 0L);
        String email = author.getEmail();

        mvc.perform(get("/switches/" + id + "/history"))
                .andExpect(content().string(not(containsString(email))));
        mvc.perform(get("/changes"))
                .andExpect(content().string(not(containsString(email))));
        mvc.perform(get("/switches/" + id + "/compare").param("from", "1").param("to", "2"))
                .andExpect(content().string(not(containsString(email))));
        mvc.perform(get("/switches/" + id + "/revisions/2/revert").session(session))
                .andExpect(content().string(not(containsString(email))));
        mvc.perform(get("/switches/" + id + "/revisions/1/revert").session(session))
                .andExpect(content().string(not(containsString(email))));
        revertPost(id, 1, session, 1L, "")
                .andExpect(content().string(not(containsString(email))));
    }

    // ---- helpers ----

    private ResultActions editPost(Long id, MockHttpSession session, String force, String summary, long version)
            throws Exception {
        return mvc.perform(post("/switches/" + id).session(session).with(csrf())
                .param("manufacturer", "Gateron")
                .param("modelName", switchService.get(id).getSpec().modelName())
                .param("switchType", "LINEAR")
                .param("actuationForceG", force)
                .param("aliases", "Y")
                .param("editSummary", summary)
                .param("version", String.valueOf(version)));
    }

    private ResultActions revertPost(Long id, int no, MockHttpSession session, long version, String summary)
            throws Exception {
        return mvc.perform(post("/switches/" + id + "/revisions/" + no + "/revert").session(session).with(csrf())
                .param("version", String.valueOf(version))
                .param("editSummary", summary));
    }

    /** 회원이 등록한 스위치. 1번 리비전의 편집자는 author다. 별칭 "X"는 수정 때 "Y"로 바뀐다. */
    private Long createSwitch(Member author, String model) {
        SwitchSpec spec = new SwitchSpec("Gateron", null, model, null, SwitchType.LINEAR, new BigDecimal("50"),
                null, null, null, null, null);
        return switchService.create(spec, List.of("X"), "등록", author.getId(), author.getNickname(), null).getId();
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("wiki-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "wiki" + hex, Role.USER, true));
    }

    private MockHttpSession adminSession() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", email).param("password", password).with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String hex() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
