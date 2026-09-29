package com.fakesms.sdk;

/**
 * A stand-in for a third-party SMS SDK that ships only as a local binary (no Maven coordinate).
 * The real thing would be an .aar/.jar a vendor emails you; this one exists so the repository can
 * prove that such a binary stays out of every published artifact.
 */
public final class FakeSmsClient {

    /** Thrown when the "vendor" rejects a request. */
    public static final class SmsException extends Exception {
        public SmsException(String message) {
            super(message);
        }
    }

    /** The only code this fake accepts. */
    public static final String ACCEPTED_CODE = "000000";

    /** Sends a code to [phone] and returns the challenge id. */
    public String requestCode(String phone) throws SmsException {
        if (phone == null || phone.trim().isEmpty()) {
            throw new SmsException("phone must not be blank");
        }
        return "fake-" + Integer.toHexString(phone.hashCode());
    }

    /** Whether [code] is right for [challengeId]. */
    public boolean checkCode(String challengeId, String code) throws SmsException {
        if (challengeId == null || !challengeId.startsWith("fake-")) {
            throw new SmsException("unknown challenge");
        }
        return ACCEPTED_CODE.equals(code);
    }
}
