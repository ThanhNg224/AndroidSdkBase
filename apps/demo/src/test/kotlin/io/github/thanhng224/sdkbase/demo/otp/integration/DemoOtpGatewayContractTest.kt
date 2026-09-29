package io.github.thanhng224.sdkbase.demo.otp.integration

import io.github.thanhng224.sdkbase.core.testing.GatewayContract
import io.github.thanhng224.sdkbase.core.testing.assertSuccess
import org.junit.Test

/**
 * The worked example for hosts: run your own gateway through [GatewayContract]. The fake delays
 * 400 ms per call, so the timeouts here are well above that.
 */
class DemoOtpGatewayContractTest {

    private val gateway = FakeOtpGateway()

    @Test
    fun `requestOtp returns a result`() {
        GatewayContract.assertReturns(timeoutMillis = 3_000) { gateway.requestOtp("+84901234567") }.assertSuccess()
    }

    @Test
    fun `verifyOtp returns a result`() {
        GatewayContract.assertReturns(timeoutMillis = 3_000) { gateway.verifyOtp("demo-challenge", "123456") }
            .assertSuccess()
    }

    @Test
    fun `requestOtp stops when cancelled`() {
        GatewayContract.assertCancellable(timeoutMillis = 3_000) { gateway.requestOtp("+84901234567") }
    }

    @Test
    fun `verifyOtp stops when cancelled`() {
        GatewayContract.assertCancellable(timeoutMillis = 3_000) { gateway.verifyOtp("demo-challenge", "123456") }
    }
}
