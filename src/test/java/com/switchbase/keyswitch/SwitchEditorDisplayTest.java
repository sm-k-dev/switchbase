package com.switchbase.keyswitch;

import com.switchbase.TestcontainersConfiguration;
import com.switchbase.member.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 편집자 표시(wiki-design §3, 0단계): 화면에 관리자 이메일이 나오지 않고, 기존 행은 "초기 관리자"로 나온다.
 * 등록·수정·CSV 가져오기는 닉네임과 회원 id를 함께 남긴다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class SwitchEditorDisplayTest {

    static final String ADMIN_EMAIL = "admin@switchbase.local";
    static final String ADMIN_PASSWORD = "AdminPassw0rd!";
    static final String ADMIN_NICKNAME = "admin";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired SwitchCsvImporter csvImporter;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;

    // 기존 행(V5 이전): 편집자 칸에 이메일이 있고 회원 id는 없다. 화면에는 이메일 대신 "초기 관리자"가 나와야 한다.
    @Test
    void legacyRowShowsInitialAdminAndNeverTheEmail() throws Exception {
        KeyboardSwitch sw = switchService.create(spec("Legacy " + hex()), List.of(), "처음 등록", null, "tester", null);
        jdbc.update("UPDATE switch_revision SET editor = ?, editor_member_id = NULL WHERE switch_id = ?",
                ADMIN_EMAIL, sw.getId());

        mvc.perform(get("/switches/" + sw.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("초기 관리자")))
                .andExpect(content().string(not(containsString(ADMIN_EMAIL))));

        assertThat(switchService.revisionsOf(sw.getId()).get(0).getEditorDisplay()).isEqualTo("초기 관리자");
    }

    // 관리자가 등록하고 수정하면 편집자는 닉네임과 회원 id로 남고, 상세 화면에는 이메일이 나오지 않는다.
    @Test
    void adminCreateAndUpdateRecordNicknameAndMemberIdWithoutEmail() throws Exception {
        Long adminId = members.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        String model = "Admin Edit " + hex();
        MockHttpSession session = adminSession();

        MvcResult created = mvc.perform(post("/admin/switches").session(session).with(csrf())
                        .param("manufacturer", "Gateron")
                        .param("modelName", model)
                        .param("switchType", "LINEAR")
                        .param("actuationForceG", "50")
                        .param("aliases", "")
                        .param("editSummary", "처음 등록"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long id = idFromRedirect(created);

        SwitchRevision first = switchService.revisionsOf(id).get(0);
        assertThat(first.getEditorMemberId()).isEqualTo(adminId);
        assertThat(first.getEditorDisplay()).isEqualTo(ADMIN_NICKNAME);

        mvc.perform(post("/switches/" + id).session(session).with(csrf())
                        .param("manufacturer", "Gateron")
                        .param("modelName", model)
                        .param("switchType", "LINEAR")
                        .param("actuationForceG", "55")
                        .param("aliases", "")
                        .param("editSummary", "입력압 수정")
                        .param("version", "0"))
                .andExpect(status().is3xxRedirection());

        SwitchRevision latest = switchService.revisionsOf(id).get(0);
        assertThat(latest.getRevisionNo()).isEqualTo(2);
        assertThat(latest.getEditorMemberId()).isEqualTo(adminId);
        assertThat(latest.getEditorDisplay()).isEqualTo(ADMIN_NICKNAME);

        mvc.perform(get("/switches/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(ADMIN_EMAIL))));
    }

    // CSV 가져오기도 같은 규칙: 닉네임과 회원 id를 남긴다.
    @Test
    void csvImportRecordsNicknameAndMemberId() throws Exception {
        Long adminId = members.findByEmail(ADMIN_EMAIL).orElseThrow().getId();
        String model = "Csv Edit " + hex();
        String csv = "header\nGateron,,%s,,LINEAR,45,,,,,,\n".formatted(model);

        SwitchCsvImporter.Result result = csvImporter.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), adminId, ADMIN_NICKNAME);
        assertThat(result.created()).isEqualTo(1);

        Long id = switchService.search(new SwitchSearch(model, null, null, null),
                        PageRequest.of(0, 20))
                .getContent().getFirst().getId();
        SwitchRevision rev = switchService.revisionsOf(id).get(0);
        assertThat(rev.getEditorMemberId()).isEqualTo(adminId);
        assertThat(rev.getEditorDisplay()).isEqualTo(ADMIN_NICKNAME);
    }

    private MockHttpSession adminSession() throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .param("email", ADMIN_EMAIL)
                        .param("password", ADMIN_PASSWORD)
                        .with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static Long idFromRedirect(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private static SwitchSpec spec(String model) {
        return new SwitchSpec("Gateron", null, model, null, SwitchType.LINEAR, new BigDecimal("50"),
                null, null, null, null, null);
    }

    private static String hex() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
