# Integration proof fixture

`../../scripts/verify-integration.sh` copies the current checkout into a unique `build/integration-proof.*` directory, overlays these feature, composition, adapter, and external-consumer projects, registers and publishes the fixtures only in that copy, then compiles/tests them and runs direct/adapter consumers from Maven coordinates with release R8.

The tracked fixture is a reusable example. Generated module registrations, ABI baselines, Maven publications, consumer builds, and R8 mappings live only in the temporary build directory, which the script removes when it exits. The script compares both Git status and source-file digests before and after, preserving pre-existing edits while failing if verification changes the source tree.
