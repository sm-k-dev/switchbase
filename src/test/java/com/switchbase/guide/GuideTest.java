package com.switchbase.guide;

import com.switchbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 입문자 가이드와 감각 용어 사전은 누구나 볼 수 있다 (HANDOFF §4 3단계). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "switchbase.admin.email=admin@switchbase.local",
        "switchbase.admin.password=AdminPassw0rd!"
})
class GuideTest {

    @Autowired MockMvc mvc;

    @Test
    void guideIsPublic() throws Exception {
        mvc.perform(get("/guide"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("입문자 가이드")));
    }

    @Test
    void termsPageIsPublicAndMarkedAsDraft() throws Exception {
        mvc.perform(get("/guide/terms"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("감각 용어 사전")))
                .andExpect(content().string(containsString("초안")));
    }
}
