---
title: 'Story 1.5: A startup self-test turns bots off loudly when NMS breaks'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'dispatch'
baseline_commit: '26a21ca6b7eb1d9695837f7738e0b5ac3672ac95'
baseline_commit_nekotinecore: 'e0dea30688aaf847f7bf6839acabda33d4bb5b2e'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Paper update that renames or removes an NMS member `bot.internal` uses only surfaces as a `LinkageError` mid-game, at the first spawn or tick, instead of a clear message at boot.

**Approach:** When the roster is constructed (the host resolves it eagerly in `onEnable`), a self-test reads the bytecode of every class in `fr.nekotine.core.bot.internal` and resolves every external class, method and field it references, then initializes those classes. On any failure the roster logs a loud error naming each broken accessor, never ticks, and every API call throws `BotsUnavailableException`. Hosts and the server keep running.

## Boundaries & Constraints

**Always:**
- The self-test runs in the `BotRoster` constructor, before it registers its tick listener, and never throws: every failure (unreadable class, missing class/member, failed initialization, no internal class found) is a reported failure.
- "Accessor" = a class, method or field reference in the constant pool of an internal class whose owner is not under `java/`, `javax/`, `jdk/`, `sun/` or `fr/nekotine/`. Resolution follows the JVM's: the owner, its superclasses, then its interfaces, by name and descriptor; constructors via `<init>`. Array owners are checked by their element class.
- **Decision (user, 2026-11-10, review pass 1): resolution also checks kind and access.** For every field/method instruction in an internal class (and method handles in the constant pool), the resolved member must match the instruction's kind (static vs instance; `invokeinterface`/interface ref only on an interface, class ref only on a class) and be accessible from the calling class (public; protected only from a subclass; never private or package-private across packages). A mismatch is reported as a broken accessor with the reason.
- It reads class bytes as resources without loading the internal classes, then initializes each (`Class.forName(name, true, loader)`) to force verification. Internal classes keep side-effect-free static initializers.
- Pass: one info line `Bots self-test passed: <n> accessors in <m> classes`. Fail: one error naming every broken accessor (`owner#name(descriptor)` or the class name) and saying bots are disabled.
- Unavailable roster: `spawn`, `remove`, `list` throw `BotsUnavailableException` (public, `fr.nekotine.core.bot`, extends `IllegalStateException`, message starts `Bots unavailable`). New `isAvailable()`. `unload()` works. No `BotBody` use before the self-test passes.
- Test seam (internal, documented as such): `BotSelfTest.injectFault(String accessor)` makes the next run report that accessor as broken.
- `BotsTestHost.onEnable` resolves `BotRoster` after `super.onEnable()`; `BotRoster` Javadoc tells hosts to do the same.
- Branches: NekotineCore `feat/bots-1-5-self-test` from `origin/bot`, PR to `bot`; Vi6Clean from local `bot`, PR to `bot`, with the gitlink.

**Never:**
- No hand-kept accessor list; no spawning a probe bot or building a probe connection at enable.
- No change to Core's module scan, no ProtocolLib injection check, no Vi6 roster resolve (Story 2.1).
- No controls (1.6/1.7), no Paper or dev-bundle upgrade, no lockfile changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Healthy boot | Host enables | Pass line logged; `isAvailable()` true; bots spawn | N/A |
| Injected fault | `injectFault("X")`, `new BotRoster()` | Error names `X`; `isAvailable()` false; spawn/remove/list throw `BotsUnavailableException`; host stays enabled; host's own roster still works | Seam consumed by that run only |
| Real missing member | Synthesized class bytes calling `MinecraftServer#noSuchMethod()V` | Checker reports that accessor | N/A |
| Kind or access changed | Synthesized calls: `invokestatic` on an instance method, `invokevirtual` on a static one, a private or package-private member | Each reported with its reason | N/A |
| No classes found | Code source unreadable or package empty | Reported as a failure, roster unavailable | Logged in the same error |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- constructor runs the self-test first; `available` flag; guard at the top of `spawn`/`remove`/`list`; skip `EventUtil.register` when unavailable (and `unregister` in `unload` then is harmless).
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotSelfTest.java` (new) -- list `fr/nekotine/core/bot/internal/*.class` from `BotSelfTest.class.getProtectionDomain().getCodeSource()` (jar via `FileSystems.newFileSystem`, or a directory); parse with `java.lang.classfile.ClassFile.of().parse(bytes)`, iterate `constantPool()` for `ClassEntry` and `MemberRefEntry` (field, method, interface method); resolve with the roster's class loader. Expose a pure `check(byte[] classBytes, ClassLoader)` returning broken accessor names, for the test.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java`, `BotConnection.java` -- scanned, not changed. `BotConnection#send` overrides `Connection#send`: its `super.send` Methodref covers the override.
- `NekotineCore/bots-test-host/src/main/java/fr/nekotine/botstesthost/BotsTestHost.java` -- add the eager resolve.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotLifecycleIT.java` -- reuse `roster()`, `spawnLocation()`, `cleanUp`, `LogCapture`; `publicApiExposesNoNms` already scans the new public type.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotsUnavailableException.java` -- public unchecked exception.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotSelfTest.java` -- scanner, resolver, initializer, seam.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- self-test in constructor, unavailable state, `isAvailable()`, Javadoc on eager resolve.
- [x] `NekotineCore/bots-test-host/src/main/java/fr/nekotine/botstesthost/BotsTestHost.java` -- eager resolve.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotSelfTestIT.java` (new) -- every matrix row; broken rosters are `unload()`ed in `finally`; the synthesized class is built with `ClassFile.of().build(...)`.
- [x] `NekotineCore/README.md` -- one line on the self-test and `BotsUnavailableException`.
- [x] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given a fresh healthy roster, when it is constructed, then the pass line reports at least 2 classes and a non-zero accessor count.
- Given NekotineCore and Vi6Clean on the new pointer, when `gradlew build` and `gradlew integrationTest` run in both (Vi6Clean also bare `gradlew -PVi6CleanJarOutputPath=build/dist`), then all pass and `BotLifecycleIT`, `BotTickIT` stay green.

## Implementation Notes

- Commits: NekotineCore `7e8e929`, `da66623` (review pass 1: kind and access checks, missing class reported once), `870602f` (review pass 2: class access, superinterface static/private methods skipped, more mismatch tests, seam cleared in `finally`) on `feat/bots-1-5-self-test` (from `origin/bot`); Vi6Clean `530bc55` and `d9dc80e` (gitlinks) on `feat/bots-1-5-self-test` (from local `bot`).
- Accessor names: `owner#name(desc)` for methods, `owner#name:desc` for fields, the dotted class name for classes; a reason in parentheses unless simply missing. Members of a missing class are not reported again.
- The pass line on the test server: `Bots self-test passed: 82 accessors in 5 classes` (`BotBody`, `BotConnection`, `BotSelfTest` and its nested `Result`, `Caller`).
- Kind and access probes in `BotSelfTestIT` find a package-private static method, a protected instance field and a non-public nested class in NMS by reflection; a Paper update that widens all of them fails the test with "no ... found", needing another probe class.
- Not modelled: the JVM's protected-receiver rule; `new` on a class that became abstract (review E2-c/E2-d, rejected).
- The PRs were opened by the implementation subagent before review (outside the workflow): NekotineMC/NekotineCore#8, NekotineMC/Vi6Clean#82; review fix commits are pushed later.
- Verified: NekotineCore `gradlew build` + `integrationTest` (8 `BotSelfTestIT`, 10 `BotLifecycleIT`, 10 `BotTickIT`, 3 smoke); Vi6Clean bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `build`, `integrationTest` green. No lockfile changes.

## Spec Change Log

- Review pass 1, finding B1/E2/E7 (intent_gap): the user decided resolution also checks instruction kind and access; frozen Always and matrix amended. Avoids: a Paper change to visibility, static-ness or class/interface kind passing the self-test and throwing at first spawn. KEEP: the whole existing implementation (bytecode scan, Result, seam, roster unavailable state, tests); extend `check`, do not rewrite. Code not reverted: the change builds on the pushed commits.

## Review Triage Log

Review pass 1 (2026-11-10). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| B1 / E2 / E7 / B10b | Resolution ignores access (private/package-private), static vs instance, and class vs interface ref kind: such a Paper change passes the self-test and throws `IllegalAccessError`/`IncompatibleClassChangeError` at first spawn or tick | medium | `resolveMethod`/`resolveField` match only name + descriptor over `getDeclared*`; the frozen rule says "by name and descriptor" | intent_gap |
| G1 | No test of a wrong descriptor on an existing name, a wrong field type, or a missing constructor; dropping descriptor comparison keeps every test green | low | Gap finding as filed | patch |
| B7 | A missing class is reported once as a class and again per member ref on it, flooding the error line | low | `resolveField`/`resolveMethod` return `missing class` for each member; the test filters them out | patch |
| B8 / G2 | The initialization (`Class.forName(…, true, …)`) step is untested | low | Gap finding as filed, disposition defer: needs a synthesized class and a custom loader | defer |
| B2 | `getDeclared*` throws `NoClassDefFoundError` for an unrelated member, giving a false failure | low | The server jar is self-consistent: every type in a server class's signatures loads on that server; unlikely, and a false failure is loud, not silent | reject |
| E1 | An NMS type only in a descriptor is not checked as a class | low | Such types are also reached through the member's `getDeclared*` descriptor match, or are referenced as class entries elsewhere; a type only in an own private signature is unlikely | reject |
| B3 / E3 | `Files.list` is not recursive: subpackages skipped | low | AD-3 confines NMS to one internal package | reject |
| B4 | `injectFault` is public in the shipped jar | low | `bot.internal` is not API (AD-3), same as `failNextTick` (1.4 B10) | reject |
| B5 | Eager resolve is documented, not enforced | low | Spec chose the Javadoc; Vi6's eager resolve is Story 2.1 | reject |
| B6 | Exception extends ISE, checked before the main-thread check; prefix built in two places | low | Extends ISE is a spec decision; the order only hides a thread bug on an already disabled roster; prefix duplication is cosmetic | reject |
| E5 | `new BotsUnavailableException(null)` throws NPE | low | Only the roster constructs it, with a literal | reject |
| B9 | `noInternalClassIsAFailure` asserts too little | low | Both branches already fail the run; message wording regressions are cosmetic | reject |
| B10a | Javadoc says "JVM field resolution" but searches superclasses before interfaces | false | Search order only matters for ambiguity; existence, the only result, is the same either way | reject |
| B11 | Boot cost not logged | low | No harm shown; 4 small classes | reject |
| E4 | Side-effecting static initializers would run in the constructor | false | No internal class has one; the rule is documented | reject |
| E6 | Public `onTick` on an unavailable roster can still tick | false | The listener is never registered and the roster holds no bot, so a direct call does nothing | reject |

Review pass 2 (2026-11-10), after the kind/access fix `da66623`. Same layers.

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| G2-1 / B2-7 | Interface/class owner mismatch, protected access and method-handle kinds untested: deleting each branch keeps tests green | low | Gap finding as filed | patch |
| E2-b / B2-2 | A referenced NMS class that stops being public passes; linking throws `IllegalAccessError` | medium | `ClassEntry` only checks `load(...) != null`; the pass 1 decision requires access from the caller | patch |
| E2-a | A class-owner methodref matches a static (or private) method declared on a superinterface, which JVM resolution never selects | low | `hierarchy()` includes interfaces and `resolveMethod` takes any match; guard is one condition | patch |
| E2-h / B2-6b | Injected fault leaks to a later roster if the intended run never happens | low | The seam is global until consumed; clearing it in the test's `finally` is a direct fix | patch |
| G2-3 | Eager resolve in `BotsTestHost.onEnable` is not observed by a test | low | Gap finding as filed, disposition defer | defer |
| B2-1 | An internal override that does not call `super` is not checked to still override | medium | Unverified for future code: today `BotConnection#send` is covered by its `super.send` ref; 1.6/1.7 may add overrides | defer |
| G2-2 / B2-7b | Initialization step untested | low | carried: B8 / G2, deferred | defer (carried) |
| E2-f | Subpackages and multi-release entries skipped | low | carried: B3 / E3 (AD-3 one package); the jar is not multi-release for Core | reject |
| E2-g | Null message NPE | low | carried: E5 | reject |
| E2-i / B2-10 | Unavailability checked before the main-thread check; ISE overlap | low | carried: B6 | reject |
| B2-3 | Descriptor-only types unchecked | low | carried: E1 | reject |
| B2-6a | Public seam in the shipped jar | low | carried: B4 | reject |
| B2-9 | Boot cost unmeasured | low | carried: B11 | reject |
| E2-e | Field lookup order differs from JVMS | false | carried: B10a | reject |
| E2-c | `new` on a class that became abstract is not caught | low | Needs `NEW` opcode tracking; an NMS concrete class `BotBody` instantiates (`ServerPlayer`, packets) turning abstract is unlikely | reject |
| E2-d | Protected instance access on a foreign receiver is not modelled | low | Internal code reaches protected `Connection` members only on `this`; unlikely | reject |
| B2-4 | Hardcoded package breaks under relocation | false | Core is never relocated: `verifyShadowJarLayout` forbids relocated Core copies | reject |
| B2-5 | Hosts cannot read the failure details | low | AD-3 makes the log the channel; adds public surface | reject |
| B2-8 | Healthy test only asserts `classes >= 2` | low | Would need class names in `Result` (more surface); the jar layout check and the other ITs cover missing classes | reject |
| E2-j | Spec says `ClassFile.of().build`, test drops stack maps | false | The synthesized classes are parsed, never loaded; same API | reject |
| E2-k | `unload()` does not throw on an unavailable roster | false | The spec says `unload()` works | reject |

## Design Notes

Why bytecode and not a list: every NMS call in `bot.internal` is a compile-time link, and a breaking Paper update raises `NoSuchMethodError`/`NoSuchFieldError`/`NoClassDefFoundError` at first use. Resolving the same constant-pool references at boot catches exactly those, and covers accessors that 1.6/1.7 add with no list to keep in step. Initializing the classes adds verifier checks (type hierarchy changes) that resolution alone misses. Reflective accessors (none today) would need their own check added to `BotSelfTest`.

Known limit: Core's `setupModules()` loads every `fr.nekotine.core` class at `onLoad` (`ReflexionUtil.streamClassesFromPackage`). If NMS removed a superclass or interface of an internal class (e.g. `Connection`), that load fails and Core's modules are not registered, before the self-test can run. Member changes, the common case, are unaffected. Fixing it means changing Core's scan; out of scope.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL; `BotSelfTestIT`, `BotTickIT`, `BotLifecycleIT`, `BotsTestHostSmokeIT` pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- No lockfile changes. Server log of an `integrationTest` run shows the pass line once at BotsTestHost enable.
