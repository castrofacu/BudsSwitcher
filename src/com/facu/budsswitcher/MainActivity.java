package com.facu.budsswitcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    // Samsung Galaxy Buds SPP UUIDs (universal across Buds+, Buds Pro, Buds2/Pro, Buds FE, Buds3/Pro)
    private static final UUID UUID_SPP_NEW = UUID.fromString("2e73a4ad-332d-41fc-90e2-16bef06523f2");
    private static final UUID UUID_SMEP_SPP = UUID.fromString("f8620674-a1ed-41ab-a8b9-de9ad655729d");
    private static final UUID UUID_SPP_LEGACY = UUID.fromString("00001102-0000-1000-8000-00805f9b34fd");
    private static final UUID UUID_SPP_STD = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            showToast("Error: Bluetooth is not available");
            finish();
            return;
        }

        if (!adapter.isEnabled()) {
            adapter.enable();
            showToast("Enabling Bluetooth...");
        }

        BluetoothDevice target = findBudsDevice(adapter);
        if (target == null) {
            showToast("No paired Galaxy Buds found");
            finish();
            return;
        }

        final BluetoothDevice buds = target;
        final String name = buds.getName() != null ? buds.getName() : "Galaxy Buds";

        adapter.getProfileProxy(getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, final BluetoothProfile proxy) {
                if (profile != BluetoothProfile.A2DP) return;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            int state = proxy.getConnectionState(buds);
                            if (state == BluetoothProfile.STATE_CONNECTED) {
                                showToast("🎧 " + name + " already connected to TV");
                                invokeSetActiveDevice(proxy, buds);
                            } else {
                                showToast("🎧 Switching " + name + " to TV...");
                                performTakeover(adapter, proxy, buds, name);
                            }
                        } catch (Throwable t) {
                            showToast("Connection error: " + t.getMessage());
                        } finally {
                            adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy);
                        }
                    }
                }).start();
            }

            @Override
            public void onServiceDisconnected(int profile) {}
        }, BluetoothProfile.A2DP);

        finish();
    }

    private BluetoothDevice findBudsDevice(BluetoothAdapter adapter) {
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                return null;
            }

            // 1. Check by Samsung Galaxy Buds service UUIDs (works even if renamed)
            for (BluetoothDevice d : bonded) {
                ParcelUuid[] uuids = d.getUuids();
                if (uuids != null) {
                    for (ParcelUuid pu : uuids) {
                        UUID u = pu.getUuid();
                        if (UUID_SPP_NEW.equals(u) || UUID_SMEP_SPP.equals(u) || UUID_SPP_LEGACY.equals(u)) {
                            return d;
                        }
                    }
                }
            }

            // 2. Check by device name containing "buds"
            for (BluetoothDevice d : bonded) {
                String n = d.getName();
                if (n != null && n.toLowerCase().contains("buds")) {
                    return d;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void performTakeover(final BluetoothAdapter adapter, BluetoothProfile a2dpProxy, final BluetoothDevice buds, String name) {
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        // Step 1: Ensure connection policy/priority is enabled
        invokePriority(a2dpProxy, buds);

        // Step 2: Trigger A2DP + Headset profile connect AND RFCOMM SPP takeover (GalaxyBudsClient style)
        invokeConnect(a2dpProxy, buds);

        adapter.getProfileProxy(getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile headsetProxy) {
                invokePriority(headsetProxy, buds);
                invokeConnect(headsetProxy, buds);
                adapter.closeProfileProxy(BluetoothProfile.HEADSET, headsetProxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {}
        }, BluetoothProfile.HEADSET);

        // Step 3: Open RFCOMM socket to Samsung SPP UUIDs to force Galaxy Buds to drop other device
        UUID[] candidateUuids = new UUID[]{UUID_SPP_NEW, UUID_SMEP_SPP, UUID_SPP_LEGACY, UUID_SPP_STD};
        BluetoothSocket connectedSocket = null;

        for (UUID uuid : candidateUuids) {
            BluetoothSocket socket = null;
            try {
                socket = buds.createRfcommSocketToServiceRecord(uuid);
                socket.connect();
                connectedSocket = socket;
                break;
            } catch (Throwable e1) {
                if (socket != null) {
                    try { socket.close(); } catch (Throwable ignored) {}
                }
                try {
                    socket = buds.createInsecureRfcommSocketToServiceRecord(uuid);
                    socket.connect();
                    connectedSocket = socket;
                    break;
                } catch (Throwable e2) {
                    if (socket != null) {
                        try { socket.close(); } catch (Throwable ignored) {}
                    }
                }
            }
        }

        // Re-trigger A2DP connect & setActiveDevice right after RFCOMM paging woke/grabbed the earbuds
        invokeConnect(a2dpProxy, buds);
        invokeSetActiveDevice(a2dpProxy, buds);

        // Wait up to 5 seconds for A2DP state to reach STATE_CONNECTED
        boolean isConnected = false;
        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (a2dpProxy.getConnectionState(buds) == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true;
                break;
            }
            if (i == 3 || i == 6) {
                invokeConnect(a2dpProxy, buds);
            }
        }

        if (connectedSocket != null) {
            try { connectedSocket.close(); } catch (Throwable ignored) {}
        }

        if (isConnected) {
            invokeSetActiveDevice(a2dpProxy, buds);
            showToast("✅ " + name + " connected!");
        } else {
            showToast("⚠️ Could not connect to " + name + " (are they out of the case?)");
        }
    }

    private void invokePriority(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method m = proxy.getClass().getMethod("setConnectionPolicy", BluetoothDevice.class, int.class);
            m.invoke(proxy, device, 100);
        } catch (Throwable ignored) {
            try {
                Method m = proxy.getClass().getMethod("setPriority", BluetoothDevice.class, int.class);
                m.invoke(proxy, device, 100);
            } catch (Throwable ignored2) {}
        }
    }

    private void invokeConnect(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method m = proxy.getClass().getMethod("connect", BluetoothDevice.class);
            m.setAccessible(true);
            m.invoke(proxy, device);
        } catch (Throwable ignored) {}
    }

    private void invokeSetActiveDevice(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method m = proxy.getClass().getMethod("setActiveDevice", BluetoothDevice.class);
            m.setAccessible(true);
            m.invoke(proxy, device);
        } catch (Throwable ignored) {}
    }

    private void showToast(final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
