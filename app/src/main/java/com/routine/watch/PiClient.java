package com.routine.watch;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Talks to the Pi (plain HTTP on the home Wi-Fi). */
public final class PiClient implements SyncEngine.Net {
    public static final int DEFAULT_PORT = 8765;
    public static final int DISCOVERY_PORT = 8766;

    /** The Pi said no (wrong code, or too many wrong codes). */
    public static final class AuthException extends IOException {
        public AuthException(String m) {
            super(m);
        }
    }

    /** The Pi refused an edit and says why (for example "There is already an item at 1:00 PM."). */
    public static final class RejectedException extends IOException {
        public RejectedException(String m) {
            super(m);
        }
    }

    public static final class Found {
        public final String name, ip;
        public final int port;

        Found(String name, String ip, int port) {
            this.name = name;
            this.ip = ip;
            this.port = port;
        }
    }

    private final String host, code;
    private final int port;
    private volatile HttpURLConnection current;

    public PiClient(String host, int port, String code) {
        this.host = host;
        this.port = port;
        this.code = code;
    }

    /** Cut a waiting request short (used when the screen is closed or the person taps Sync). */
    public void abort() {
        HttpURLConnection c = current;
        if (c != null) c.disconnect();
    }

    private JSONObject request(String method, String path, int readTimeoutMs, String json) throws IOException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":" + port + path).openConnection();
        current = c;
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(4000);
            c.setReadTimeout(readTimeoutMs);
            c.setUseCaches(false);
            c.setRequestProperty("X-Code", code);
            if ("POST".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.getOutputStream().write((json == null ? "{}" : json).getBytes(StandardCharsets.UTF_8));
            }
            int status = c.getResponseCode();
            InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
            String body = in == null ? "" : read(in);
            if (status == 401 || status == 429) {
                String msg = "Wrong code";
                try {
                    msg = new JSONObject(body).optString("error", msg);
                } catch (JSONException ignored) {
                }
                throw new AuthException(msg);
            }
            if (status == 400 || status == 404 || status == 409) {          // an edit the Pi did not accept
                String msg = "The Pi did not accept that.";
                try {
                    msg = new JSONObject(body).optString("error", msg);
                } catch (JSONException ignored) {
                }
                if (status == 404 && path.startsWith("/edit") && msg.equals("Not found")) {
                    msg = "The Pi has an old watch_api.py. Copy the new one to the Pi and restart the reminder app.";
                }
                throw new RejectedException(msg);
            }
            if (status != 200) throw new IOException("Pi answered " + status);
            return new JSONObject(body);
        } finally {
            current = null;
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public JSONObject sync(int since, String epoch, int n, String digest, int wait) throws IOException, JSONException {
        String q = "/sync?since=" + since + "&epoch=" + URLEncoder.encode(epoch, "UTF-8") + "&n=" + n
                + "&digest=" + URLEncoder.encode(digest, "UTF-8") + "&wait=" + wait;
        return request("GET", q, (wait + 8) * 1000, null);
    }

    /** Ask the Pi to speak the daily briefing. The answer holds the text too. */
    public JSONObject brief() throws IOException, JSONException {
        return request("POST", "/brief", 8000, null);
    }

    public JSONObject ping() throws IOException, JSONException {
        return request("GET", "/ping", 6000, null);
    }

    /** Send one edit (see Edits). Throws RejectedException with the Pi's own words if it says no. */
    public void edit(JSONObject payload) throws IOException, JSONException {
        request("POST", "/edit", 10000, payload.toString());
    }

    /** Shout on the Wi-Fi "is there a Pi?" and collect the answers. */
    public static List<Found> discover(int timeoutMs) {
        try {
            return discover(InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT, timeoutMs);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static List<Found> discover(InetAddress target, int port, int timeoutMs) {
        List<Found> out = new ArrayList<>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(400);
            byte[] ask = "ROUTINE-PI?".getBytes(StandardCharsets.UTF_8);
            long end = System.currentTimeMillis() + timeoutMs;
            long nextSend = 0;
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() >= nextSend) {          // ask again every second (a packet can get lost)
                    s.send(new DatagramPacket(ask, ask.length, target, port));
                    nextSend = System.currentTimeMillis() + 1000;
                }
                try {
                    byte[] buf = new byte[512];
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    JSONObject o = new JSONObject(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
                    String ip = p.getAddress().getHostAddress();
                    boolean known = false;
                    for (Found f : out) if (f.ip.equals(ip)) known = true;
                    if (!known) out.add(new Found(o.optString("name", "Pi"), ip, o.optInt("port", DEFAULT_PORT)));
                } catch (java.net.SocketTimeoutException ignored) {
                } catch (JSONException ignored) {
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (s != null) s.close();
        }
        return out;
    }
}
