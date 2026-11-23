package com.switchbase.keyswitch;

import com.switchbase.builds.BuildPart;
import com.switchbase.member.MemberUserDetails;
import com.switchbase.ratings.RatingService;
import com.switchbase.ratings.RatingSummary;
import com.switchbase.sounds.SoundService;
import com.switchbase.sounds.SoundView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A vs B 비교 (sound-compare-design §5.3, §5.4). 누구나 볼 수 있고, 결과는 저장하지 않는다. */
@Controller
public class SwitchCompareController {

    /** 필터 결과에서 평균을 보여 주는 최소 회원 수(Q6). */
    static final int MIN_MEMBERS_FOR_AVERAGE = 3;

    private final SwitchService switchService;
    private final RatingService ratingService;
    private final SoundService soundService;

    SwitchCompareController(SwitchService switchService, RatingService ratingService, SoundService soundService) {
        this.switchService = switchService;
        this.ratingService = ratingService;
        this.soundService = soundService;
    }

    /** 한쪽 스위치의 체감 요약. averagesHidden은 필터 결과의 표본이 적을 때 true. */
    public record SideSummary(String name, Double sound, Double fatigue, Double star,
                              long members, long ratings, boolean averagesHidden) {
    }

    /** 두 스위치에 모두 있는 채널. */
    public record SharedChannel(String channelName, List<SoundView> aSamples, List<SoundView> bSamples) {
    }

    @GetMapping("/switches/compare")
    String compare(@RequestParam("a") Long a,
                   @RequestParam(value = "b", required = false) Long b,
                   @RequestParam(value = "housing", required = false) String housing,
                   @RequestParam(value = "lubed", required = false) String lubed,
                   @AuthenticationPrincipal MemberUserDetails viewer, Model model) {
        if (b != null && Objects.equals(a, b)) {
            return "redirect:/switches/" + a;
        }

        String housingCode = (housing != null && BuildPart.HOUSING.accepts(housing)) ? housing : null;
        Boolean lubedValue = "true".equals(lubed) ? Boolean.TRUE : "false".equals(lubed) ? Boolean.FALSE : null;
        boolean filterApplied = housingCode != null || lubedValue != null;
        Long viewerId = viewer == null ? null : viewer.getId();

        KeyboardSwitch aSw = switchService.get(a);
        model.addAttribute("a", a);
        model.addAttribute("aName", displayName(aSw));
        model.addAttribute("housingOptions", BuildPart.HOUSING.choices());
        model.addAttribute("housing", housingCode == null ? "" : housingCode);
        model.addAttribute("lubed", lubedValue == null ? "" : lubedValue.toString());
        model.addAttribute("filterApplied", filterApplied);

        if (b == null) {
            model.addAttribute("bSw", null);
            return "switches/versus";
        }

        KeyboardSwitch bSw = switchService.get(b);
        model.addAttribute("b", b);
        model.addAttribute("bName", displayName(bSw));
        model.addAttribute("bSw", bSw);
        model.addAttribute("specRows", specRows(aSw, bSw));
        model.addAttribute("aSummary", sideSummary(a, aSw, housingCode, lubedValue, filterApplied));
        model.addAttribute("bSummary", sideSummary(b, bSw, housingCode, lubedValue, filterApplied));

        List<SoundView.Group> aGroups = soundService.list(a, viewerId);
        List<SoundView.Group> bGroups = soundService.list(b, viewerId);
        List<SharedChannel> shared = sharedChannels(aGroups, bGroups);
        model.addAttribute("sharedChannels", shared);
        model.addAttribute("aGroups", aGroups);
        model.addAttribute("bGroups", bGroups);
        return "switches/versus";
    }

    private SideSummary sideSummary(Long id, KeyboardSwitch sw, String housing, Boolean lubed, boolean filterApplied) {
        if (!filterApplied) {
            RatingSummary s = ratingService.summary(id);
            return new SideSummary(displayName(sw), s.avgSound(), s.avgFatigue(), s.avgStar(),
                    orZero(s.sampleCount()), orZero(s.ratingCount()), false);
        }
        RatingSummary s = ratingService.filteredSummary(id, housing, lubed);
        long members = orZero(s.sampleCount());
        boolean hidden = members < MIN_MEMBERS_FOR_AVERAGE;
        return new SideSummary(displayName(sw),
                hidden ? null : s.avgSound(), hidden ? null : s.avgFatigue(), hidden ? null : s.avgStar(),
                members, orZero(s.ratingCount()), hidden);
    }

    /** 같은 채널 샘플을 찾는다. 공통 채널이 없으면 빈 목록이고, 화면은 각자의 채널 목록을 보여 준다. */
    static List<SharedChannel> sharedChannels(List<SoundView.Group> aGroups, List<SoundView.Group> bGroups) {
        Map<String, SoundView.Group> byName = new LinkedHashMap<>();
        bGroups.forEach(g -> byName.put(g.channelName(), g));
        List<SharedChannel> result = new ArrayList<>();
        for (SoundView.Group ga : aGroups) {
            SoundView.Group gb = byName.get(ga.channelName());
            if (gb != null) {
                result.add(new SharedChannel(ga.channelName(), ga.samples(), gb.samples()));
            }
        }
        return result;
    }

    static List<CompareRow> specRows(KeyboardSwitch a, KeyboardSwitch b) {
        List<CompareRow> rows = new ArrayList<>();
        rows.add(row("제조사", a.getSpec().manufacturer(), b.getSpec().manufacturer()));
        rows.add(row("브랜드", a.getSpec().brand(), b.getSpec().brand()));
        rows.add(row("모델", modelOf(a), modelOf(b)));
        rows.add(row("종류", a.getSpec().switchType().getLabel(), b.getSpec().switchType().getLabel()));
        rows.add(row("입력압", force(a.getSpec().actuationForceG()), force(b.getSpec().actuationForceG())));
        rows.add(row("바닥압", force(a.getSpec().bottomOutForceG()), force(b.getSpec().bottomOutForceG())));
        rows.add(row("입력 지점", mm(a.getSpec().preTravelMm()), mm(b.getSpec().preTravelMm())));
        rows.add(row("키 깊이", mm(a.getSpec().totalTravelMm()), mm(b.getSpec().totalTravelMm())));
        rows.add(row("출시", a.getSpec().releaseYear() == null ? null : a.getSpec().releaseYear().toString(),
                b.getSpec().releaseYear() == null ? null : b.getSpec().releaseYear().toString()));
        return rows;
    }

    private static CompareRow row(String label, String from, String to) {
        String f = from == null || from.isBlank() ? "-" : from;
        String t = to == null || to.isBlank() ? "-" : to;
        return new CompareRow(label, f, t, !f.equals(t));
    }

    private static String modelOf(KeyboardSwitch sw) {
        String variant = sw.getSpec().variant();
        return variant == null || variant.isBlank()
                ? sw.getSpec().modelName()
                : sw.getSpec().modelName() + " (" + variant + ")";
    }

    private static String displayName(KeyboardSwitch sw) {
        return sw.getSpec().manufacturer() + " " + modelOf(sw);
    }

    private static String force(java.math.BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString() + "g";
    }

    private static String mm(java.math.BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString() + "mm";
    }

    private static long orZero(Long v) {
        return v == null ? 0L : v;
    }
}
