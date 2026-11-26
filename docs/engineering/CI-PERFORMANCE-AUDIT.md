# CI performance audit (2026-11-26)

**What this is.** A measured account of where Reflux's hosted CI spends its time, why the Windows libmpv workflow keeps failing on zlib, and a ranked plan for making CI faster **without** loosening any check. Sections 1 to 9 are the audit as written; the owner approved L1, L2, L3, L4, L5 and S2, which are implemented and recorded in section 10 (not yet pushed, so not yet measured on hosted runners). Every duration comes from the GitHub Actions API (job and step timestamps) or from the job logs; every configuration fact from the repository at `9e5769e`. Estimates are labelled as estimates. The test-time history behind it is [B-079](../BETA-BUG-TRACKER.md#b-079).

## 1. Topology

Every push to any branch starts **CI** and the **Release** rehearsal; a push that touches `tools/libmpv/**`, `desktop/app/packaging/libmpv-windows.properties` or the workflow itself also starts **Windows libmpv**. Each workflow's jobs run at once (no `needs` between them, except Release's Windows/Android job, which needs the libmpv job's artifact). Concurrency: a newer push to the same branch cancels the older CI and Release runs.

| Workflow | Job | Runner | Command (abridged) |
| --- | --- | --- | --- |
| CI (`ci.yml`) | Check (ubuntu-24.04) | 4 vCPU | `./gradlew check :tools:cli:installDist -Preflux.android=false`, then `tools/ci/desktop-window-tests.sh` (Xvfb) |
| | Check (windows-2025) | 4 vCPU | the same Gradle command; no window tests (the step is Linux-only) |
| | Android | ubuntu | unit tests, lint, platform API, release APK and bundle, third-party notices |
| | Website | ubuntu | `npm ci`, audit, tests, build, dist check, link check, Playwright layout check, roadmap check |
| | Release tools and documentation links; Workflow lint; Secret scan | ubuntu | under 15 s each |
| Release (`release.yml`) | Windows libmpv from source | ubuntu | restore `build/libmpv` from cache, else build it in Docker; **always** compare with both pins |
| | Linux archive | ubuntu | `releaseInfo :desktop:app:packageDesktopArchive`, provenance |
| | Android, Windows, and provenance (needs libmpv) | ubuntu | signed rehearsal build of everything, provenance |
| Windows libmpv (`libmpv.yml`) | Build and compare with the pin | ubuntu | **always** a full Docker build, then both pin checks; never cached (it proves the pin reproduces) |

## 2. Measured durations

Queue time (job created → started) was **1–3 s for every job** in every run below: waiting for runners costs nothing.

**CI 45811843168** (`54c21a4`, green, wall **57m45s**):

| Job | Run time | Where the time goes |
| --- | --- | --- |
| Check (ubuntu-24.04) | **57m42s** | setup 16 s; packages 48 s; **Build and test 51m26s**; **window tests 5m05s** |
| Check (windows-2025) | 31m43s | setup 20 s; **Build and test 31m17s** |
| Android | 4m45s | setup 26 s; Gradle 4m14s |
| Website | 4m11s | Playwright install 26 s; **layout check 3m21s**; every other step under 10 s |
| Release tools and docs links; Workflow lint; Secret scan | 9 s; 8 s; 11 s | |

**Inside "Build and test"** (gaps between Gradle's `> Task` lines, which are printed as each task finishes):

| | Linux | Windows |
| --- | --- | --- |
| `:ui` JVM tests (567 tests) | **≈ 49 min** | **≈ 26 min** |
| `:library` tests | under 1 min | ≈ 2.5 min |
| all compilation | ≈ 1.2 min | ≈ 1.3 min |
| everything else (other modules' tests, the CLI) | ≈ 1.5 min | ≈ 1 min |
| tasks from the build cache | **2** | **2** |

B-079 measured the same split on 2026-11-22: the UI test task runs one JVM, uses about one of four cores, and is 1.7× slower on Linux uniformly across classes (software rendering per frame, no hot spot).

**CI 45829860032** (`b823a74`): Linux check failed at 47m52s (B-077, `:playback:mpv:test`); Windows 37m02s; Android 6m10s; Website 4m14s.

**Release 45811843105** (`54c21a4`, green, wall **10m03s**): libmpv **14 s (cache hit)**; Linux archive 2m07s (Gradle 1m35s); Android, Windows and provenance 7m49s (build and sign 6m33s, provenance 20 s).

**Release 45829860004** (`b823a74`, wall 21m14s): libmpv **21m10s (cache miss: Docker build 21m05s)**, failing the source-bundle pin (corrected by `9e5769e`); Linux archive 1m57s; the rest skipped.

**Windows libmpv 45853439517** (`9e5769e`, 47 s): failed in the Build step on the zlib download (section 5).

**CI 45853439508** (`9e5769e`, wall 56m11s): Linux check **failed** at 56m08s: "Set up job" took **3m50s** (16 s in the other runs: runner provisioning, outside the workflow's control), packages 43 s, "Build and test" 51m14s, ending in `:ui:allTests` FAILED after ≈ 48.6 min of UI tests: `VisualSystemTest.artworkIsReadForItsColorOnceNotOnEveryVisit`, `UncaughtExceptionsBeforeTest` at `VisualSystemTest.kt:250` (1 of 567; section 6, S3); so the window tests did not run. Windows check 25m06s (green). Android 5m28s, Website 4m34s (Playwright install 44 s), the rest under 15 s, all green.

**Release 45853439521** (`9e5769e`, **green**, wall **27m31s**): libmpv **15m48s, a cache miss** (`libmpv-f225f870…`, the expected new key; Docker build 15m32s), then **both pins OK** (`libmpv-2.dll` `e52f3f45…`, `libmpv-source.tar.xz` `53853ac9…`); Linux archive 2m41s; Android, Windows and provenance 11m37s, which waits for the libmpv job, so a libmpv rebuild puts the rehearsal's critical path at libmpv + that job (≈ 27.5 min, still under CI's).

## 3. Critical path

**CI's Linux check is the critical path of every push**, and within it the UI test task: about 49 of its 57–58 minutes. Everything else in CI finishes within 37 minutes (Windows) or 7 minutes (the rest), and the Release rehearsal within 10 minutes with a cached libmpv, 21 minutes without. So:

- Making anything but the Linux check faster does not shorten CI's wall time.
- Within the Linux check, setup (16 s), packages (48 s), compilation (≈ 1.2 min) and every other module's tests (≈ 1.5 min) together are under 4 minutes. **The only large lever is `:ui`'s tests**, and the window tests (5 min) that run after them in the same job.

## 4. Caches

| Cache | Configuration | Behaviour measured |
| --- | --- | --- |
| Gradle user home, dependencies, build cache (`setup-gradle`) | defaults; `org.gradle.caching=true`, `org.gradle.configuration-cache=true` | **`cache-read-only: true` on `beta-3`** (the log of 45811843168): `setup-gradle` writes caches only from the default branch, so every `beta-3` run restores what `main` saved last (`gradle-home-v2|Linux-X64|check…-b7d5ba2…`) and saves nothing. Dependencies and wrappers are hits (setup takes 9–10 s), but the **build cache holds `main`'s outputs**, so on `beta-3` almost nothing is reused: **2 tasks `FROM-CACHE`**, every test re-run, on every push, documentation-only pushes included. When the cache did hold a build of the same inputs, a check took 2m43s (B-079, `044c8f0`) |
| Release libmpv (`actions/cache`) | path `build/libmpv`; key `hashFiles('tools/libmpv/**', 'desktop/app/packaging/libmpv-windows.properties')` | Hit: 14 s. Miss: ≈ 21 min. The key covers files that are also inside the pinned source bundle (all four in `tools/libmpv/` are), so a key change for them is necessary: the bundle really changes. The properties file is in the key so that a **pin** change forces a rebuild; its comment lines force one too, needlessly. Whatever is restored is re-verified against both pins, so the cache cannot let a wrong build through |
| Website (`setup-node`) | no npm cache | `npm ci` takes under 10 s; not worth changing |
| Android SDK, Playwright | none | `setup-android` 11–14 s; Playwright's Chromium 25–26 s; not worth changing |

**Duplicated work, measured:** (1) CI's Android job and the rehearsal's Android/Windows job both build the Android release (4–6.5 min each), on separate runners at the same time, so neither is on the critical path; (2) the window-test step re-runs `:desktop:app`'s non-window tests (about 30 s; B-079 decided to leave it); (3) compilation happens in each Gradle job (≈ 1–1.5 min each). None of these is large; none is worth risk.

## 5. The zlib download failures (Windows libmpv workflow)

**Facts (from logs and the repository):**

- **Three failures, all of zlib, each with a different wrong digest:** run 43918527247 (`2113fb5`, 2026-11-16) `448bf4d6…`; run 45829859927 (`b823a74`, 2026-11-26) `5ea058da…`; run 45853439517 (`9e5769e`, 2026-11-26) `1775b93e…`. The pin is `9a93b2b7…`. In each, the 18 other archives downloaded and verified; zlib is the last line of `sources.txt`.
- **The pin is the official archive:** `https://zlib.net/fossils/zlib-1.3.1.tar.gz` and the GitHub release asset `https://github.com/madler/zlib/releases/download/v1.3.1/zlib-1.3.1.tar.gz` are byte-identical, 1,512,791 bytes, `9a93b2b7…` (downloaded 2026-11-26 from the development machine; three earlier downloads from zlib.net matched too).
- **zlib.net, seen from here:** `HTTP/1.1 200 OK`, `Server: Apache`, `Content-Length: 1512791`, `application/x-gzip`, no redirect, no CDN header.
- **The download code** (`tools/libmpv/build.sh`, unchanged since it was pinned): `curl -fsSL --retry 3 -o "$file.part" "$url" && mv "$file.part" "$file"`, then `sha256sum`, then exit 1 on any mismatch. So it downloads to a temporary name, verifies before anything is unpacked, and has no fallback: a wrong file always fails the build. The `/cache` it writes to is inside the container (neither workflow mounts it), so **no archive survives between runs: a stale cache is impossible**.
- **The bytes were a successful HTTP response.** `-f` fails on HTTP 4xx/5xx, and a failed `curl` leaves no file (the `mv` does not run, and `sha256sum` on a missing file stops the script under `set -e` before any digest is printed). A printed digest therefore means curl received a complete response with a success status and different bytes.
- **Timing:** the whole download phase took ≈ 12 s in each failure and ≈ 14 s in the successful release build on `b823a74` (45829860004), which fetched zlib correctly at the same moment as failure 45829859927. No timeout or slow transfer distinguishes them.
- **History of this workflow:** 6 runs; 3 green (2026-11-09 twice, 2026-11-22), 3 failed on zlib.

**Hypotheses (not proven):**

- *zlib.net occasionally answers some clients with a different body and a success status* (a rate-limit or error page served as 200, an incomplete mirror sync, or a load balancer in front of more than one origin). The digests differing every time fit a **generated** page better than a second copy of an archive. Not seen from the development machine (4 of 4 correct).
- *Proxy or CDN on the runner's network path*: no evidence either way; the response headers from the runner were not recorded.
- **Ruled out by the evidence above:** a stale or wrong cached archive (none persists); a truncated or failed transfer (curl would have failed and no digest would be printed); an incorrect pin (two independent hosts agree).
- **Not established:** why the release rehearsal's builds have not hit it. Its job downloads only on a cache miss (rarely), so it has simply made far fewer attempts.

**What is missing to prove the cause:** the failing response's status line, final URL, headers, size and first bytes. `build.sh` does not record them.

**Improvements, all keeping the pin as the only acceptance test (none implemented):**

1. **Do it in the workflow, not in `build.sh`.** `build.sh`, `sources.txt`, `Dockerfile` and `README.md` are inside the pinned source bundle: changing any byte of them changes `sourceSha256` and needs a re-pin (as `b823a74` showed). `build.sh` already accepts a mounted `/cache` (`[[ ! -s "$file" ]]` skips the download, and the SHA-256 is still checked). So a workflow step can fetch every archive in `sources.txt` into a directory mounted as `/cache`; `build.sh` re-verifies each one; the bundle and both pins are untouched.
2. **In that step:** download to a temporary file; compare the SHA-256 with `sources.txt`; on a mismatch, **delete the file**, log the HTTP status, final URL, `Content-Type`, `Content-Length`, the size received and the first bytes in hex, and download again from the same URL (for example 3 attempts with a pause); fail if none matches. A retry can only ever produce a file that has the pinned digest.
3. **Optionally cache the verified archives** (`actions/cache`, key = hash of `sources.txt`): later runs then fetch nothing from zlib.net, and `build.sh` still checks every digest. A poisoned cache cannot pass: the digest is checked on use.
4. **A second official source** (the GitHub release asset above) tried after zlib.net, under the same digest. Only in the workflow step: a URL change in `sources.txt` would change the bundle.

## 6. Opportunities

### Low risk

| # | Change | Estimated saving (estimate) | Effort | Regression risk | Validation | Rollback |
| --- | --- | --- | --- | --- | --- | --- |
| L1 | Let `beta-3` write its own Gradle caches (`setup-gradle` `cache-read-only: ${{ github.ref != 'refs/heads/main' && github.ref != 'refs/heads/beta-3' }}`; GitHub scopes caches by branch, so `main` never reads what `beta-3` writes), so its build cache holds `beta-3`'s outputs | **Pushes that do not change what `:ui` is built from** (documentation, CI, tools, website): the Linux check from ≈ 58 min to a few minutes (B-079 measured 2m43s for a run whose inputs were cached). Code changes under `ui` or what it depends on: ≈ none | 1 line in `ci.yml` | Low. Gradle reuses a test result only for identical inputs, and a failed test is never cached. Caveat: `REFLUX_REQUIRE_ENGINE_TESTS` and `FONTCONFIG_FILE` are environment variables, not declared test inputs; within one job they never change, but declaring them (L2) closes the gap | A documentation-only push: the test tasks `FROM-CACHE`, and the test-report counts identical to the last full run; then a code push: everything affected re-runs | Revert the line |
| L2 | Declare the environment the tests depend on as task inputs (`REFLUX_REQUIRE_ENGINE_TESTS`, `FONTCONFIG_FILE`, the libmpv package version) | none directly; makes L1 safe across environment changes | small build-script change | Low | A changed variable forces a re-run | Revert |
| L3 | Window tests in a job of their own, in parallel with "Build and test" (B-079's second option) | **≈ 5 min** off the critical path (the window tests' 5m05s, minus nothing: the new job's own compile is in parallel) | small `ci.yml` change; one more required check | Low: the same script, the same tests, its own timeout | Both jobs green; the window-test count unchanged | Move the step back |
| L4 | The release libmpv cache key from the two pin **values**, not the whole properties file | ≈ 21 min, only when that file's comments change (rare) | 1 line | Low: the restored build is still compared with both pins | Change a comment: cache hit, pins pass | Revert |
| L5 | zlib: a verified pre-fetch step with discard-and-retry and diagnostics (section 5, items 1–3) | removes a recurring failure (3 of 6 runs) | a workflow step | Low: the digest remains the only acceptance test, in the step and again in `build.sh` | Inject a wrong digest for one archive in a branch: the job fails, with diagnostics | Remove the step |

### Structural

| # | Change | Estimated saving (estimate) | Effort | Regression risk | Validation | Rollback |
| --- | --- | --- | --- | --- | --- | --- |
| S1 | Shard `:ui`'s tests across **separate runners** (2–3 jobs per OS, each one JVM, serial, as today) by a test-class filter | Linux ≈ 49 min of UI tests → ≈ 17–25 min per shard (estimate from B-079's uniform per-class cost); CI wall ≈ 58 → ≈ 25–30 min | build-script shard filter plus matrix jobs | **Medium**: no extra CPU contention on one runner (B-079's failures came with two JVMs sharing four vCPUs), but each shard runs classes in a new order, which can expose the order-dependent leak B-079 found (`VisualSystemTest`) | Every shard green repeatedly; the union of shard reports equals today's test list (no class lost or doubled) | Back to one job |
| S2 | One aggregating "all checks passed" job (`needs:` every job, fails if any did) | none; it makes S1 and L3 safe for branch protection | small | Low | Required check passes only when all do | Remove |
| S3 | Fix the leak and the timing-sensitive tests (B-044, B-074, B-077, and the `VisualSystemTest` leak: B-079 saw it only with two JVMs; on `9e5769e` it failed the ordinary single-JVM Linux check, CI 45853439508; it has no tracker entry of its own yet) | enables S1 safely and later H1 | test-reliability work (Beta 3) | Low if done as fixes, not loosening | Repeated green runs under load | n/a |

**Branch protection.** No workflow change here removes a check. Where jobs are split (L3, S1), branch protection should require the aggregating job (S2), which fails whenever any job fails or is cancelled, so a missing or skipped shard cannot count as success.

### High risk, deferred

| # | Change | Why not now |
| --- | --- | --- |
| H1 | More test JVMs in one runner (`maxParallelForks`) | Tried (B-079, `2f59c37`): Linux 28.5 min but **three failures** never seen serially; reverted by the owner. Needs S3 first |
| H2 | `org.gradle.parallel=true` | The other modules take ≈ 3 min in total; running them beside `:ui`'s tests adds CPU contention to contention-sensitive tests (B-044, B-105's kind) for little gain |
| H3 | Run the UI tests on one OS only | Removes platform coverage (Linux text measurement, B-089; Windows focus behaviour) |
| H4 | Skip CI for documentation-only paths | Required checks would never report, or would report nothing; L1 makes such pushes fast with every check still run |
| H5 | Retrying the libmpv download in a way that accepts a file without its digest, or re-pinning zlib to what was received | Forbidden: the pin is the only proof of what is built |
| H6 | Larger runners | One JVM uses one core; more cores do nothing without H1 or S1, and cost money |

## 7. Ranked plan

1. **L1** (and **L2** with it): the largest saving for the many pushes that do not touch the UI's inputs, one line of CI.
2. **L5**: stops the Windows libmpv workflow failing on zlib without touching the pinned bundle.
3. **L3 + S2**: about five minutes off every full run, and the aggregation job the later split needs.
4. **S3**, then **S1**: the only route to a much shorter full run; reliability work first.
5. **L4**: when convenient.
6. H1–H6: not without dedicated experiments and the owner's decision.

## 8. Baseline (for later comparison)

| Measure | Value | Source |
| --- | --- | --- |
| CI wall, green, `beta-3` | 57m45s | 45811843168 |
| Linux check / its "Build and test" / window tests | 57m42s / 51m26s / 5m05s | 45811843168 |
| Windows check / "Build and test" | 31m43s / 31m17s (37m02s on `b823a74`) | 45811843168, 45829860032 |
| `:ui` tests, Linux / Windows | ≈ 49 / ≈ 26 min | task-line gaps, 45811843168 |
| Tasks from the build cache, `beta-3` | 2 | 45811843168 |
| Release rehearsal, libmpv cached / rebuilt | wall 10m03s / 27m31s (the libmpv job 14 s / 15m48s–21m10s) | 45811843105; 45853439521, 45829860004 |
| Windows check, range | 25m06s–37m02s | 45853439508, 45829860032 |
| Windows libmpv workflow, green | 17–21 min | 42606393980, 42643196578, 44999761604 |
| Queue time per job | 1–3 s | all runs above |

## 9. Unknowns

- What zlib.net sent the three failing runners (section 5).
- How much of a code push L1 saves: it depends on which modules a change touches; `:ui` depends on most of them, so most code pushes still run its tests.
- Whether S1's new class orders expose more order-dependence than B-079 saw; only repeated sharded runs can tell.
- Hosted runners' contention varies; B-079 saw a 4-CPU container pass what hosted runners failed.

## 10. Implementation (2026-11-26)

Approved by the owner: L1, L2, L3 with S2, L4, L5; B-106 registered; this document indexed in the README. Deferred by the owner: S1 (sharding the UI tests) until B-079's and B-106's causes are fixed, H1, H2, H4, and anything that removes coverage or weakens release verification. Four local commits on `beta-3`, **not pushed**:

| Commit | Stage | Files |
| --- | --- | --- |
| `156694f` | L1, L2 | `.github/workflows/ci.yml`, `build.gradle.kts` |
| `0043973` | L5 | `tools/ci/fetch_libmpv_sources.py` and its test, `.github/workflows/libmpv.yml`, `release.yml`, `ci.yml` (the test step) |
| `da896c1` | L4 | `tools/ci/libmpv_cache_key.sh` and its test, `release.yml` |
| `f87b1ae` | L3, S2 | `.github/workflows/ci.yml` |

Not changed: `tools/libmpv/` (all four files), `desktop/app/packaging/libmpv-windows.properties`, both pins, any test or assertion, the release workflow's Gradle caching (read-only, so release builds never take a branch's cache), any platform's coverage.

### L1 and L2: Gradle caches on `beta-3`, and what a test result depends on

- **`setup-gradle` (v6.4.1, read from its `action.yml` at the pinned commit):** `cache-read-only` defaults to true except on the default branch. CI's Check, window-test and Android jobs now set it false only for **pushes to `main` and `beta-3`**; pull requests and other branches read only. GitHub scopes caches by branch, so `main` never reads what `beta-3` writes, and a pull request cannot write to either. Its `cache-provider` default is `enhanced`, which the action describes as a commercial caching service; unchanged here, noted for the owner.
- **Test inputs (`build.gradle.kts`, every `Test` task):** declared as inputs, none of them secret: `REFLUX_REQUIRE_ENGINE_TESTS`, `REFLUX_TEST_ENVIRONMENT`, `LANG`, `LC_ALL`, `DISPLAY`, `WAYLAND_DISPLAY`, `XDG_SESSION_TYPE`, `XDG_CURRENT_DESKTOP`, `GDK_SCALE`, `REFLUX_SCALE`, `REFLUX_RENDER_HWDEC`, `REFLUX_NATIVE_VIDEO_WINDOW`, and the **contents** of `FONTCONFIG_FILE`. Any other `REFLUX_` variable (the tests read 38 in all: opt-in walks, live servers, credentials) or a `~/.reflux-live` folder makes a test task **neither cacheable nor up to date**; their values are only looked at, never made inputs, so no credential reaches Gradle's caches.
- **`REFLUX_TEST_ENVIRONMENT`:** a CI step hashes the runner image (`ImageOS`, `ImageVersion`, architecture) and, on Linux, the installed versions of `tools/ci/linux-packages.txt` (libmpv, Mesa, fonts), so a cached result is never reused after the image or a system package changes. The step fails if a listed package is missing (tested), and its value is stable (tested).

Cache correctness, measured (WSL2 Ubuntu 24.04, a VM; `:playback:mpv:test`, which reads `REFLUX_REQUIRE_ENGINE_TESTS`; no live folder, no other `REFLUX_` variable):

| Case | With L2 | Control: `9e5769e`, without L2 |
| --- | --- | --- |
| First run | executed | |
| The same again, outputs kept | UP-TO-DATE | |
| Outputs deleted, the same environment | **FROM-CACHE** | |
| `REFLUX_REQUIRE_ENGINE_TESTS` changed | executed | **FROM-CACHE (a result from the other setting)** |
| The fontconfig file's contents changed, same path | executed | |
| `REFLUX_TEST_ENVIRONMENT` changed | executed | |
| An opt-in variable set (`REFLUX_MATRIX`), then again | executed, executed | **FROM-CACHE (the opt-in test silently not run)** |
| A documentation-only change | **FROM-CACHE** | |

On the development machine, where `~/.reflux-live` exists, every test task now runs (Gradle reports "opt-in, live-server or credential environment present"), as it should. The configuration cache is still reused.

**What this will save on hosted CI is not measured yet.** The evidence supports only this: on `beta-3`, a push whose test inputs are unchanged (documentation, workflows, tools) can take its test results from the cache once a previous `beta-3` push has written them; a push that changes code re-runs every test of every module that depends on it (`:ui` depends on most). The first push after these commits writes the cache and is itself a full run; the effect shows from the second.

### L5: libmpv sources fetched and verified in the workflow

`tools/ci/fetch_libmpv_sources.py` fetches each archive of `tools/libmpv/sources.txt` with curl (the client `build.sh` uses) into a directory, under the name `build.sh` reads, and keeps a file only if its SHA-256 is the listed one; otherwise it deletes it and fetches again from the same URL, at most three attempts (10 s, then 20 s apart), and fails the job if none matches. A file already there (a restored cache) is checked the same way. Each rejection logs the URL and the final URL (query strings removed), redirects, HTTP status, content type and length, the size received, the digest and the first 48 bytes made printable; no request carries a credential. Both libmpv workflows mount the directory **read-only** as the build's `/cache`; `build.sh` then downloads nothing and still checks every digest before unpacking. Verified archives are cached under `libmpv-sources-<hash of sources.txt>`. The Windows libmpv workflow still builds from scratch every time (it proves the pins reproduce) and now also runs when the fetcher changes.

| Validation | Environment | Result |
| --- | --- | --- |
| `tools/ci/test_fetch_libmpv_sources.py`, 10 tests (local HTTP server: HTML sent as 200, wrong then right, 404, truncated transfer, a digest nothing matches, corrupt and valid files already present, a redirect to a signed URL whose query is never logged, the real listing) | WSL2 | 10 pass (ResourceWarnings as errors); run in CI by "Release tools and documentation links" |
| Mutation: a fetcher that accepts any 200 | WSL2 | 4 of the 10 fail |
| The 19 real archives from their real URLs | WSL2, the development machine's network | 19 of 19 verified in 701 s; **ffmpeg.org's first transfer timed out at 600 s** (2.1 of 12 MB): rejected, deleted, described (curl exit 28), and the second attempt verified |
| `build.sh`'s own source loop (extracted unchanged) over that directory, read-only, curl replaced by a stub that fails if called | WSL2 | all 19 accepted, no download attempted |
| The same with one byte appended to zlib | WSL2 | refused by `build.sh` before anything is unpacked |
| The fetcher over the populated directory | WSL2 | 19 "present and verified", nothing downloaded |

**zlib on hosted runners: not yet measured.** The fetcher cannot make zlib.net send the right bytes; it retries, records what was sent, and fails if every attempt is wrong. A green run after this change says nothing on its own about zlib.net's reliability: only the logs of rejected attempts over many runs will.

### L4: the release workflow's libmpv cache key

`tools/ci/libmpv_cache_key.sh` makes `libmpv-v2-<sha256>` from `tools/libmpv/Dockerfile`, `build.sh`, `sources.txt` and the **values** of `dllSha256` and `sourceSha256`, and fails if either pin is missing. Not included: the properties file's comments, and `tools/libmpv/README.md`. The README is inside the source bundle, but every byte of the bundle is pinned by `sourceSha256`: a README change meant to ship comes with a re-pin, which changes the key; one without is drift, which the Windows libmpv workflow (a fresh build whenever `tools/libmpv/` changes) fails on. A restored build is still compared with both pins.

| Change | Key (`tools/ci/test_libmpv_cache_key.py`, 7 tests on copies of the real files) |
| --- | --- |
| README only | the same |
| A properties comment only | the same |
| The properties file with CRLF line endings | the same |
| Dockerfile / build.sh / sources.txt | different, each |
| `sourceSha256` / `dllSha256` | different, each |
| A pin missing | the script fails; no key |

On history: `54c21a4` and `9e5769e` (which differ only in a properties comment) share `libmpv-v2-85eab4bc…`; `b823a74`, whose edits changed the bundle, gets `libmpv-v2-bbf74d03…`. The first push after this change misses (new key format) and rebuilds once.

### L3 and S2: the window tests' own job, and one required check

"Desktop window tests (ubuntu-24.04)" is a job of its own: the same script (`tools/ci/desktop-window-tests.sh`), Gradle task and JVM as the old step, the same environment (engine tests required, no `FONTCONFIG_FILE`, the same fingerprint), its own report upload on failure. Nothing about which tests share a JVM changes: the step always ran its own Gradle invocation. "All checks passed" `needs` every job and runs whatever happened to them (`if: always()`); it passes only if all seven (`check`, `window-tests`, `android`, `website`, `release-tools`, `workflows`, `secrets`) report exactly `success`. Tested with the script extracted from the workflow and seven simulated outcomes: all success passes; window tests failed, cancelled or skipped, the check matrix failed, a job missing, an extra job: each fails.

**Owner action:** neither `main` nor `beta-3` has branch protection or a ruleset (checked through the API). To make CI required, protect the branch and require **"All checks passed"** only; requiring the individual jobs as well is unnecessary, and requiring a job name that no longer exists ("Check (ubuntu-24.04)" still exists; the old window-test *step* was never a check) would block merges.

**Critical-path effect: an estimate until hosted runs measure it.** From 45811843168: the Linux check was 57m42s, of which the window tests were 5m05s; the check alone should take about 52–53 min, the new job about 7–10 min beside it (setup, packages, compiling `:desktop:app`, 5 min of tests).

### Workflow topology after the changes

| Workflow | Jobs (parallel unless noted) |
| --- | --- |
| CI | Check (ubuntu-24.04), Check (windows-2025), **Desktop window tests (ubuntu-24.04)**, Android, Website, Release tools and documentation links (now also `tools/ci`'s tests), Workflow lint, Secret scan; then **All checks passed** (after all of them) |
| Release | Windows libmpv from source (key from `libmpv_cache_key.sh`; on a miss, verified sources then the build; always the pin check), Linux archive, Android/Windows/provenance (after libmpv), the two draft jobs (tag runs only) |
| Windows libmpv | verified sources (cached by `sources.txt`), a fresh build, the pin check; on changes to `tools/libmpv/**`, the properties, the fetcher, or the workflow |

### Other validation

- `actionlint` 1.7.7 (the version CI uses; its release binary checked against its published SHA-256) with `shellcheck` 0.9.0 (Ubuntu's package): no problems in every committed state. It caught one issue on the way (SC2016 on `dpkg-query`'s `${Package}` format, intended; suppressed for that line with the reason).
- `tools/ci` tests: 17 pass. Documentation links: pass.
- **Full local Gradle check** with `build.gradle.kts`'s change (`./gradlew check --continue`, Windows 11, physical; `~/.reflux-live` present, so every test task executed, none from a cache): 21m05s, **1,695 tests, 37 skipped: the same count and skips as before the change**, all 14 test tasks run, 1 failure: `JellyfinExecutionLiveTest.everyDeliveryIsPlannedNegotiatedPlayedAndConfirmed`, an `HttpTimeoutException` from the live test server (a test that runs only where `~/.reflux-live` names one; it passed on 2026-11-26 with the same code). Re-run alone minutes later: **4 of 4 passed**. Recorded as a transient live-server timeout, both runs kept here; not a defect of this change, which can only make such a test run rather than come from a cache.
- **No test removed or weakened:** the only test files added are `tools/ci`'s two; no test file under a module changed (`git diff 9e5769e --stat -- '*/src/*Test*'` is empty).

### Hosted measurements (2026-11-27)

Old commit hashes; [the commit map](../history/verification/COMMIT-MAP-2026-11-27.md) has the new ones.

| Run | Linux check | Window tests | Windows check | CI wall | What it shows |
| --- | --- | --- | --- | --- | --- |
| 45811843168 (`54c21a4`, before) | 57m42s (window tests inside) | in the check | 31m43s | 57m45s | the baseline |
| 45881492615 (`fdb68c7`, first run with L1–L5) | 51m56s | 8m00s, its own job | failed in 2m25s (the L2 defect, fixed in `0bd84c9`) | 52m04s | the first cache-writing run: 0 tasks from cache, as expected |
| 45891073808 (`0bd84c9`) | **1m53s** | 8m18s | 27m00s | **27m08s** | every Linux unit-test task `FROM-CACHE` with the same environment fingerprint: L1 and L2 working; Windows is now the critical path |
| 45897629306 (`10177a3`) | 50m21s | 6m34s (attempt 2) | 55 s (from cache) | 50m54s | a binutils security update changed the fingerprint, so the Linux tests ran in full: L2 refusing a result from another environment, as designed |

So a push with unchanged inputs and an unchanged runner environment now finishes CI in about 27 minutes (Windows' check), down from 58; any change to the code the UI tests depend on, or to the runner's packages, still costs the full Linux run. One sample each; the release rehearsal's libmpv job took 15 s from cache with the new key. The window-test job's extra cost is its own setup and compile (about 1–3 minutes beside the check).

### Remaining risks

- **Cache storage:** `beta-3` writing Gradle caches uses the repository's GitHub cache quota (10 GB, least recently used evicted first), beside `main`'s.
- **A restored result is only as right as its declared inputs.** L2 declares every environment variable the tests read and the system packages CI installs; a dependency outside those (for example a file a test reads from outside the project, other than `~/.reflux-live`) would not be tracked. None is known.
- **Test results from the cache** come with the reports of the run that made them; a reviewer sees the same counts, not a new run.
- **The fetcher's worst case** is three 600-second attempts for one archive (30 min), well inside the libmpv jobs' 120- and 150-minute limits.
- **B-106** (`VisualSystemTest`): still open; the Linux check can still fail on it until it is fixed.

### Deferred, unchanged

S1 (UI tests across runners) waits for B-079's and B-106's causes to be fixed and for repeated green runs; H1 to H6 as in section 6.
