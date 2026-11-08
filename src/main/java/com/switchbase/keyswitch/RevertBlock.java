package com.switchbase.keyswitch;

/** 되돌리기를 막는 이유. 화면 문구는 wiki-screens.md §3. */
public enum RevertBlock {
    /** 권한 없음: 작성자 본인이나 관리자가 아님 */
    NOT_ALLOWED,
    /** 뒤에 다른 수정이 있어 최신이 아님 */
    NOT_LATEST,
    /** 처음 등록(리비전 1)은 취소할 수 없음 */
    FIRST_REVISION
}
