package com.switchbase.sounds;

/** 같은 스위치에 같은 영상과 시작 시간의 샘플이 이미 있다. */
public class SoundDuplicateException extends RuntimeException {

    public SoundDuplicateException() {
        super("이미 등록된 샘플이에요. 같은 영상과 시작 시간은 한 번만 올릴 수 있어요.");
    }
}
