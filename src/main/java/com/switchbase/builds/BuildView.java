package com.switchbase.builds;

/**
 * 화면용 빌드. 이메일은 넣지 않는다(작성자 표시는 닉네임만, build-design §2).
 * 라벨이 null이면 템플릿이 "미지정"으로 보여 준다.
 */
public record BuildView(
        Long id,
        String displayName,
        String ownerNickname,
        String housingLabel,
        String plateLabel,
        String mountLabel,
        String keycapLabel,
        String memo,
        boolean hidden,
        boolean mine) {

    /** 작성자 표시 없이 만든다(목록 등). 상세처럼 로그인 회원이 필요하면 from(b, viewerId)를 쓴다. */
    public static BuildView from(KeyboardBuild b) {
        return from(b, null);
    }

    /** viewerId가 작성자와 같으면 mine=true (신고 버튼 숨김과 수정 버튼 표시에 쓴다). */
    public static BuildView from(KeyboardBuild b, Long viewerId) {
        String name = b.getName();
        return new BuildView(
                b.getId(),
                name == null ? "내 키보드" : name,
                b.getMember().getNickname(),
                label(BuildPart.HOUSING, b.getHousingMaterial()),
                label(BuildPart.PLATE, b.getPlateMaterial()),
                label(BuildPart.MOUNT, b.getMountType()),
                label(BuildPart.KEYCAP, b.getKeycapMaterial()),
                b.getMemo(),
                b.isHidden(),
                viewerId != null && viewerId.equals(b.getMember().getId()));
    }

    /** 미지정(null)은 null, 목록에 없는 옛 코드는 코드 그대로. */
    private static String label(BuildPart part, String code) {
        if (code == null) {
            return null;
        }
        String label = part.label(code);
        return label == null ? code : label;
    }
}
