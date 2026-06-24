# ocpp-device-emulator

OCPP device (charge point) emulator.

Currently implements **OCPP 1.6**. The codebase is structured so additional
protocol versions plug in as parallel modules: shared infrastructure (modes,
events, transport, device contract) lives in the common packages, and the 1.6
implementation lives under `communication/ocpp16` and `device/ocpp16`. The
device version is selected at runtime via `DeviceFactory` from the
`[ocppVersion]` argument.

## Run

```
java -jar OcppDeviceEmulator.jar [deviceId] [url] [ocppVersion]
```

- `[deviceId]` — the charge point id (or `watcher` / `tester` for those modes)
- `[url]` — the central system websocket endpoint
- `[ocppVersion]` — e.g. `ocpp1.6`

## Build & test

Built with Ant/NetBeans (JDK 1.8). Depends on the sibling Toel `common`
project's `toel.jar`.

```
ant clean jar      # build dist/OcppDeviceEmulator.jar
ant test           # run the unit + scenario tests
```

### Tests

Scenario tests drive a real device against **`TestCentralSystem`** — a small,
scriptable websocket backend (under `test/.../support/`) that records the OCPP
frames the device sends and replies per-action with a CALLRESULT, CALLERROR,
malformed text, no reply, or a socket drop. This makes both happy-path and
fault behaviour assertable without a real central system. `TestCentralSystem`
can also be reused by hand as a lightweight backend.
