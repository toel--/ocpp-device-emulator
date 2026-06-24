# Design: merge OCPP 2.0.1 into the device emulator

Date: 2026-06-22
Status: Phase 1 (test harness + protocol abstraction, 1.6) COMPLETE. Phase 2 (integrate 2.0.1 as the
ocpp201 module) pending — see Plan B.

## Goal

Make the generic `ocpp-device-emulator` (package `se.toel.ocpp.deviceEmulator`) run **either OCPP 1.6
or OCPP 2.0.1**, selected at runtime by the existing `[ocppVersion]` argument. Bring the 2.0.1
capability in from the GridIt fork; produce one maintainable codebase with shared infrastructure and
per-version protocol/device modules.

## Sources

- 1.6 emulator (target repo): `/Volumes/Data/Java/src/OCPP/ocpp-device-emulator`,
  package `se.toel.ocpp.deviceEmulator`. Git repo, origin `toel--/ocpp-device-emulator`. Has
  uncommitted WIP (`Device.java` rework, `Configuration` moved `device/impl`→`communication`).
- 2.0.1 emulator (source of 2.0.1 logic): `/Users/toel/Customers/Qamcom/GridIt/backend/clients/deviceEmulator`,
  package `se.toel.gridit.deviceEmulator`.

## Decisions (locked)

- **Merge** into one app (not keep-separate). Keep name `ocpp-device-emulator` (no rename).
- Keep package **`se.toel.ocpp`** (the GridIt `se.toel.gridit` 2.0.1 code is re-packaged into it).
- **GridIt stays fully untouched** as an independent customer fork. We copy/adapt its 2.0.1 logic; we
  do not modify or remove the GridIt project.
- Merge shape = **shared core + per-version modules** (a common interface with `ocpp16`/`ocpp201`
  implementations), not a single version-aware `Device`, not a thin two-Device shell.
- **Build on top of the existing uncommitted WIP** (no pre-commit of WIP). Final review diff will
  include the WIP — accepted.
- Testing = a small **test-only `TestCentralSystem`** backend + positive & negative scenarios,
  built **TDD**.

## Architecture / layout

```
se/toel/ocpp/deviceEmulator/
  Main                         reads [ocppVersion]; builds the right Device + protocol, wires them
  communication/
    OcppIF, WebSocket, CallbackIF, OcppCommon      (shared)
    ocpp16/   Ocpp16
    ocpp201/  Ocpp201          (renamed from OCPP201_1)
  device/
    DeviceIF                   (NEW) version-agnostic device contract the modes drive
    impl/  DeviceData, FirmwareUpdate              (shared; tiny reconcile)
    ocpp16/   Device, Connector, LocalAuthorizationList, LocalAuthorization, AuthorizationCache
    ocpp201/  Device, Connector, Variables
  events/    (shared, already identical)
  modes/     ApplicationModeIF (shared) + DeviceEmulator, DeviceTester, DeviceWatcher (reconciled → drive DeviceIF)
  utils/     FTP (shared) + DateTimeUtil (reconciled)
```

### Divergence basis (measured 2026-06-22, ignoring package line)

- Identical, share as-is: all `events/*`, `communication/WebSocket`, `CallbackIF`,
  `modes/ApplicationModeIF`, `utils/FTP`.
- Trivial reconcile (share): `device/impl/DeviceData` (4 diff lines), `FirmwareUpdate` (2).
- Moderate reconcile (share): `modes/DeviceEmulator` (45), `DeviceTester` (15), `DeviceWatcher` (17),
  `utils/DateTimeUtil` (45). Drift is mostly version literals + `sendConf`/`sendResponse` naming.
- Version-specific (per-version modules): `device/Device` (~1350 lines, diverged 1342 vs 1401),
  `device/impl/Connector` (202 diff lines), protocol classes, and device-model classes
  (1.6 `LocalAuthorizationList`/`AuthorizationCache` vs 2.0.1 `Variables`).

## Key interfaces

- **`OcppIF`** (protocol): the two existing interfaces are byte-identical except one method name.
  Standardize on the 2.0.1 term **`sendResponse`** (replacing 1.6 `sendConf`); update `Ocpp16`
  callsites. `Ocpp16` and `Ocpp201` both implement `OcppIF`.
- **`DeviceIF`** (NEW): the abstraction the three `modes/*` call into, so the modes are shared and
  version-agnostic. `device.ocpp16.Device` and `device.ocpp201.Device` implement it. Its surface is
  defined by what `DeviceEmulator`/`DeviceTester`/`DeviceWatcher` actually call on a device.

## Version selection

`Main` parses `[ocppVersion]` (`ocpp1.6` default, `ocpp2.0.1`). Switch on it to instantiate the
matching `device.<ver>.Device` + `communication.<ver>.<Proto>` pair, wired via `DeviceIF`/`OcppIF`.
Everything downstream (modes, events, transport) is shared. Unknown version → error + usage.

## Testing

`TestCentralSystem` — a test-only `WebSocketServer` (Java-WebSocket, already a dependency) under
`test/`, NOT in `src` (test helpers stay out of the shipped code). Responsibilities:

- Bind an ephemeral `localhost` port; expose it so the test points the emulator at `ws://localhost:<port>`.
- Scriptable per incoming CALL: reply CALLRESULT (positive), CALLERROR, malformed/wrong payload,
  delayed/no response, or drop the socket (negative).
- Record received frames so tests assert the device's actual OCPP behavior.

Scenarios (run for both 1.6 and 2.0.1):
- Positive: BootNotification→Accepted (honors returned interval/heartbeat); Authorize→Accepted;
  Start/Stop (1.6) / TransactionEvent (2.0.1) accepted; StatusNotification; Heartbeat round-trip.
- Negative: BootNotification→Rejected (device backs off, no transaction); CALLERROR handling;
  malformed JSON; auth Rejected; mid-session disconnect/reconnect; unexpected message-type id.

Use the numbered-test ordering pattern (`testNN_...`) where session state must flow between cases.

## TDD flow

For each scenario: write the test against `DeviceIF` + `TestCentralSystem` first (red), then
build/merge the per-version `Device` until green, then refactor. Shared scaffolding is exercised
transitively by these behavioral tests. Run tests before reporting any step done.

## Build / dependencies

Stays an ant/NetBeans project building against `Toel/common/dist/toel.jar`
(`../../../../Java/src/Toel/common/dist/toel.jar`). Before implementation, confirm the GridIt 2.0.1
sources introduce no extra runtime deps beyond what the 1.6 project already has (json, slf4j, toel,
ftp4j). Resolve any gap then.

## Out of scope

- Modifying or removing the GridIt project.
- Committing the existing WIP (we build on top of it).
- OCPP versions other than 1.6 and 2.0.1.
- Any change to other apps (OcppBackendEmulator etc.) — though OcppBackendEmulator may later serve as
  an additional manual backend.

## Risks

- Reconciling two diverged ~1350-line `Device` files: mitigated by keeping them per-version (no forced
  single class) and covering behavior with the `TestCentralSystem` scenarios before/while merging.
- Building on uncommitted WIP makes the review diff larger; accepted by owner.
