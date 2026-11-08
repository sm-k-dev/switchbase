package com.switchbase.builds;

import java.util.List;

/**
 * 빌드의 고정 선택지 항목 (build-design §3). 코드와 표시 이름을 분리한다.
 * 미지정(NULL)은 항상 허용하며 여기 목록에 넣지 않는다.
 */
public enum BuildPart {

    HOUSING("housing", List.of(
            new BuildChoice("ABS", "플라스틱(ABS)"),
            new BuildChoice("PC", "폴리카보네이트(PC)"),
            new BuildChoice("ALUMINUM", "알루미늄"),
            new BuildChoice("BRASS", "황동"),
            new BuildChoice("OTHER", "기타"))),

    PLATE("plate", List.of(
            new BuildChoice("NONE", "없음"),
            new BuildChoice("PC", "폴리카보네이트(PC)"),
            new BuildChoice("FR4", "FR4"),
            new BuildChoice("ALUMINUM", "알루미늄"),
            new BuildChoice("OTHER", "기타"))),

    MOUNT("mount", List.of(
            new BuildChoice("TOP", "탑(Top)"),
            new BuildChoice("GASKET", "가스켓(Gasket)"),
            new BuildChoice("TRAY", "트레이(Tray)"),
            new BuildChoice("PLATE", "플레이트(Plate)"),
            new BuildChoice("PCB", "PCB 마운트"),
            new BuildChoice("OTHER", "기타"))),

    KEYCAP("keycap", List.of(
            new BuildChoice("ABS", "ABS"),
            new BuildChoice("PBT", "PBT"),
            new BuildChoice("OTHER", "기타")));

    private final String key;
    private final List<BuildChoice> choices;

    BuildPart(String key, List<BuildChoice> choices) {
        this.key = key;
        this.choices = choices;
    }

    /** 폼 필드 이름과 모델 키로 쓰는 이름. */
    public String key() {
        return key;
    }

    public List<BuildChoice> choices() {
        return choices;
    }

    /** null(미지정)이거나 목록에 있는 코드면 true. */
    public boolean accepts(String code) {
        return code == null || choices.stream().anyMatch(c -> c.code().equals(code));
    }

    /** 표시 이름. null이면 "미지정", 목록에 없으면 null. */
    public String label(String code) {
        if (code == null) {
            return "미지정";
        }
        return choices.stream().filter(c -> c.code().equals(code)).map(BuildChoice::label).findFirst().orElse(null);
    }
}
