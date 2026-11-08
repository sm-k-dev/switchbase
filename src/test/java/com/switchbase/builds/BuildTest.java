package com.switchbase.builds;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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

/** 빌드 등록·수정·숨김·목록·상세 (build-design §8, build-screens). 실제 로그인 세션으로 요청한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BuildTest {

    static final String PASSWORD = "password123";

    @Autowired MockMvc mvc;
    @Autowired BuildService buildService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void memberCreatesBuildShownInListAndDetailWithNicknameOnly() throws Exception {
        Member owner = verifiedMember();
        MockHttpSession session = login(owner.getEmail());
        String name = "Board " + hex();

        createPost(session, name, "ABS", "", "", "", "")
                .andExpect(status().is3xxRedirection());

        KeyboardBuild saved = buildService.visibleBuilds(PageRequest.of(0, 20)).getContent().stream()
                .filter(b -> name.equals(b.getName())).findFirst().orElseThrow();
        mvc.perform(get("/builds"))
                .andExpect(status().isOk())
                .andExpect(view().name("builds/list"))
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString(owner.getNickname())))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mvc.perform(get("/builds/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("builds/detail"))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
    }

    @Test
    void blankNameIsStoredAsNullAndShownAsMyKeyboard() throws Exception {
        Member owner = verifiedMember();
        MockHttpSession session = login(owner.getEmail());

        createPost(session, "   ", "", "", "", "", "")
                .andExpect(status().is3xxRedirection());

        KeyboardBuild saved = buildService.visibleBuilds(PageRequest.of(0, 50)).getContent().stream()
                .filter(b -> b.getMember().getId().equals(owner.getId())).findFirst().orElseThrow();
        assertThat(saved.getName()).isNull();
        mvc.perform(get("/builds/" + saved.getId()))
                .andExpect(content().string(containsString("내 키보드")));
    }

    @Test
    void anonymousWritesAreSentToLogin() throws Exception {
        Member owner = verifiedMember();
        KeyboardBuild build = createBuild(owner, "Anon " + hex());

        mvc.perform(post("/builds").with(csrf()).param("memo", "x"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/builds/new"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/builds/" + build.getId() + "/hide").with(csrf()))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/builds/" + build.getId() + "/edit"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void twentyBuildsLimitCountsHiddenToo() throws Exception {
        Member owner = verifiedMember();
        KeyboardBuild first = null;
        for (int i = 0; i < BuildService.MAX_BUILDS_PER_MEMBER; i++) {
            KeyboardBuild b = createBuild(owner, "Limit " + hex());
            if (first == null) first = b;
        }
        buildService.setHidden(first.getId(), true);

        createPost(login(owner.getEmail()), "Twenty-first " + hex(), "", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(view().name("builds/form"))
                .andExpect(model().attributeHasErrors("form"));
        assertThat(buildService.countBuildsOf(owner.getId())).isEqualTo(BuildService.MAX_BUILDS_PER_MEMBER);
    }

    @Test
    void otherMemberCanEditWithoutHistory() throws Exception {
        Member owner = verifiedMember();
        Member other = verifiedMember();
        KeyboardBuild build = createBuild(owner, "Edit " + hex());
        String newName = "Renamed " + hex();

        mvc.perform(post("/builds/" + build.getId()).session(login(other.getEmail())).with(csrf())
                        .param("name", newName).param("memo", "기록 없음").param("version", "0"))
                .andExpect(redirectedUrl("/builds/" + build.getId()));

        KeyboardBuild after = buildService.load(build.getId());
        assertThat(after.getName()).isEqualTo(newName);
        assertThat(after.getVersion()).isEqualTo(1L);
    }

    @Test
    void hideRemovesFromListAndUnhideRestores() throws Exception {
        Member owner = verifiedMember();
        Member other = verifiedMember();
        String name = "Hide " + hex();
        KeyboardBuild build = createBuild(owner, name);

        mvc.perform(post("/builds/" + build.getId() + "/hide").session(login(other.getEmail())).with(csrf()))
                .andExpect(redirectedUrl("/builds"))
                .andExpect(flash().attribute("message", "숨겼어요."));
        mvc.perform(get("/builds"))
                .andExpect(content().string(not(containsString(name))));
        mvc.perform(get("/builds/" + build.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/builds/" + build.getId()).session(login(owner.getEmail())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("숨김")));

        mvc.perform(post("/builds/" + build.getId() + "/unhide").session(login(other.getEmail())).with(csrf()))
                .andExpect(redirectedUrl("/builds/" + build.getId()))
                .andExpect(flash().attribute("message", "다시 공개했어요."));
        mvc.perform(get("/builds/" + build.getId()))
                .andExpect(status().isOk());
        mvc.perform(get("/builds"))
                .andExpect(content().string(containsString(name)));
    }

    // R2. 숨김 빌드의 편집 화면과 수정은 작성자만 열린다. 다른 회원에게는 404.
    @Test
    void otherMemberGetsNotFoundForHiddenBuildEditAndUpdate() throws Exception {
        Member owner = verifiedMember();
        Member other = verifiedMember();
        KeyboardBuild build = createBuild(owner, "Private " + hex());
        buildService.setHidden(build.getId(), true);
        MockHttpSession otherSession = login(other.getEmail());

        mvc.perform(get("/builds/" + build.getId() + "/edit").session(otherSession))
                .andExpect(status().isNotFound());
        mvc.perform(post("/builds/" + build.getId()).session(otherSession).with(csrf())
                        .param("name", "Hijack").param("version", "0"))
                .andExpect(status().isNotFound());
        assertThat(buildService.load(build.getId()).getName()).startsWith("Private");

        mvc.perform(get("/builds/" + build.getId() + "/edit").session(login(owner.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void staleVersionRerendersFormWith409() throws Exception {
        Member owner = verifiedMember();
        KeyboardBuild build = createBuild(owner, "Stale " + hex());
        MockHttpSession session = login(owner.getEmail());
        mvc.perform(post("/builds/" + build.getId()).session(session).with(csrf())
                .param("name", "first").param("version", "0"))
                .andExpect(redirectedUrl("/builds/" + build.getId()));

        mvc.perform(post("/builds/" + build.getId()).session(session).with(csrf())
                        .param("name", "late").param("version", "0"))
                .andExpect(status().isConflict())
                .andExpect(view().name("builds/form"))
                .andExpect(model().attributeExists("form", "buildId", "housingOptions"))
                .andExpect(content().string(containsString("그 사이 다른 사람이 먼저 고쳤어요")));
    }

    @Test
    void choicesOutsideFixedListAreRejectedAndOtherIsAllowed() throws Exception {
        Member owner = verifiedMember();
        MockHttpSession session = login(owner.getEmail());

        createPost(session, "Bad choice " + hex(), "UNKNOWN", "", "", "", "")
                .andExpect(status().isOk())
                .andExpect(view().name("builds/form"))
                .andExpect(model().attributeHasFieldErrors("form", "housingMaterial"));

        createPost(session, "Other choice " + hex(), "OTHER", "OTHER", "OTHER", "OTHER", "설명")
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void listPagesAt20AndExcludesHidden() throws Exception {
        Member owner = verifiedMember();
        String hiddenName = "Secret " + hex();
        KeyboardBuild hidden = createBuild(owner, hiddenName);
        buildService.setHidden(hidden.getId(), true);
        // 한 회원은 20개까지라 여러 회원에 나눠 등록한다
        for (int i = 0; i < 21; i++) {
            createBuild(verifiedMember(), "Paged " + hex());
        }

        var page = buildService.visibleBuilds(PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getContent()).noneMatch(b -> b.isHidden());
        mvc.perform(get("/builds").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(hiddenName))));
    }

    @Test
    void everyBuildPageRendersWith200AndNoEmail() throws Exception {
        Member owner = verifiedMember();
        MockHttpSession session = login(owner.getEmail());
        KeyboardBuild build = createBuild(owner, "Render " + hex());

        mvc.perform(get("/builds/new").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("builds/form"))
                .andExpect(model().attribute("housingOptions", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mvc.perform(get("/builds/" + build.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("form", "buildId"))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mvc.perform(get("/builds/" + build.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mvc.perform(get("/builds"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        // 검증 실패로 폼을 다시 그릴 때도 이메일은 없어야 한다
        createPost(session, "x".repeat(60), "", "", "", "", "")
                .andExpect(view().name("builds/form"))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
    }

    // ---- helpers ----

    private ResultActions createPost(MockHttpSession session, String name, String housing, String plate,
                                     String mount, String keycap, String memo) throws Exception {
        return mvc.perform(post("/builds").session(session).with(csrf())
                .param("name", name)
                .param("housingMaterial", housing)
                .param("plateMaterial", plate)
                .param("mountType", mount)
                .param("keycapMaterial", keycap)
                .param("memo", memo));
    }

    private KeyboardBuild createBuild(Member owner, String name) {
        return buildService.create(owner.getId(),
                new BuildInput(name, "ABS", null, null, null, null));
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("build-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "bld" + hex, Role.USER, true));
    }

    private MockHttpSession login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", email).param("password", PASSWORD).with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String hex() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
