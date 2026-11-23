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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A vs B 비교 화면과 기존 버전 비교 화면의 회귀 (sound-compare-design §5.3, §5.4). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class SwitchCompareTest {

    static final String PASSWORD = "password123";

    @Autowired MockMvc mvc;
    @Autowired SwitchService switchService;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void compareShowsBothSwitchesAndTheirSpecs() throws Exception {
        String modelA = "CmpA " + hex();
        String modelB = "CmpB " + hex();
        Long a = createSwitch(modelA);
        Long b = createSwitch(modelB);

        mvc.perform(get("/switches/compare").param("a", a.toString()).param("b", b.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(modelA)))
                .andExpect(content().string(containsString(modelB)))
                .andExpect(content().string(containsString("리니어")));
    }

    @Test
    void comparingSameSwitchRedirectsToDetail() throws Exception {
        Long a = createSwitch("Same " + hex());
        mvc.perform(get("/switches/compare").param("a", a.toString()).param("b", a.toString()))
                .andExpect(redirectedUrl("/switches/" + a));
    }

    @Test
    void filteredAveragesAreHiddenWhenFewerThanThreeMembers() throws Exception {
        Long a = createSwitch("FilA " + hex());
        Long b = createSwitch("FilB " + hex());

        mvc.perform(get("/switches/compare").param("a", a.toString()).param("b", b.toString())
                        .param("housing", "ABS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("표본이 적어 평균을 숨겼어요")));
    }

    @Test
    void sharedChannelSamplesAreShownSideBySide() throws Exception {
        Long a = createSwitch("ChA " + hex());
        Long b = createSwitch("ChB " + hex());
        MockHttpSession session = login(verifiedMember());
        String channel = "채널비교" + hex();
        postSample(session, a, "https://youtu.be/dQw4w9WgXcQ", "0:10", channel);
        postSample(session, b, "https://youtu.be/aaaaaaaaaaa", "0:20", channel);

        mvc.perform(get("/switches/compare").param("a", a.toString()).param("b", b.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(channel)))
                .andExpect(content().string(not(containsString("같은 채널 샘플이 없어요"))));
    }

    @Test
    void versionCompareStillRendersAfterSharingTheNameWithSoundCompare() throws Exception {
        Long a = createSwitch("Ver " + hex());
        mvc.perform(get("/switches/" + a + "/compare").param("from", "1").param("to", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("비교")));
    }

    @Test
    void detailFilterShowsSmallSampleMessageWhenNoRatingsMatch() throws Exception {
        Long a = createSwitch("DtlF " + hex());

        mvc.perform(get("/switches/" + a).param("housing", "ABS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("빌드 조건으로 좁혀 보기")))
                .andExpect(content().string(containsString("표본이 3명 미만이라 평균을 숨겼어요")));
    }

    @Test
    void detailIgnoresUnknownHousingCode() throws Exception {
        Long a = createSwitch("DtlU " + hex());

        mvc.perform(get("/switches/" + a).param("housing", "NOPE"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("표본이 3명 미만이라 평균을 숨겼어요"))));
    }

    private void postSample(MockHttpSession session, Long switchId, String youtube, String start, String channel)
            throws Exception {
        mvc.perform(post("/switches/" + switchId + "/sounds").session(session).with(csrf())
                .param("youtube", youtube).param("startTime", start).param("channelName", channel)
                .param("conditions", "CASE_OPEN")).andExpect(status().is3xxRedirection());
    }

    private Long createSwitch(String model) {
        SwitchSpec spec = new SwitchSpec("Gateron", null, model, null, SwitchType.LINEAR,
                new BigDecimal("50"), null, null, null, null, null);
        return switchService.create(spec, List.of(), "등록", null, "tester", null).getId();
    }

    private Member verifiedMember() {
        String hex = hex();
        return members.save(new Member("cmp-" + hex + "@example.com", passwordEncoder.encode(PASSWORD),
                "cmp" + hex, Role.USER, true));
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
