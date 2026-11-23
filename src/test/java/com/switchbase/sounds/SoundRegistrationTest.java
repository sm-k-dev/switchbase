package com.switchbase.sounds;

import com.switchbase.TestcontainersConfiguration;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 타건음 샘플 등록(권한·중복·잘못된 입력)과 상세 목록의 숨김 규칙 (sound-compare-design §4, §5). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class SoundRegistrationTest {

    static final String PASSWORD = "password123";
    static final String VIDEO = "https://youtu.be/dQw4w9WgXcQ";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void anonymousVisitorIsSentToLoginForNewForm() throws Exception {
        Long switchId = createSwitch();
        mvc.perform(get("/switches/" + switchId + "/sounds/new"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anonymousVisitorCannotPostSample() throws Exception {
        Long switchId = createSwitch();
        mvc.perform(post("/switches/" + switchId + "/sounds").with(csrf())
                        .param("youtube", VIDEO).param("channelName", "채널A"))
                .andExpect(redirectedUrl("/login"));
        assertThat(sampleCount(switchId)).isZero();
    }

    @Test
    void memberRegistersSampleAndSeesItOnDetailPage() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession session = login(verifiedMember());

        mvc.perform(post("/switches/" + switchId + "/sounds").session(session).with(csrf())
                        .param("youtube", VIDEO).param("startTime", "1:23")
                        .param("channelName", "채널A").param("conditions", "CASE_OPEN"))
                .andExpect(redirectedUrl("/switches/" + switchId));

        mvc.perform(get("/switches/" + switchId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("채널A")))
                .andExpect(content().string(containsString("시작 1:23")));
    }

    @Test
    void sameVideoAndStartTimeIsRejectedWithConflict() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession session = login(verifiedMember());
        postSample(session, switchId, VIDEO, "1:23", "채널A").andExpect(redirectedUrl("/switches/" + switchId));

        postSample(session, switchId, VIDEO, "83", "채널A")
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("이미 등록된 샘플이에요")));
        assertThat(sampleCount(switchId)).isEqualTo(1);
    }

    @Test
    void wrongAddressIsRejectedAndNothingIsSaved() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession session = login(verifiedMember());

        postSample(session, switchId, "https://example.com/watch?v=dQw4w9WgXcQ", "", "채널A")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("유튜브 주소를 확인해 주세요")));
        assertThat(sampleCount(switchId)).isZero();
    }

    @Test
    void badStartTimeIsRejectedAndNothingIsSaved() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession session = login(verifiedMember());

        postSample(session, switchId, VIDEO, "1:99", "채널A")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시작 시간은 1:23 또는 83처럼")));
        assertThat(sampleCount(switchId)).isZero();
    }

    @Test
    void hiddenSampleIsShownOnlyToItsAuthor() throws Exception {
        Long switchId = createSwitch();
        Member author = verifiedMember();
        MockHttpSession authorSession = login(author);
        postSample(authorSession, switchId, VIDEO, "1:23", "채널A").andExpect(redirectedUrl("/switches/" + switchId));
        jdbc.update("UPDATE sound_sample SET hidden = TRUE WHERE switch_id = ?", switchId);

        mvc.perform(get("/switches/" + switchId))
                .andExpect(content().string(not(containsString("시작 1:23"))));
        mvc.perform(get("/switches/" + switchId).session(authorSession))
                .andExpect(content().string(containsString("숨김 처리된 샘플입니다")));
        mvc.perform(get("/switches/" + switchId).session(login(verifiedMember())))
                .andExpect(content().string(not(containsString("시작 1:23"))));
    }

    @Test
    void authorCannotReportOwnSample() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession authorSession = login(verifiedMember());
        postSample(authorSession, switchId, VIDEO, "1:23", "채널A");
        Long sampleId = sampleId(switchId);

        mvc.perform(post("/reports").session(authorSession).with(csrf())
                        .param("targetType", "SOUND").param("targetId", sampleId.toString())
                        .param("reasonCode", "WRONG_INFO"))
                .andExpect(status().isOk());
        assertThat(reportCount(sampleId)).isZero();
    }

    @Test
    void reportedSampleIsHiddenByAdminAndDisappearsForVisitors() throws Exception {
        Long switchId = createSwitch();
        MockHttpSession authorSession = login(verifiedMember());
        postSample(authorSession, switchId, VIDEO, "1:23", "채널A");
        Long sampleId = sampleId(switchId);

        mvc.perform(post("/reports").session(login(verifiedMember())).with(csrf())
                        .param("targetType", "SOUND").param("targetId", sampleId.toString())
                        .param("reasonCode", "WRONG_INFO").param("detail", "재생 안 됨"))
                .andExpect(redirectedUrl("/switches/" + switchId));
        assertThat(reportCount(sampleId)).isEqualTo(1);

        Long reportId = jdbc.queryForObject(
                "SELECT id FROM report WHERE target_type = 'SOUND' AND target_id = ?", Long.class, sampleId);
        mvc.perform(post("/admin/reports/" + reportId + "/hide").session(adminSession()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/switches/" + switchId))
                .andExpect(content().string(not(containsString("시작 1:23"))));
        mvc.perform(get("/switches/" + switchId).session(authorSession))
                .andExpect(content().string(containsString("숨김 처리된 샘플입니다")));
    }

    private Long sampleId(Long switchId) {
        return jdbc.queryForObject("SELECT id FROM sound_sample WHERE switch_id = ?", Long.class, switchId);
    }

    private int reportCount(Long sampleId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM report WHERE target_type = 'SOUND' AND target_id = ?", Integer.class, sampleId);
        return n == null ? 0 : n;
    }

    private MockHttpSession adminSession() throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", "admin@switchbase.local")
                        .param("password", "AdminPassw0rd!").with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private org.springframework.test.web.servlet.ResultActions postSample(MockHttpSession session, Long switchId,
                                                                   String youtube, String start, String channel)
            throws Exception {
        return mvc.perform(post("/switches/" + switchId + "/sounds").session(session).with(csrf())
                .param("youtube", youtube).param("startTime", start).param("channelName", channel)
                .param("conditions", "CASE_OPEN"));
    }

    private int sampleCount(Long switchId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM sound_sample WHERE switch_id = ? AND deleted_at IS NULL",
                Integer.class, switchId);
        return n == null ? 0 : n;
    }

    private Long createSwitch() {
        SwitchSpec spec = new SwitchSpec("Gateron", null, "Snd " + hex(), null, SwitchType.LINEAR,
                new BigDecimal("50"), null, null, null, null, null);
        return switchService.create(spec, List.of(), "등록", null, "tester", null).getId();
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("snd-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "snd" + hex, Role.USER, true));
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result = mvc.perform(post("/login").param("email", member.getEmail())
                        .param("password", PASSWORD).with(csrf()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String hex() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
