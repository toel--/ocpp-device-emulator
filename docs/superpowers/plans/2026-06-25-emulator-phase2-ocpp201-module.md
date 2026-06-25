# Emulator Phase 2: OCPP 2.0.1 Module (Plan B) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OCPP 2.0.1 support as a parallel `ocpp201` module (from the untouched GridIt fork), selected via `DeviceFactory("ocpp2.0.1")`, with its own scenario test suite — reusing the shared core built in Phase 1.

**Architecture:** Mirror the `ocpp16` module. Copy GridIt's 2.0.1 protocol + device-model classes into `communication/ocpp201` and `device/ocpp201`, repackage `se.toel.gridit` → `se.toel.ocpp`, and make them implement the shared `OcppIF`/`DeviceIF`/`ConnectorIF`. Reuse the shared `WebSocket`, `CallbackIF`, `OcppIF`, `DeviceData`, `FirmwareUpdate`. The GridIt project is the source only — it is never modified.

**Tech Stack:** Java 8, ant/NetBeans, Java-WebSocket 1.5.x, org.json, slf4j, toel.jar, JUnit 4.

## Global Constraints

- Java 8; no `var`; explicit types. Member order: constants, fields, ctor, public, private.
- Import classes (short names); no fully-qualified inline refs. Catch narrowly.
- Test helpers under `test/`. Package root `se.toel.ocpp.deviceEmulator`.
- **The GridIt project (`/Users/toel/Customers/Qamcom/GridIt/backend/clients/deviceEmulator`) is READ-ONLY. Never modify or delete it.**
- Reuse shared classes — do NOT duplicate `WebSocket`, `CallbackIF`, `OcppIF`, `DeviceData`, `FirmwareUpdate`.
- Build: `ant clean jar test`. The Phase-1 suite (19 tests) MUST stay green after every task.
- Source of 2.0.1 code: `…/gridit/deviceEmulator/{communication,device}` (package `se.toel.gridit.deviceEmulator`).

## Source → target mapping (reference)

| GridIt source (se.toel.gridit) | Target (se.toel.ocpp) | Notes |
|---|---|---|
| communication/OCPP201_1.java | communication/ocpp201/Ocpp201.java | rename class; `implements OcppIF` (shared) |
| communication/ProtocolCommon.java | communication/ocpp201/Ocpp201Common.java | rename; base class for Ocpp201 |
| communication/ProtocolIF.java | — (dropped) | identical to shared OcppIF |
| communication/CallbackIF.java | — (use shared) | byte-identical |
| communication/WebSocket.java | — (use shared) | byte-identical |
| device/Device.java | device/ocpp201/Device.java | `implements DeviceIF`; add `setAutoMeterValues` |
| device/impl/Connector.java | device/ocpp201/Connector.java | `implements ConnectorIF` |
| device/impl/Variables.java | device/ocpp201/Variables.java | 2.0.1-specific |
| device/impl/Configuration.java | device/ocpp201/Configuration.java | 2.0.1-specific |
| device/impl/DeviceData.java | — (use shared) | 4-line diff — reconcile, see Task 2 |
| device/impl/FirmwareUpdate.java | — (use shared) | 2-line diff — reconcile, see Task 2 |

---

### Task 1: Copy + repackage the 2.0.1 protocol classes into `communication/ocpp201`

**Files:**
- Create: `src/se/toel/ocpp/deviceEmulator/communication/ocpp201/Ocpp201.java` (from GridIt `OCPP201_1.java`)
- Create: `src/se/toel/ocpp/deviceEmulator/communication/ocpp201/Ocpp201Common.java` (from GridIt `ProtocolCommon.java`)
- Test: `test/se/toel/ocpp/deviceEmulator/communication/Ocpp201ProtocolTest.java`

**Interfaces:**
- Consumes: shared `OcppIF`, `CallbackIF`, `WebSocket` (in `…communication`).
- Produces: `Ocpp201 extends Ocpp201Common implements OcppIF` with the same ctor shape as `Ocpp16` — `Ocpp201(String deviceId, String url, CallbackIF callback)`.

- [ ] **Step 1: Write the failing test**

```java
package se.toel.ocpp.deviceEmulator.communication;

import org.json.JSONObject;
import org.junit.Test;
import se.toel.ocpp.deviceEmulator.communication.ocpp201.Ocpp201;
import static org.junit.Assert.*;

public class Ocpp201ProtocolTest {
    @Test public void test01_ocpp201ImplementsOcppIF() {
        assertTrue(OcppIF.class.isAssignableFrom(Ocpp201.class));
    }
    @Test public void test02_ocpp201HasSendResponse() throws Exception {
        assertNotNull(Ocpp201.class.getMethod("sendResponse", String.class, JSONObject.class));
    }
}
```

- [ ] **Step 2: Run** `ant test` — Expected: FAIL (Ocpp201 missing).
- [ ] **Step 3: Create the two classes.** Copy GridIt `ProtocolCommon.java` → `communication/ocpp201/Ocpp201Common.java`: set `package …communication.ocpp201;`, rename class `ProtocolCommon` → `Ocpp201Common`, add imports for any shared types it references unqualified (`WebSocket`, `CallbackIF` from `…communication`). Copy GridIt `OCPP201_1.java` → `communication/ocpp201/Ocpp201.java`: set package, rename class `OCPP201_1` → `Ocpp201`, change `extends ProtocolCommon` → `extends Ocpp201Common`, change `implements ProtocolIF` → `implements OcppIF` (add `import …communication.OcppIF;`), add imports for `CallbackIF`/`WebSocket`/`Event`/`EventHandler`/`EventIds` as needed. The response method is already named `sendResponse` (no rename needed). Drop any `import se.toel.gridit.*`.
- [ ] **Step 4: Run** `ant clean jar test` — Expected: build OK, test PASS. Fix missing imports by compile error until green.
- [ ] **Step 5: Commit** `feat: add ocpp201 protocol classes (Ocpp201, Ocpp201Common)`.

---

### Task 2: Reconcile shared `DeviceData`/`FirmwareUpdate` for 2.0.1

**Files:**
- Read: GridIt `device/impl/DeviceData.java`, `FirmwareUpdate.java`
- Possibly modify: `src/se/toel/ocpp/deviceEmulator/device/impl/DeviceData.java`, `FirmwareUpdate.java`

**Interfaces:**
- Produces: shared `DeviceData`/`FirmwareUpdate` that satisfy BOTH the 1.6 and 2.0.1 `Device`.

- [ ] **Step 1:** Diff the GridIt versions against the shared ones (ignoring package):
  `diff <(grep -v '^package' GRIDIT/device/impl/DeviceData.java) src/.../device/impl/DeviceData.java`
- [ ] **Step 2:** Classify the 4-line (DeviceData) and 2-line (FirmwareUpdate) differences:
  - If the GridIt side only ADDS a method/field the 2.0.1 Device needs and it doesn't change existing behavior → add it to the shared class.
  - If it CONFLICTS with 1.6 usage → do NOT change shared; instead create `device/ocpp201/DeviceData.java` / `FirmwareUpdate.java` (version-specific) and adjust the mapping. (Prefer reuse; only fork on a real conflict.)
- [ ] **Step 3:** Apply the chosen change. Run `ant clean jar test` — Expected: the existing 19 tests still PASS (proves no 1.6 regression).
- [ ] **Step 4:** Commit `refactor: reconcile shared DeviceData/FirmwareUpdate for ocpp201` (or `feat: ocpp201-specific DeviceData/FirmwareUpdate` if forked). State which path was taken in the message.

---

### Task 3: Copy + repackage the 2.0.1 device-model classes into `device/ocpp201`

**Files:**
- Create: `device/ocpp201/Connector.java`, `device/ocpp201/Variables.java`, `device/ocpp201/Configuration.java` (from GridIt `device/impl/*`)
- Test: `test/se/toel/ocpp/deviceEmulator/device/Ocpp201ConnectorTest.java`

**Interfaces:**
- Consumes: shared `ConnectorIF`, `DateTimeUtil`, and the Task 2 `DeviceData`.
- Produces: `device.ocpp201.Connector implements ConnectorIF` (has `int getTransactionId()`); `Variables`, `Configuration` repackaged.

- [ ] **Step 1: Write the failing test**

```java
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import se.toel.ocpp.deviceEmulator.device.ocpp201.Connector;
import static org.junit.Assert.*;

public class Ocpp201ConnectorTest {
    @Test public void test01_connectorImplementsConnectorIF() {
        assertTrue(ConnectorIF.class.isAssignableFrom(Connector.class));
    }
}
```

- [ ] **Step 2:** Run `ant test` — Expected: FAIL.
- [ ] **Step 3:** Copy the three GridIt files into `device/ocpp201/`, set `package …device.ocpp201;`, drop `se.toel.gridit` imports, add imports for shared types they reference (`ConnectorIF`, `DateTimeUtil`, `DeviceData`). Make `Connector` `implements ConnectorIF` (it already has `getTransactionId()` — confirm). Fix cross-references between these three (same package, no import needed).
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS (and existing 19 still green).
- [ ] **Step 5:** Commit `feat: add ocpp201 device-model classes (Connector, Variables, Configuration)`.

---

### Task 4: Add the 2.0.1 `Device` implementing `DeviceIF`

**Files:**
- Create: `device/ocpp201/Device.java` (from GridIt `device/Device.java`)
- Test: `test/se/toel/ocpp/deviceEmulator/device/Ocpp201DeviceTest.java`

**Interfaces:**
- Consumes: `DeviceIF`, `ConnectorIF`, `Ocpp201` (Task 1), `device.ocpp201.*` (Task 3), shared `DeviceData`/`FirmwareUpdate`, `Configuration`.
- Produces: `device.ocpp201.Device implements DeviceIF` with ctor `Device(String id, String url, String ocppVersion)`, methods `start/shutdown/doStartTransaction(int,String)/doStopTransaction(int)/doMeterValues(int)/getConnector(int)→ConnectorIF/setAutoMeterValues(boolean)`.

- [ ] **Step 1: Write the failing test**

```java
package se.toel.ocpp.deviceEmulator.device;

import org.junit.Test;
import static org.junit.Assert.*;

public class Ocpp201DeviceTest {
    @Test public void test01_implementsDeviceIF() {
        assertTrue(DeviceIF.class.isAssignableFrom(
            se.toel.ocpp.deviceEmulator.device.ocpp201.Device.class));
    }
}
```

- [ ] **Step 2:** Run `ant test` — Expected: FAIL.
- [ ] **Step 3:** Copy GridIt `device/Device.java` → `device/ocpp201/Device.java`. Set package. Repackage imports: `communication.OCPP201_1` → `communication.ocpp201.Ocpp201` (and its field/ctor use), `communication.ProtocolIF` → shared `communication.OcppIF`, `communication.CallbackIF` (shared), device-model refs → `device.ocpp201.*` (same package) and shared `device.impl.DeviceData`/`FirmwareUpdate`. Add `implements DeviceIF` + `import …device.DeviceIF;` + `import …device.ConnectorIF;`. Change `getConnector` return type to `ConnectorIF` (covariant; concrete `Connector` already implements it). Add `public void setAutoMeterValues(boolean v){ this.autoMeterValues = v; }`. In the version `switch`, the supported case is `"ocpp2.0.1"` → `new Ocpp201(...)`; keep a default throw.
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS. Resolve compile errors (missing imports / renamed protocol field) until green; the 19 existing tests stay green.
- [ ] **Step 5:** Commit `feat: add ocpp201 Device implementing DeviceIF`.

---

### Task 5: Wire `DeviceFactory` to create the 2.0.1 device

**Files:**
- Modify: `src/se/toel/ocpp/deviceEmulator/device/DeviceFactory.java`
- Modify: `test/se/toel/ocpp/deviceEmulator/device/DeviceFactoryTest.java`

**Interfaces:**
- Consumes: `device.ocpp201.Device`.
- Produces: `DeviceFactory.create(id,url,"ocpp2.0.1")` → `device.ocpp201.Device`.

- [ ] **Step 1: Add the failing test** to `DeviceFactoryTest`:

```java
    @Test public void test03_createsOcpp201Device() {
        DeviceIF d = DeviceFactory.create("CP1", "ws://localhost:1/x", "ocpp2.0.1");
        assertTrue(d instanceof se.toel.ocpp.deviceEmulator.device.ocpp201.Device);
    }
```

- [ ] **Step 2:** Run `ant test` — Expected: FAIL (factory throws IllegalArgumentException for ocpp2.0.1).
- [ ] **Step 3:** Add `case "ocpp2.0.1": return new se.toel.ocpp.deviceEmulator.device.ocpp201.Device(deviceId, url, ocppVersion);` to `DeviceFactory.create` (add the import).
- [ ] **Step 4:** Run `ant clean jar test` — Expected: PASS.
- [ ] **Step 5:** Commit `feat: DeviceFactory builds the ocpp201 device for ocpp2.0.1`.

---

### Task 6: Positive scenario — BootNotification accepted (2.0.1, e2e)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp201BootNotificationTest.java`

**Interfaces:** Consumes `TestCentralSystem`, `DeviceFactory`, `Reply` (Phase 1).

- [ ] **Step 1: Write the test** (mirrors the 1.6 boot test; 2.0.1 uses the same action name):

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

public class Ocpp201BootNotificationTest {
    @Test public void test01_sendsBootNotificationAndIsAccepted() throws Exception {
        TestCentralSystem cs = new TestCentralSystem(18190);
        cs.onCall("BootNotification", c -> Reply.result(new JSONObject()
            .put("status", "Accepted").put("currentTime", "2026-06-25T00:00:00Z").put("interval", 300)));
        cs.startAndWait();
        DeviceIF device = DeviceFactory.create("CP201_BOOT", "ws://localhost:18190/CP201_BOOT", "ocpp2.0.1");
        device.start();
        JSONArray boot = cs.awaitReceived("BootNotification", 10000);
        assertEquals(2, boot.getInt(0));
        assertNotNull(boot.getJSONObject(3));
        device.shutdown();
        cs.stop();
    }
}
```

- [ ] **Step 2:** Run `ant test` — Expected: PASS (the 2.0.1 connect→boot flow mirrors 1.6). If the boot payload assertion mismatches the 2.0.1 `doBootNotification` output, relax it to the CALL/msgId/payload-shape (read `doBootNotification` in `device/ocpp201/Device.java`); do not change production code here.
- [ ] **Step 3:** Commit `test: e2e BootNotification-accepted scenario (ocpp2.0.1)`.

---

### Task 7: Positive scenario — transaction happy path (2.0.1, e2e)

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp201TransactionTest.java`

**Interfaces:** Consumes the Phase-1 transaction pattern (`DeviceIF.doStartTransaction/doStopTransaction/doMeterValues/getConnector`).

- [ ] **Step 1: Write the test** — same structure as `Ocpp16TransactionTest` but `"ocpp2.0.1"`, port 18191, device id `CP201_TX`. Backend answers `BootNotification`, `Authorize` (`idTagInfo.status=Accepted`), `StatusNotification` (`{}`), `Heartbeat`, `MeterValues` (`{}`), `StartTransaction` (`{transactionId:77, idTagInfo:{status:Accepted}}`), `StopTransaction` (`{idTagInfo:{status:Accepted}}`). Drive: wait for `BootNotification` + `Authorize`; `assertTrue(device.doStartTransaction(1,"TAG201"))`; await `StartTransaction` (assert `idTag`); `assertEquals(77, device.getConnector(1).getTransactionId())`; `device.doMeterValues(1)`; await `MeterValues`; `assertTrue(device.doStopTransaction(77))`; await `StopTransaction`.
- [ ] **Step 2:** Run `ant test` — Expected: PASS. If a 2.0.1 payload field name differs (read `device/ocpp201/Device.java` `doStartTransaction`/`doStopTransaction`), adjust the test assertions to the actual field names; do not change production code. NOTE: the Phase-1 StopTransaction NPE fix lives in the 1.6 Device — if the 2.0.1 `Device` has the same `doStartTransaction`/`doStopTransaction` idTag pattern, apply the SAME fix here (store idTag on start + null-guard the auth-cache update) and say so in the commit; if a negative behavior emerges, STOP and report.
- [ ] **Step 3:** Commit `test: e2e transaction happy-path scenario (ocpp2.0.1)`.

---

### Task 8: Negative scenarios + Phase 2 wrap

**Files:**
- Test: `test/se/toel/ocpp/deviceEmulator/scenarios/Ocpp201BackendFaultsTest.java`
- Modify: `README.md`, the design spec status line

**Interfaces:** Consumes `Reply.error/malformed/drop` + the Phase-1 `assertShutsDownWithin` pattern.

- [ ] **Step 1:** Write 2.0.1 fault tests mirroring `Ocpp16BackendFaultsTest` (CALLERROR boot → no Authorize; malformed → survives; drop → handled; clean shutdown each), ports 18192–18194, `"ocpp2.0.1"`.
- [ ] **Step 2:** Run `ant test` — Expected: PASS. A hang/crash here is a real 2.0.1 robustness bug → STOP and report before changing production code.
- [ ] **Step 3:** Run `ant clean jar test` — Expected: all tests (1.6 + 2.0.1) green; jar builds. Smoke-run `java -jar dist/OcppDeviceEmulator.jar CP1 ws://localhost:1/x ocpp2.0.1` (starts, fails to connect — fine).
- [ ] **Step 4:** Update `README.md` (now supports 1.6 **and** 2.0.1 via `[ocppVersion]`) and the spec status line (Phase 2 complete).
- [ ] **Step 5:** Commit `docs: record Phase 2 (ocpp2.0.1 module) complete`.

---

## Self-Review

**Spec coverage:** ocpp201 protocol module ✓ T1; shared DeviceData/FirmwareUpdate reuse ✓ T2; ocpp201 device-model ✓ T3; ocpp201 Device on DeviceIF ✓ T4; version selection ✓ T5; positive scenarios ✓ T6–T7; negative scenarios ✓ T8; both versions tested ✓; GridIt untouched ✓ (read-only, copies only).

**Placeholder scan:** Tasks 2/4/7 say "read the GridIt source / adjust to actual field names" — real instructions against existing code, each shipping concrete tests, not TODOs. No "handle edge cases" left.

**Type consistency:** `Ocpp201(String,String,CallbackIF)` ↔ T4 usage; `DeviceIF` surface identical to Phase 1 (incl. `setAutoMeterValues`, `getConnector→ConnectorIF`); `DeviceFactory.create(String,String,String)→DeviceIF` ↔ T5/T6/T7; `Reply`/`TestCentralSystem` reused from Phase 1.

## Notes
- The 2.0.1 emulator uses 1.6-style message action names, so scenario tests parallel Phase 1 closely.
- If Task 2 shows the DeviceData/FirmwareUpdate diffs conflict with 1.6, fork them into `device/ocpp201` rather than risk a 1.6 regression — the 19 Phase-1 tests are the guardrail.
- Ports 18190–18194 (Phase 1 used 18170–18185).
