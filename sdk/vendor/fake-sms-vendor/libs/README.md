# fake-sms-sdk.jar

A stand-in for a vendor SDK that ships only as a local binary. It is built from `src/` (JDK 17+):

```bash
javac --release 11 -d /tmp/fakesms libs/src/com/fakesms/sdk/FakeSmsClient.java
jar --create --file libs/fake-sms-sdk.jar --date 2026-01-01T00:00:00Z -C /tmp/fakesms com
```

It is committed because the point of a `vendor` module is a binary with no Maven coordinate.
