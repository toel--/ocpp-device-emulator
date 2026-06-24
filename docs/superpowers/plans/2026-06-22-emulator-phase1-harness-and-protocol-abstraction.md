# Emulator Phase 1: Test Harness + Protocol Abstraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put a `TestCentralSystem` backend and a positive/negative TDD test suite around the existing OCPP 1.6 emulator, and refactor it onto a version-agnostic `DeviceIF`/`OcppIF`/`ConnectorIF` + a `DeviceFactory` selected by `[ocppVersion]` — so OCPP 2.0.1 can later drop in as a parallel module (Plan B).

**Architecture:** Shared core (modes, events, transport, utils) drives devices through `DeviceIF`; protocols implement `OcppIF`. The current 1.6 classes move into `communication/ocpp16` + `device/ocpp16` and implement those interfaces. `Main` delegates version selection to `DeviceFactory`. A test-only `TestCentralSystem` WebSocket server makes both positive and negative backend behaviors assertable.

**Tech Stack:** Java 8, ant/NetBeans, Java-WebSocket 1.5.x (already a dep), org.json, slf4j, `se.toel.util` (toel.jar), JUnit 4 (already used in `test/`).

## Global Constraints

- Java 8 source/target; never use `var` — explicit types always.
- Member order: constants, fields, constructor(s), public methods, private methods.
- Import classes (short names in code); no fully-qualified inline references.
- Catch narrowly; `Throwable` only in long-running worker threads / listeners.
- Test-only helpers live under `test/`, never in `src/` (library purity).
- Package root stays `se.toel.ocpp.deviceEmulator`.
- Standardize the protocol response method name on `sendResponse` (drop `sendConf`).
- Build on top of the existing uncommitted WIP; do not pre-commit the WIP.
- Never auto-commit beyond the per-task commits in this plan, and never push, without explicit user OK. (The plan's `git commit` steps are pre-authorized by the user choosing to execute this plan; pushing is not.)
- Build: `ant clean jar`. Run tests: `ant test` (NetBeans `test` target). Both must pass before a task's commit.
- The emulator builds against `../../../../Java/src/Toel/common/dist/toel.jar`; that jar must exist (it does, current).

---

### Task 1: `TestCentralSystem` scriptable test backend

**Files:**
- Create: `test/se/toel/ocpp/deviceEmulator/support/TestCentralSystem.java`
- Test: `test/se/toel/ocpp/deviceEmulator/support/TestCentralSystemSelfTest.java`

**Interfaces:**
- Consumes: `org.java_websocket.server.WebSocketServer`, `org.json.JSONArray`.
- Produces:
  - `TestCentralSystem(int port)` — construct on a chosen localhost port.
  - `void startAndWait()` — start server, block until listening.
  - `void stop()` — stop server.
  - `int getPort()`.
  - `void onCall(String action, java.util.function.Function<JSONArray,Reply> handler)` — script the reply for an inbound OCPP CALL of the given `action` (the 3rd element of a `[2, msgId, action, payload]` frame).
  - `java.util.List<JSONArray> getReceived()` — all inbound frames, in order, as parsed `JSONArray`.
  - `JSONArray awaitReceived(String action, long timeoutMs)` — block until a CALL with `action` arrives; return it or throw `AssertionError` on timeout.
  - Nested `Reply` with static factories: `Reply.result(JSONObject payload)` (sends `[3, msgId, payload]`), `Reply.error(String code, String description)` (sends `[4, msgId, code, description, {}]`), `Reply.malformed(String raw)` (sends raw text), `Reply.none()` (no reply), `Reply.drop()` (close the socket).

- [ ] **Step 1: Write the failing self-test (positive path)**

```java
package se.toel.ocpp.deviceEmulator.support;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

public class TestCentralSystemSelfTest {

    @Test
    public void test01_recordsCallAndRepliesWithResult() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18170);
        cs.onCall("BootNotification", call -> Reply.result(new JSONObject().put("status", "Accepted")));
        cs.startAndWait();

        final StringBuilder got = new StringBuilder();
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:18170/CP1")) {
            public void onOpen(ServerHandshake h) { send("[2,\"m1\",\"BootNotification\",{\"x\":1}]"); }
            public void onMessage(String m) { got.append(m); }
            public void onClose(int c, String r, boolean remote) {}
            public void onError(Exception e) {}
        };
        client.connectBlocking();

        JSONArray received = cs.awaitReceived("BootNotification", 2000);
        assertEquals("m1", received.getString(1));

        long deadline = System.currentTimeMillis() + 2000;
        while (got.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        JSONArray reply = new JSONArray(got.toString());
        assertEquals(3, reply.getInt(0));
        assertEquals("m1", reply.getString(1));
        assertEquals("Accepted", reply.getJSONObject(2).getString("status"));

        client.closeBlocking();
        cs.stop();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `ant test` (or run `TestCentralSystemSelfTest` in NetBeans)
Expected: FAIL — `TestCentralSystem` / `Reply` do not exist (compile error).

- [ ] **Step 3: Implement `TestCentralSystem` + `Reply`**

Create `TestCentralSystem.java`: extend `WebSocketServer`. In `onMessage(WebSocket, String)`, parse the text to `JSONArray`, append to a synchronized `received` list, notify waiters, and if element 0 == 2, look up the handler for `action = arr.getString(2)`; apply it to produce a `Reply`; act on it:
- `result(payload)` → `conn.send(new JSONArray().put(3).put(arr.getString(1)).put(payload).toString())`
- `error(code,desc)` → `conn.send([4,msgId,code,desc,{}])`
- `malformed(raw)` → `conn.send(raw)`
- `none()` → do nothing
- `drop()` → `conn.close()`

`startAndWait()`: call `start()`, then `onStart()` sets a latch; await it (≤2s). `awaitReceived(action,timeout)`: wait on the list's monitor until a frame with that action exists or timeout (throw `AssertionError`). Order members per the Global Constraints. `Reply` is a static nested class holding a kind enum + fields, with the static factory methods listed in Interfaces.

- [ ] **Step 4: Run the test to verify it passes**

Run: `ant test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add test/se/toel/ocpp/deviceEmulator/support/TestCentralSystem.java \
        test/se/toel/ocpp/deviceEmulator/support/TestCentralSystemSelfTest.java
git commit -m "test: add scriptable TestCentralSystem backend for emulator tests"
```

---

### Task 2: Add a negative reply to the self-test (harden the harness)

**Files:**
- Modify: `test/se/toel/ocpp/deviceEmulator/support/TestCentralSystemSelfTest.java`

**Interfaces:**
- Consumes: Task 1 `TestCentralSystem`, `Reply`.
- Produces: nothing new (confidence only).

- [ ] **Step 1: Add failing test for CALLERROR + drop**

```java
    @Test
    public void test02_repliesWithCallError() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18171);
        cs.onCall("Authorize", call -> Reply.error("InternalError", "boom"));
        cs.startAndWait();
        final StringBuilder got = new StringBuilder();
        WebSocketClient client = new WebSocketClient(new URI("ws://localhost:18171/CP1")) {
            public void onOpen(ServerHandshake h) { send("[2,\"a1\",\"Authorize\",{}]"); }
            public void onMessage(String m) { got.append(m); }
            public void onClose(int c, String r, boolean remote) {}
            public void onError(Exception e) {}
        };
        client.connectBlocking();
        long deadline = System.currentTimeMillis() + 2000;
        while (got.length() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(20);
        JSONArray reply = new JSONArray(got.toString());
        assertEquals(4, reply.getInt(0));
        assertEquals("InternalError", reply.getString(2));
        client.closeBlocking();
        cs.stop();
    }
```

- [ ] **Step 2: Run** `ant test` — Expected: PASS (Task 1 already implements `error`). If FAIL, fix `Reply.error` framing in `TestCentralSystem`.
- [ ] **Step 3: Commit**

```bash
git add test/se/toel/ocpp/deviceEmulator/support/TestCentralSystemSelfTest.java
git commit -m "test: cover CALLERROR reply in TestCentralSystem self-test"
```

---

### Task 3: Rename protocol response method `sendConf` → `sendResponse`

**Files:**
- Modify: `src/se/toel/ocpp/deviceEmulator/communication/OcppIF.java`
- Modify: `src/se/toel/ocpp/deviceEmulator/communication/Ocpp16.java`
- Modify: `src/se/toel/ocpp/deviceEmulator/device/Device.java`

**Interfaces:**
- Consumes: existing `OcppIF` (`connect`, `disconnect`, `sendReq`, `sendConf`).
- Produces: `OcppIF.sendResponse(String id, JSONObject payload)` replacing `sendConf`. Same semantics.

- [ ] **Step 1: Characterization test — Boot reply uses the protocol**

Create `test/se/toel/ocpp/deviceEmulator/communication/Ocpp16ResponseTest.java`:

```java
package se.toel.ocpp.deviceEmulator.communication;

import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class Ocpp16ResponseTest {
    @Test
    public void test01_ocpp16ExposesSendResponse() throws Exception {
        Method m = Ocpp16.class.getMethod("sendResponse", String.class, JSONObject.class);
        assertNotNull(m);
        assertEquals(0, OcppIF.class.getDeclaredMethods().length
            - countNamed(OcppIF.class.getDeclaredMethods(), "sendConf")
            - (OcppIF.class.getDeclaredMethods().length)); // no sendConf remains (see helper)
    }
    private int countNamed(Method[] ms, String name) {
        int n = 0; for (Method x : ms) if (x.getName().equals(name)) n++; return n;
    }
}
```

(If the reflective assertion is awkward, replace with: assert `OcppIF.class.getMethod("sendResponse", String.class, JSONObject.class)` exists and `getMethod("sendConf", ...)` throws `NoSuchMethodException`.)

- [ ] **Step 2: Run** `ant test` — Expected: FAIL (`sendResponse` not defined).
- [ ] **Step 3: Rename** in all three files: `sendConf` → `sendResponse` (signature + body + every callsite in `Device.java`). Update the `OcppIF` Javadoc.
- [ ] **Step 4: Run** `ant clean jar test` — Expected: build OK, test PASS.
- [ ] **Step 5: Commit**

```bash
git add src/se/toel/ocpp/deviceEmulator/communication/OcppIF.java \
        src/se/toel/ocpp/deviceEmulator/communication/Ocpp16.java \
        src/se/toel/ocpp/deviceEmulator/device/Device.java \
        test/se/toel/ocpp/deviceEmulator/communication/Ocpp16ResponseTest.java
git commit -m "refactor: standardize OcppIF response method on sendResponse"
```

---

### Task 4: Extract `ConnectorIF` (what DeviceTester reads off a connector)

**Files:**
- Create: `src/se/toel/ocpp/deviceEmulator/device/ConnectorIF.java`
- Modify: `src/se/toel/ocpp/deviceEmulator/device/impl/Connector.java` (implement `ConnectorIF`)
- Test: `test/se/toel/ocpp/deviceEmulator/device/ConnectorIFTest.java`

**Interfaces:**
- Consumes: existing `Connector`.
- Produces: `ConnectorIF` exposing the connector accessors used by `DeviceTester` and tests. Before writing, confirm the exact getters `DeviceTester` calls on the returned connector (grep `getConnector` usage in `DeviceTester.java`); include exactly those (e.g. `int getId()`, `int getTransactionId()`, `String getStatus()`). Add only what is actually consumed (YAGNI).

- [ ] **Step 1:** Write `ConnectorIFTest` asserting `Connector implements ConnectorIF` and the consumed getters return expected defaults for a fresh connector.
- [ ] **Step 2:** Run `ant test` — Expected: FAIL (no `ConnectorIF`).
- [ ] **Step 3:** Create `ConnectorIF` with exactly the consumed getters; make `Connector implements ConnectorIF`. Do not change `Connector` behavior.
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS.
- [ ] **Step 5:** Commit `feat: add ConnectorIF for version-agnostic connector access`.

---

### Task 5: Extract `DeviceIF` and have the 1.6 `Device` implement it

**Files:**
- Create: `src/se/toel/ocpp/deviceEmulator/device/DeviceIF.java`
- Modify: `src/se/toel/ocpp/deviceEmulator/device/Device.java` (implement `DeviceIF`)
- Modify: `src/se/toel/ocpp/deviceEmulator/modes/DeviceEmulator.java`, `DeviceTester.java`, `DeviceWatcher.java` (type fields/locals as `DeviceIF`)
- Test: `test/se/toel/ocpp/deviceEmulator/device/DeviceIFTest.java`

**Interfaces:**
- Consumes: `Device`, `ConnectorIF` (Task 4).
- Produces: `DeviceIF` with exactly the members the modes call:
  - `void start();`
  - `void shutdown();`
  - `boolean doStartTransaction(int connectorId, String idTag);`
  - `boolean doStopTransaction(int transactionId);`
  - `void doMeterValues(int connectorId);`
  - `ConnectorIF getConnector(int connectorId);`

- [ ] **Step 1:** Write `DeviceIFTest`: assert `Device implements DeviceIF` and each method above is present with the stated signature (reflection or a compile-time reference in the test).
- [ ] **Step 2:** Run `ant test` — Expected: FAIL.
- [ ] **Step 3:** Create `DeviceIF` (above). Make `Device implements DeviceIF`; change `Device.getConnector` return type to `ConnectorIF` (its concrete `Connector` already satisfies it). Retype the `device` field/locals in the three modes to `DeviceIF`. (`Device` is still constructed concretely for now — Task 7 introduces the factory.)
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS.
- [ ] **Step 5:** Commit `refactor: introduce DeviceIF; modes depend on the interface`.

---

### Task 6: Move 1.6 classes into `ocpp16` subpackages

**Files:**
- Move: `communication/Ocpp16.java` → `communication/ocpp16/Ocpp16.java`
- Move: `communication/OcppCommon.java` → `communication/ocpp16/OcppCommon.java` (if 1.6-specific) — verify; if generic, leave in `communication/`.
- Move: `device/Device.java` → `device/ocpp16/Device.java`
- Move: `device/impl/Connector.java`, `LocalAuthorizationList.java`, `LocalAuthorization.java`, `AuthorizationCache.java` → `device/ocpp16/...` (keep `DeviceData`, `FirmwareUpdate` in shared `device/impl/` — they are shared)
- Modify: package declarations + imports across the moved files and their referencers (`modes/*`, `Main`).

**Interfaces:**
- Consumes: Tasks 3–5 results.
- Produces: 1.6 protocol/device under `…communication.ocpp16` / `…device.ocpp16`; shared interfaces remain in `…communication` / `…device`.

- [ ] **Step 1:** Confirm green baseline: `ant test` PASS.
- [ ] **Step 2:** Move files and update `package`/`import` lines (no behavior change). Keep `DeviceIF`/`ConnectorIF`/`OcppIF` and shared `impl/DeviceData`,`impl/FirmwareUpdate` where they are.
- [ ] **Step 3:** Run `ant clean jar test` — Expected: build OK, all tests PASS (pure move/rename).
- [ ] **Step 4:** Commit `refactor: relocate 1.6 protocol/device into ocpp16 subpackages`.

---

### Task 7: `DeviceFactory` + `Main` version selection

**Files:**
- Create: `src/se/toel/ocpp/deviceEmulator/device/DeviceFactory.java`
- Modify: `src/se/toel/ocpp/deviceEmulator/modes/DeviceEmulator.java` (use the factory)
- Test: `test/se/toel/ocpp/deviceEmulator/device/DeviceFactoryTest.java`

**Interfaces:**
- Consumes: `DeviceIF`, `device.ocpp16.Device`.
- Produces: `static DeviceIF DeviceFactory.create(String deviceId, String url, String ocppVersion)` — returns `device.ocpp16.Device` for `"ocpp1.6"`; throws `IllegalArgumentException("Unsupported OCPP version: " + ocppVersion)` for anything else (2.0.1 is added in Plan B).

- [ ] **Step 1:** Write `DeviceFactoryTest`:

```java
@Test public void test01_createsOcpp16Device() {
    DeviceIF d = DeviceFactory.create("CP1", "ws://localhost:1/x", "ocpp1.6");
    assertTrue(d instanceof se.toel.ocpp.deviceEmulator.device.ocpp16.Device);
}
@Test(expected = IllegalArgumentException.class)
public void test02_rejectsUnknownVersion() {
    DeviceFactory.create("CP1", "ws://localhost:1/x", "ocpp9.9");
}
```

- [ ] **Step 2:** Run `ant test` — Expected: FAIL.
- [ ] **Step 3:** Implement `DeviceFactory.create` (switch on `ocppVersion`). In `DeviceEmulator`, replace `new Device(...)` with `DeviceFactory.create(deviceId, url, ocppVersion)`.
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS. Manually: `java -jar dist/OcppDeviceEmulator.jar CP1 ws://localhost:1/x ocpp1.6` still starts (will fail to connect — fine).
- [ ] **Step 5:** Commit `feat: add DeviceFactory and route version selection through it`.

---

### Task 8: Positive scenario — BootNotification accepted (1.6, end-to-end)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp16BootNotificationTest.java`

**Interfaces:**
- Consumes: `TestCentralSystem`, `DeviceFactory`, `DeviceIF`.
- Produces: the reusable end-to-end pattern (start CS → build device pointed at it → drive → assert on `cs.getReceived()`).

- [ ] **Step 1: Write the failing test**

```java
package se.toel.ocpp.deviceEmulator.scenarios;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.DeviceFactory;
import se.toel.ocpp.deviceEmulator.device.DeviceIF;
import se.toel.ocpp.deviceEmulator.support.Reply;
import se.toel.ocpp.deviceEmulator.support.TestCentralSystem;
import static org.junit.Assert.*;

public class Ocpp16BootNotificationTest {

    @Test
    public void test01_sendsBootNotificationAndIsAccepted() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18180);
        cs.onCall("BootNotification", call ->
            Reply.result(new JSONObject()
                .put("status", "Accepted")
                .put("currentTime", "2026-06-22T00:00:00Z")
                .put("interval", 300)));
        cs.startAndWait();

        DeviceIF device = DeviceFactory.create("CP_TEST_1", "ws://localhost:18180/CP_TEST_1", "ocpp1.6");
        device.start();

        JSONArray boot = cs.awaitReceived("BootNotification", 4000);
        assertEquals(2, boot.getInt(0));                 // CALL
        assertNotNull(boot.getJSONObject(3));            // has payload
        assertTrue(boot.getJSONObject(3).has("chargePointVendor"));

        device.shutdown();
        cs.stop();
    }
}
```

- [ ] **Step 2:** Run `ant test` — Expected: PASS if the existing 1.6 `Device.start()`/`doBootNotification()` already emit BootNotification on connect (characterization). If it FAILS because `start()` doesn't auto-boot, read `Device.start()`/`doReady()` and adjust the test to drive the same sequence the real emulator uses (do NOT change production behavior in this task). Document the actual sequence in the test comment.
- [ ] **Step 3:** Commit `test: e2e BootNotification-accepted scenario (ocpp1.6)`.

---

### Task 9: Negative scenario — BootNotification rejected (1.6)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp16BootRejectedTest.java`

**Interfaces:** Consumes Task 8 pattern.

- [ ] **Step 1: Write the test**

```java
    @Test
    public void test01_bootRejected_noTransactionFollows() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18181);
        cs.onCall("BootNotification", call ->
            Reply.result(new JSONObject().put("status", "Rejected").put("interval", 10)));
        cs.startAndWait();
        DeviceIF device = DeviceFactory.create("CP_REJ", "ws://localhost:18181/CP_REJ", "ocpp1.6");
        device.start();
        cs.awaitReceived("BootNotification", 4000);
        Thread.sleep(1500);
        // No StartTransaction should be sent while not accepted.
        for (JSONArray f : cs.getReceived()) {
            if (f.getInt(0) == 2) assertNotEquals("StartTransaction", f.getString(2));
        }
        device.shutdown();
        cs.stop();
    }
```

- [ ] **Step 2:** Run `ant test` — Expected: PASS against existing behavior (device must not transact before acceptance). If it FAILS, that is a **real bug** in 1.6 `Device`; STOP and report it (do not silently change behavior — confirm with the user first).
- [ ] **Step 3:** Commit `test: e2e BootNotification-rejected scenario (ocpp1.6)`.

---

### Task 10: Positive scenario — Authorize + StartTransaction + MeterValues + StopTransaction (1.6)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp16TransactionTest.java`

**Interfaces:** Consumes `DeviceIF.doStartTransaction/doStopTransaction/doMeterValues/getConnector`, `ConnectorIF`.

- [ ] **Step 1: Write the test** — Boot→Accepted; `Authorize`→`idTagInfo.status=Accepted`; `StartTransaction`→`{transactionId:42, idTagInfo:{status:Accepted}}`; `MeterValues`→`{}`; `StopTransaction`→`{}`. Drive:

```java
        device.start();
        cs.awaitReceived("BootNotification", 4000);
        DeviceIF d = device;
        assertTrue(d.doStartTransaction(1, "TAG123"));
        JSONArray start = cs.awaitReceived("StartTransaction", 4000);
        assertEquals("TAG123", start.getJSONObject(3).getString("idTag"));
        d.doMeterValues(1);
        cs.awaitReceived("MeterValues", 4000);
        assertTrue(d.doStopTransaction(d.getConnector(1).getTransactionId()));
        cs.awaitReceived("StopTransaction", 4000);
```

(Adjust payload field names to the 1.6 `Device` actuals after reading `doStartTransaction`/`doMeterValues`; keep assertions on fields that exist.)

- [ ] **Step 2:** Run `ant test` — Expected: PASS (characterizes existing 1.6 transaction flow). Fix only test expectations to match real payloads; not production code.
- [ ] **Step 3:** Commit `test: e2e transaction happy-path scenario (ocpp1.6)`.

---

### Task 11: Negative scenarios — CALLERROR, malformed response, mid-session drop (1.6)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp16BackendFaultsTest.java`

**Interfaces:** Consumes `Reply.error/malformed/drop`.

- [ ] **Step 1: Write three tests** in one class:
  - `test01_callErrorOnBoot_deviceStaysUp` — `BootNotification`→`Reply.error("InternalError","x")`; assert `device` does not crash the JVM/thread and `cs.getReceived()` still has the boot CALL; `device.shutdown()` returns cleanly.
  - `test02_malformedResponse_isIgnoredGracefully` — `Heartbeat` (or Boot)→`Reply.malformed("not-json")`; assert no exception escapes and the device can still issue a subsequent CALL.
  - `test03_backendDropsSocket_deviceHandlesClose` — `BootNotification`→`Reply.drop()`; assert the device's `onClose` path runs (no hang) and `device.shutdown()` completes within 3s.
- [ ] **Step 2:** Run `ant test` — Expected: PASS. Any hang/uncaught-exception is a **real robustness bug** — STOP and report to the user before changing production code.
- [ ] **Step 3:** Commit `test: e2e backend-fault negative scenarios (ocpp1.6)`.

---

### Task 12: Phase 1 wrap — full suite green + docs

**Files:**
- Modify: `README.md` (note `TestCentralSystem` + how to run `ant test`)
- Modify: `docs/superpowers/specs/2026-06-22-merge-ocpp201-into-emulator-design.md` (mark Phase 1 done)

- [ ] **Step 1:** Run `ant clean jar test` — Expected: all tests PASS, jar builds.
- [ ] **Step 2:** Manually run `java -jar dist/OcppDeviceEmulator.jar tester` and `... watcher` to confirm modes still start (they should, now via `DeviceIF`).
- [ ] **Step 3:** Update `README.md` + spec status line.
- [ ] **Step 4:** Commit `docs: record Phase 1 (harness + protocol abstraction) complete`.

---

## Self-Review

**Spec coverage:** Harness (`TestCentralSystem`) ✓ T1–2; `OcppIF.sendResponse` ✓ T3; `DeviceIF`/`ConnectorIF` ✓ T4–5; `ocpp16` module layout ✓ T6; `Main`/version selection via factory ✓ T7; positive scenarios ✓ T8,T10; negative scenarios ✓ T9,T11; both-versions — **deferred to Plan B** (Phase 1 is 1.6 only by design); TDD + numbered tests ✓ throughout; build on WIP ✓ (no pre-commit step). GridIt untouched ✓ (no GridIt path referenced).

**Placeholder scan:** Tasks 4, 8, 10 intentionally say "confirm exact getters/payload field names by reading the source" — this is a real instruction (the field names live in the existing code), not a TODO; each still ships a concrete test. No "TBD/handle edge cases/write tests for the above" left.

**Type consistency:** `DeviceIF` members (`start`, `shutdown`, `doStartTransaction(int,String)→boolean`, `doStopTransaction(int)→boolean`, `doMeterValues(int)→void`, `getConnector(int)→ConnectorIF`) match Tasks 5/7/10 usage. `OcppIF.sendResponse(String,JSONObject)` consistent T3 ↔ rename. `DeviceFactory.create(String,String,String)→DeviceIF` consistent T7 ↔ T8/10. `Reply` factories consistent T1 ↔ T8/9/11.

## Notes for the implementer
- Phase 1 scenario tests are largely **characterization tests**: the 1.6 behavior already exists, so they should pass once wired through `TestCentralSystem`. A red scenario test means either the test's payload expectations don't match the real `Device` output (fix the test) or a genuine robustness bug (Tasks 9/11 — STOP and report, don't silently patch).
- Ports 18170–18181 are used to avoid clashes; if a port is busy in CI, bump the constant in that test.
