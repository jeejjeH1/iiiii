package app.goallive.live;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * The widget answers of /api/widget and /api/today, worked out on the phone straight from FotMob.
 * Used when our server can't be reached, so the widgets keep working.
 */
public final class DirectData {
    private DirectData() {}

    private static final Set<Integer> BIG = new HashSet<>(Arrays.asList(42, 47, 87, 55, 54, 53, 73, 523, 77, 50, 44, 10216));

    static String fotmob(String path, String query) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://www.fotmob.com/api/" + path + "?" + query).openConnection();
        c.setConnectTimeout(12_000);
        c.setReadTimeout(15_000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        c.setRequestProperty("Referer", "https://www.fotmob.com/");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static String day(long ms, String zone) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return f.format(new Date(ms));
    }

    private static long utc(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(s.substring(0, 19)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    private static JSONObject side(JSONObject t) throws Exception {
        JSONObject o = new JSONObject().put("id", t.optLong("id")).put("name", t.optString("shortName", t.optString("name")));
        if (t.has("score") && !t.isNull("score")) o.put("score", t.optInt("score"));
        return o;
    }

    private static String minute(JSONObject status) {
        JSONObject lt = status.optJSONObject("liveTime");
        return lt == null ? "" : lt.optString("short").replace("‎", "").replace("‏", "").replace("’", "'");
    }

    /** Same shape as /api/widget: { state: live|finished|upcoming|none, match: {...} }. */
    public static String widget(String teamId) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject live = null, today = null, recent = null;
        String liveLg = "", todayLg = "", recentLg = "";
        for (long d : new long[]{now - 86_400_000L, now}) {
            JSONArray leagues;
            try { leagues = new JSONObject(fotmob("data/matches", "date=" + day(d, "UTC") + "&timezone=UTC")).optJSONArray("leagues"); } catch (Exception e) { continue; }
            if (leagues == null) continue;
            for (int i = 0; i < leagues.length(); i++) {
                JSONObject l = leagues.getJSONObject(i);
                JSONArray ms = l.optJSONArray("matches");
                if (ms == null) continue;
                for (int j = 0; j < ms.length(); j++) {
                    JSONObject m = ms.getJSONObject(j);
                    if (!teamId.equals(m.getJSONObject("home").optString("id")) && !teamId.equals(m.getJSONObject("away").optString("id"))) continue;
                    JSONObject s = m.getJSONObject("status");
                    long t = utc(s.optString("utcTime"));
                    if (s.optBoolean("started") && !s.optBoolean("finished") && !s.optBoolean("cancelled")) { live = m; liveLg = l.optString("name"); }
                    else if (s.optBoolean("finished") && now - t < 20 * 3_600_000L && (recent == null || t > utc(recent.getJSONObject("status").optString("utcTime")))) { recent = m; recentLg = l.optString("name"); }
                    else if (!s.optBoolean("started") && !s.optBoolean("cancelled") && (today == null || t < utc(today.getJSONObject("status").optString("utcTime")))) { today = m; todayLg = l.optString("name"); }
                }
            }
        }
        JSONObject pick = live != null ? live : today != null ? today : recent;
        String state = live != null ? "live" : today != null ? "upcoming" : recent != null ? "finished" : "none";
        String lg = live != null ? liveLg : today != null ? todayLg : recentLg;
        JSONObject match = null;
        if (pick != null) {
            JSONObject s = pick.getJSONObject("status");
            match = new JSONObject().put("id", pick.optLong("id")).put("league", lg).put("kickoff", utc(s.optString("utcTime")))
                .put("minute", minute(s)).put("home", side(pick.getJSONObject("home"))).put("away", side(pick.getJSONObject("away")));
        } else {
            JSONObject n = new JSONObject(fotmob("data/teams", "id=" + teamId)).optJSONObject("overview");
            n = n == null ? null : n.optJSONObject("nextMatch");
            if (n != null) {
                state = "upcoming";
                JSONObject tour = n.optJSONObject("tournament");
                match = new JSONObject().put("id", n.optLong("id")).put("league", tour == null ? "" : tour.optString("name"))
                    .put("kickoff", utc(n.getJSONObject("status").optString("utcTime"))).put("minute", "")
                    .put("home", side(n.getJSONObject("home"))).put("away", side(n.getJSONObject("away")));
            }
        }
        JSONObject out = new JSONObject().put("state", state);
        if (match != null) out.put("match", match);
        return out.toString();
    }

    /** Same shape as /api/today: { matches: [...] }, the user's teams first, then live and upcoming big games; at most 5. */
    public static String today(String teamsCsv) throws Exception {
        Set<String> teams = new HashSet<>(Arrays.asList(teamsCsv.split(",")));
        JSONArray leagues = new JSONObject(fotmob("data/matches", "date=" + day(System.currentTimeMillis(), "Asia/Tehran") + "&timezone=" + URLEncoder.encode("Asia/Tehran", "UTF-8")))
            .optJSONArray("leagues");
        List<Object[]> all = new ArrayList<>();
        for (int i = 0; leagues != null && i < leagues.length(); i++) {
            JSONObject l = leagues.getJSONObject(i);
            JSONArray ms = l.optJSONArray("matches");
            for (int j = 0; ms != null && j < ms.length(); j++) {
                JSONObject m = ms.getJSONObject(j);
                JSONObject s = m.getJSONObject("status");
                if (s.optBoolean("cancelled")) continue;
                boolean mine = teams.contains(m.getJSONObject("home").optString("id")) || teams.contains(m.getJSONObject("away").optString("id"));
                if (!mine && !BIG.contains(l.optInt("primaryId"))) continue;
                String state = s.optBoolean("started") && !s.optBoolean("finished") ? "live" : s.optBoolean("finished") ? "finished" : "upcoming";
                int rank = (mine ? 0 : 10) + ("live".equals(state) ? 0 : "upcoming".equals(state) ? 1 : 2);
                long t = utc(s.optString("utcTime"));
                JSONObject o = new JSONObject().put("id", m.optLong("id")).put("league", l.optString("name")).put("kickoff", t).put("state", state)
                    .put("minute", minute(s)).put("home", side(m.getJSONObject("home"))).put("away", side(m.getJSONObject("away")));
                all.add(new Object[]{rank, t, o});
            }
        }
        all.sort((a, b) -> (int) a[0] != (int) b[0] ? Integer.compare((int) a[0], (int) b[0]) : Long.compare((long) a[1], (long) b[1]));
        JSONArray out = new JSONArray();
        for (int i = 0; i < Math.min(5, all.size()); i++) out.put(all.get(i)[2]);
        return new JSONObject().put("matches", out).toString();
    }
}
