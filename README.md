# BudsSwitcher (Android TV)

Ultra-lightweight Android TV utility that forces a one-click **Bluetooth takeover** for **Samsung Galaxy Buds** directly from your launcher or a mapped remote button.

> **Compatibility:** Designed to work universally with any paired Samsung Galaxy Buds model (Buds, Buds+, Buds Live, Buds Pro, Buds2, Buds2 Pro, Buds FE, Buds3, Buds3 Pro) by automatically detecting Samsung's SPP UUIDs and device names. Currently tested on **Galaxy Buds3 Pro**.

## Why?
Samsung Galaxy Buds do not support standard Bluetooth Multipoint (they rely on proprietary *Samsung Seamless Connection* between Samsung devices). When connected to a phone or laptop, attempting to connect them from the standard Android TV Bluetooth menu often fails or requires navigating deep into system settings.

When you launch **BudsSwitcher**:
1. It runs as a zero-UI translucent activity (`Theme.Translucent.NoTitleBar`, `noHistory`, `excludeFromRecents`) that exits immediately and leaves zero background RAM footprint.
2. It automatically finds your paired Galaxy Buds via Samsung's Bluetooth SPP service UUIDs (even if renamed) or by matching `"buds"` in the device name.
3. It opens an **RFCOMM SPP** connection to Samsung's proprietary control UUIDs (`2e73a4ad-332d-41fc-90e2-16bef06523f2`, `f8620674-a1ed-41ab-a8b9-de9ad655729d`, `00001102-0000-1000-8000-00805f9b34fd`, the same protocol used by [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient)) while simultaneously invoking `BluetoothA2dp.connect()`, `setActiveDevice()`, and `BluetoothHeadset.connect()`.
4. This forces the earbuds to immediately drop their active connection on the other device and route audio to the TV, displaying a confirmation `Toast`.

## Build & Install

```bash
chmod +x build.sh
./build.sh
adb install -r -g BudsSwitcher.apk
```
