package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

@Environment(EnvType.CLIENT)
class DiscordIPCClient {

    static final Logger LOGGER = LoggerFactory.getLogger("NT/DiscordRPC");

    private InputStream  in;
    private OutputStream out;
    private Closeable    transport;
    private boolean      connected = false;

    synchronized boolean connect(String appId) {
        close();
        try {
            open();
            sendFrame(0, "{\"v\":1,\"client_id\":\"" + appId + "\"}");
            readFrame();
            connected = true;
            LOGGER.info("[Discord RPC] Connecté (app {})", appId);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Discord RPC] Connexion impossible : {}", e.getMessage());
            safeClose();
            return false;
        }
    }

    synchronized void setActivity(String details, String state, long startMs) {
        if (!connected) return;
        try {
            long pid   = ProcessHandle.current().pid();
            long start = startMs / 1000L;
            String stateField = state == null || state.isBlank() ? ""
                : "\"state\":\"" + esc(state) + "\",";
            String json = "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":" + pid
                + ",\"activity\":{"
                + "\"details\":\"" + esc(details) + "\","
                + stateField
                + "\"timestamps\":{\"start\":" + start + "},"
                + "\"assets\":{\"large_image\":\"logo\",\"large_text\":\"Nouvelle Terre SMP\"}"
                + "}},\"nonce\":\"" + UUID.randomUUID() + "\"}";
            sendFrame(1, json);
            readFrame();
        } catch (Exception e) {
            LOGGER.warn("[Discord RPC] Erreur setActivity : {}", e.getMessage());
            connected = false;
            safeClose();
        }
    }

    synchronized void close() {
        if (!connected && transport == null) return;
        connected = false;
        safeClose();
    }

    // ── Transport ────────────────────────────────────────────────────────────

    private void open() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            openWindowsPipe();
        } else {
            openUnixSocket();
        }
    }

    private void openWindowsPipe() throws IOException {
        for (int i = 0; i < 10; i++) {
            try {
                final RandomAccessFile raf = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                transport = raf;
                out = new OutputStream() {
                    @Override public void write(byte[] b, int off, int len) throws IOException { raf.write(b, off, len); }
                    @Override public void write(int b) throws IOException { raf.write(b); }
                };
                in = new InputStream() {
                    @Override public int read(byte[] b, int off, int len) throws IOException { return raf.read(b, off, len); }
                    @Override public int read() throws IOException { return raf.read(); }
                };
                return;
            } catch (IOException ignored) {}
        }
        throw new IOException("Aucun pipe Discord trouvé (discord-ipc-0..9)");
    }

    private void openUnixSocket() throws IOException {
        String[] bases = {
            System.getenv("XDG_RUNTIME_DIR"),
            System.getenv("TMPDIR"),
            "/tmp",
            "/run/user/1000"
        };
        for (String base : bases) {
            if (base == null) continue;
            for (int i = 0; i < 10; i++) {
                Path p = Path.of(base, "discord-ipc-" + i);
                if (!p.toFile().exists()) continue;
                try {
                    SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                    ch.connect(UnixDomainSocketAddress.of(p));
                    transport = ch;
                    out = Channels.newOutputStream(ch);
                    in  = Channels.newInputStream(ch);
                    return;
                } catch (IOException ignored) {}
            }
        }
        throw new IOException("Aucun socket Discord trouvé");
    }

    // ── Protocole Discord IPC (opcode:4 LE | length:4 LE | json) ─────────────

    private void sendFrame(int opcode, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode).putInt(data.length).put(data);
        out.write(buf.array());
        out.flush();
    }

    private String readFrame() throws IOException {
        byte[] hdr = in.readNBytes(8);
        if (hdr.length < 8) throw new IOException("Lecture tronquée (header)");
        int len = ByteBuffer.wrap(hdr, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new String(in.readNBytes(len), StandardCharsets.UTF_8);
    }

    private void safeClose() {
        in = null; out = null;
        if (transport != null) {
            try { transport.close(); } catch (Exception ignored) {}
            transport = null;
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
