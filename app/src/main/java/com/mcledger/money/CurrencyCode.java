package com.mcledger.money;

public enum CurrencyCode {
    AED(2),
    BHD(3);

    private final int scale;

    CurrencyCode(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}
