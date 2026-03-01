package net.minecraft.client.pose;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Background UDP listener on port 5005.
 * Parses JSON: {
 *   "nodes":   [[x,y], ...],   -- 17 landmark pixel coords
 *   "key":     "A",            -- current semaphore letter, or "None"
 *   "holding": true/false,     -- whether a key is being held
 *   "caps":    true/false      -- caps lock state
 * }
 */
public class PoseReceiver {

    public static final int   PORT  = 5005;
    public static final float CAM_W = 1920f;
    public static final float CAM_H = 1080f;

    // Skeleton nodes — normalised [0..1] flat array: [x0,y0, x1,y1, ...]
    private static final AtomicReference<float[]> latestNodes   = new AtomicReference<>(new float[0]);
    // Current detected semaphore letter, e.g. "A", or "None" / null
    private static final AtomicReference<String>  latestKey     = new AtomicReference<>("None");
    private static final AtomicBoolean            latestHolding = new AtomicBoolean(false);
    private static final AtomicBoolean            latestCaps    = new AtomicBoolean(false);

    private static Thread          listenerThread;
    private static volatile boolean running = false;

    /** Start the background listener. Call once from Minecraft client init. */
    public static void start() {
        if (running) return;
        running = true;
        listenerThread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(PORT)) {
                byte[] buf = new byte[8192];
                System.out.println("[PoseReceiver] Listening on UDP port " + PORT);
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength());
                    parseAndStore(json);
                }
            } catch (Exception e) {
                if (running) System.err.println("[PoseReceiver] Error: " + e.getMessage());
            }
        }, "PoseReceiver-UDP");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public static void stop() {
        running = false;
        if (listenerThread != null) listenerThread.interrupt();
    }

    /** Latest skeleton nodes as normalised [0..1] pairs. */
    public static float[] getNodes() { return latestNodes.get(); }

    /** Current semaphore letter, e.g. "A". Returns "None" if no gesture. */
    public static String getKey() { return latestKey.get(); }

    /** Whether a key is currently being held down. */
    public static boolean isHolding() { return latestHolding.get(); }

    /** Current caps lock state. */
    public static boolean isCaps() { return latestCaps.get(); }

    private static void parseAndStore(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            // ── Nodes ──────────────────────────────────────────────────────
            JsonArray nodes = obj.getAsJsonArray("nodes");
            float[] flat = new float[nodes.size() * 2];
            for (int i = 0; i < nodes.size(); i++) {
                JsonArray pair = nodes.get(i).getAsJsonArray();
                flat[i * 2]     = pair.get(0).getAsFloat() / CAM_W;
                flat[i * 2 + 1] = pair.get(1).getAsFloat() / CAM_H;
            }
            latestNodes.set(flat);

            // ── Extra fields ───────────────────────────────────────────────
            String key = obj.has("key") ? obj.get("key").getAsString() : "None";
            latestKey.set(key);
            latestHolding.set(obj.has("holding") && obj.get("holding").getAsBoolean());
            latestCaps.set(obj.has("caps")    && obj.get("caps").getAsBoolean());

        } catch (Exception e) {
            System.err.println("[PoseReceiver] Failed to parse packet: " + e.getMessage());
        }
    }
}
