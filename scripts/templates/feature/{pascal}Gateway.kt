package {{pkg}}.gateway

/**
 * The host calls this feature needs: suspend + SdkResult, plus a callback twin for Java hosts —
 * see `:sdk:features:otp`'s OtpGateway/OtpCallbackGateway for the pattern to follow once this
 * feature has calls to make.
 */
public interface {{pascal}}Gateway
