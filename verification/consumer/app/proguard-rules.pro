# Consumer R8 rules. This module resolves the SDK from Maven coordinates, exactly like a real
# host, so this build is the shrinker canary: an R8 error here means a published module's own
# consumer-rules.pro is missing a keep rule — the fix belongs there, not here.
