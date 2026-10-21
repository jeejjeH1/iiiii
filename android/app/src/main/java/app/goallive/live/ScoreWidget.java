package app.goallive.live;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Home-screen widget with "my team"'s live, next or last match.
 * The web app stores the chosen team in Capacitor Preferences (SharedPreferences "CapacitorStorage", key "myTeam").
 * Refreshes every minute while the match is live, more slowly otherwise.
 */
public class ScoreWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "app.goallive.live.WIDGET_REFRESH";
    static final String SITE = "https://iiiii-psi.vercel.app";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        refreshAsync(ctx);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refreshAsync(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        alarms(ctx).cancel(refreshIntent(ctx));
    }

    /** Ask every placed widget to refresh (used when the app goes to the background). */
    public static void requestUpdate(Context ctx) {
        Intent i = new Intent(ctx, ScoreWidget.class).setAction(ACTION_REFRESH);
        ctx.sendBroadcast(i);
    }

    private void refreshAsync(Context ctx) {
        final PendingResult pending = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                render(app);
            } catch (Exception e) {
                showMessage(app, "اتصال برقرار نشد · برای تلاش دوباره بزنید");
                schedule(app, 5 * 60_000L);
            } finally {
                pending.finish();
            }
        }).start();
    }

    private static void render(Context ctx) throws Exception {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ScoreWidget.class));
        if (ids.length == 0) return;

        SharedPreferences prefs = ctx.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE);
        String raw = prefs.getString("myTeam", null);
        if (raw == null) {
            showMessage(ctx, "اپ را باز کنید و «تیم من» را انتخاب کنید");
            return;
        }
        JSONObject team = new JSONObject(raw);
        String teamId = team.optString("id");
        JSONObject data = new JSONObject(get(SITE + "/api/widget?team=" + teamId));
        String state = data.optString("state", "none");
        JSONObject m = data.optJSONObject("match");

        RemoteViews v = base(ctx, team);
        if (m == null) {
            v.setViewVisibility(R.id.w_match, View.GONE);
            v.setViewVisibility(R.id.w_message, View.VISIBLE);
            v.setTextViewText(R.id.w_message, "فعلاً بازی‌ای برنامه‌ریزی نشده");
            mgr.updateAppWidget(ids, v);
            schedule(ctx, 60 * 60_000L);
            return;
        }
        JSONObject home = m.getJSONObject("home"), away = m.getJSONObject("away");
        v.setViewVisibility(R.id.w_match, View.VISIBLE);
        v.setViewVisibility(R.id.w_message, View.GONE);
        v.setTextViewText(R.id.w_league, m.optString("league"));
        v.setTextViewText(R.id.w_home, home.optString("name"));
        v.setTextViewText(R.id.w_away, away.optString("name"));

        long kickoff = m.optLong("kickoff");
        if ("upcoming".equals(state)) {
            v.setTextViewText(R.id.w_score, fa(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(kickoff))));
            v.setTextViewText(R.id.w_status, dayLabel(kickoff));
            v.setTextColor(R.id.w_status, Color.parseColor("#CCFFFFFF"));
        } else {
            // home score on the right, like the app (RTL)
            v.setTextViewText(R.id.w_score, fa(away.optInt("score") + " - " + home.optInt("score")));
            boolean live = "live".equals(state);
            String minute = m.optString("minute");
            v.setTextViewText(R.id.w_status, live ? "● زنده " + fa("HT".equals(minute) ? "بین دو نیمه" : minute) : "پایان بازی");
            v.setTextColor(R.id.w_status, live ? Color.parseColor("#FF4D6A") : Color.parseColor("#CCFFFFFF"));
        }
        Bitmap hb = logo(home.optLong("id")), ab = logo(away.optLong("id"));
        if (hb != null) v.setImageViewBitmap(R.id.w_home_logo, hb);
        if (ab != null) v.setImageViewBitmap(R.id.w_away_logo, ab);
        v.setTextViewText(R.id.w_updated, "به‌روزرسانی " + fa(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())));
        mgr.updateAppWidget(ids, v);

        // live: every minute; about to start: every 5 minutes; otherwise every half hour
        long untilKickoff = kickoff - System.currentTimeMillis();
        if ("live".equals(state)) schedule(ctx, 60_000L);
        else if ("upcoming".equals(state) && untilKickoff < 3 * 3600_000L) schedule(ctx, Math.max(60_000L, Math.min(5 * 60_000L, untilKickoff)));
        else schedule(ctx, 30 * 60_000L);
    }

    private static RemoteViews base(Context ctx, JSONObject team) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_score);
        v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx));
        String name = team.optString("fa", team.optString("name", ""));
        v.setTextViewText(R.id.w_title, "⭐ " + name);
        int color = Color.parseColor("#1B2A3A");
        try { color = Color.parseColor(team.optString("color", "#1B2A3A")); } catch (Exception ignored) {}
        v.setInt(R.id.w_bg, "setColorFilter", color);
        return v;
    }

    private static void showMessage(Context ctx, String text) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, ScoreWidget.class));
        if (ids.length == 0) return;
        String raw = ctx.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE).getString("myTeam", "{}");
        JSONObject team;
        try { team = new JSONObject(raw); } catch (Exception e) { team = new JSONObject(); }
        RemoteViews v = base(ctx, team);
        v.setViewVisibility(R.id.w_match, View.GONE);
        v.setViewVisibility(R.id.w_message, View.VISIBLE);
        v.setTextViewText(R.id.w_message, text);
        mgr.updateAppWidget(ids, v);
    }

    private static PendingIntent openApp(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent refreshIntent(Context ctx) {
        Intent i = new Intent(ctx, ScoreWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static AlarmManager alarms(Context ctx) {
        return (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
    }

    private static void schedule(Context ctx, long delayMs) {
        // inexact alarm: no special permission needed, still fires in Doze (with some delay)
        alarms(ctx).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, refreshIntent(ctx));
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try (InputStream in = c.getInputStream()) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    private static Bitmap logo(long id) {
        if (id <= 0) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://images.fotmob.com/image_resources/logo/teamlogo/" + id + "_small.png").openConnection();
            c.setConnectTimeout(8_000);
            c.setReadTimeout(8_000);
            try (InputStream in = c.getInputStream()) {
                byte[] b = readAll(in);
                return BitmapFactory.decodeByteArray(b, 0, b.length);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String dayLabel(long kickoff) {
        SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
        String k = day.format(new Date(kickoff)), today = day.format(new Date()), tomorrow = day.format(new Date(System.currentTimeMillis() + 86_400_000L));
        if (k.equals(today)) return "امروز";
        if (k.equals(tomorrow)) return "فردا";
        long days = Math.round((kickoff - System.currentTimeMillis()) / 86_400_000.0);
        return fa(String.valueOf(Math.max(days, 2))) + " روز دیگر";
    }

    private static String fa(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('۰' + (ch - '0')) : ch);
        return b.toString();
    }
}
