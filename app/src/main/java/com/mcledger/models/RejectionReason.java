package com.mcledger.models;

public enum RejectionReason {
    CURRENCY_MISMATCH,
    VALUE_DATE_AFTER_BOOKED_DAY,
    VALUE_DATE_TOO_OLD,
    ENTRY_NOT_FOUND,
    ALREADY_REVERSED
}
