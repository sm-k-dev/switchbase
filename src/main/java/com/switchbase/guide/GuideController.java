package com.switchbase.guide;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 입문자 가이드와 감각 용어 사전 (HANDOFF §4 3단계). 내용은 템플릿에 있다. */
@Controller
public class GuideController {

    @GetMapping("/guide")
    String guide() {
        return "guide/index";
    }

    @GetMapping("/guide/terms")
    String terms() {
        return "guide/terms";
    }
}
