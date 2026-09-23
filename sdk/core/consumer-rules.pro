# Keep rules this module needs its CONSUMER's R8 run to apply.
# Anything reflective, serialized, or reached only from the host belongs here.

# Strip verbose/debug log calls from a host's minified release build.
-assumenosideeffects class io.github.thanhng224.sdkbase.core.logging.TaggedLogger {
    public void v(kotlin.jvm.functions.Function0);
    public void d(kotlin.jvm.functions.Function0);
}
