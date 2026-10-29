package com.switchbase.keyswitch;

public enum SwitchType {
    LINEAR("리니어"),
    TACTILE("택타일"),
    CLICKY("클리키");

    private final String label;

    SwitchType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
