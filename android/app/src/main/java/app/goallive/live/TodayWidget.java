package app.goallive.live;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * "بازی‌های امروز" widget: up to five of today's matches from /api/today, the user's teams first.
 * Every minute or two while one of them is live, every 15 minutes otherwise.
 */
public class TodayWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "app.goallive.live.TODAY_REFRESH";
    private static final int[] ROW = {R.id.t_row0, R.id.t_row1, R.id.t_row2, R.id.t_row3, R.id.t_row4};
    private static final int[] HOME = {R.id.t_home0, R.id.t_home1, R.id.t_home2, R.id.t_home3, R.id.t_home4};
    private static final int[] MID = {R.id.t_mid0, R.id.t_mid1, R.id.t_mid2, R.id.t_mid3, R.id.t_mid4};
    private static final int[] AWAY = {R.id.t_away0, R.id.t_away1, R.id.t_away2, R.id.t_away3, R.id.t_away4};

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
        ScoreWidget.alarms(ctx).cancel(refreshIntent(ctx));
    }

    public static void requestUpdate(Context ctx) {
        ctx.sendBroadcast(new Intent(ctx, TodayWidget.class).setAction(ACTION_REFRESH));
    }

    private void refreshAsync(Context ctx) {
        final PendingResult pending = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                render(app);
            } catch (Exception e) {
                message(app, "اتصال برقرار نشد · برای تلاش دوباره بزنید");
                schedule(app, 5 * 60_000L);
            } finally {
                pending.finish();
            }
        }).start();
    }

    /** The user's teams: "my team" and the followed ones. */
    private static String teams(Context ctx) {
        Set<String> ids = new LinkedHashSet<>();
        try {
            String my = ctx.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE).getString("myTeam", null);
            if (my != null) ids.add(new JSONObject(my).optString("id"));
            SharedPreferences p = ctx.getSharedPreferences("goallive", Context.MODE_PRIVATE);
            Iterator<String> it = new JSONObject(p.getString("teams", "{}")).keys();
            while (it.hasNext()) ids.add(it.next());
        } catch (Exception ignored) {}
        ids.remove("");
        return android.text.TextUtils.join(",", ids);
    }

    private static void render(Context ctx) throws Exception {
        int[] ids = ScoreWidget.ids(ctx, TodayWidget.class);
        if (ids.length == 0) return;
        String body;
        try { body = ScoreWidget.get(ScoreWidget.SITE + "/api/today?teams=" + teams(ctx)); } catch (Exception e) { body = DirectData.today(teams(ctx)); }
        JSONArray list = new JSONObject(body).optJSONArray("matches");
        RemoteViews v = base(ctx);
        int n = list == null ? 0 : Math.min(list.length(), ROW.length);
        boolean anyLive = false;
        long nextKickoff = Long.MAX_VALUE;
        for (int i = 0; i < ROW.length; i++) {
            if (i >= n) { v.setViewVisibility(ROW[i], View.GONE); continue; }
            JSONObject m = list.getJSONObject(i);
            JSONObject h = m.getJSONObject("home"), a = m.getJSONObject("away");
            String state = m.optString("state");
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setTextViewText(HOME[i], ScoreWidget.teamName(ctx, h));
            v.setTextViewText(AWAY[i], ScoreWidget.teamName(ctx, a));
            if ("upcoming".equals(state)) {
                long k = m.optLong("kickoff");
                nextKickoff = Math.min(nextKickoff, k);
                v.setTextViewText(MID[i], ScoreWidget.fa(new SimpleDateFormat("HH:mm", Locale.US).format(new Date(k))));
                v.setTextColor(MID[i], Color.parseColor("#F3F6F8"));
            } else {
                boolean live = "live".equals(state);
                anyLive |= live;
                // LTR text: away score on the left, home on the right (RTL rows)
                v.setTextViewText(MID[i], ScoreWidget.fa(a.optInt("score") + " - " + h.optInt("score")));
                v.setTextColor(MID[i], Color.parseColor(live ? "#FF3358" : "#8D97A3"));
            }
            v.setOnClickPendingIntent(ROW[i], openMatch(ctx, m.optInt("id"), i));
        }
        v.setViewVisibility(R.id.t_message, n == 0 ? View.VISIBLE : View.GONE);
        if (n == 0) v.setTextViewText(R.id.t_message, "امروز بازی مهمی نیست");
        v.setTextViewText(R.id.t_updated, ScoreWidget.fa(new SimpleDateFormat("HH:mm", Locale.US).format(new Date())));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v);
        long untilNext = nextKickoff - System.currentTimeMillis();
        if (anyLive) schedule(ctx, 90_000L);
        else if (untilNext > 0 && untilNext < 60 * 60_000L) schedule(ctx, Math.max(60_000L, untilNext));
        else schedule(ctx, 15 * 60_000L);
    }

    private static RemoteViews base(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_today);
        v.setOnClickPendingIntent(R.id.widget_root, ScoreWidget.openApp(ctx));
        return v;
    }

    private static void message(Context ctx, String text) {
        int[] ids = ScoreWidget.ids(ctx, TodayWidget.class);
        if (ids.length == 0) return;
        RemoteViews v = base(ctx);
        for (int r : ROW) v.setViewVisibility(r, View.GONE);
        v.setViewVisibility(R.id.t_message, View.VISIBLE);
        v.setTextViewText(R.id.t_message, text);
        v.setOnClickPendingIntent(R.id.t_message, refreshIntent(ctx));
        AppWidgetManager.getInstance(ctx).updateAppWidget(ids, v);
    }

    private static PendingIntent openMatch(Context ctx, int id, int slot) {
        Intent i = new Intent(ctx, MainActivity.class).putExtra("match", id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(ctx, 300 + slot, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent refreshIntent(Context ctx) {
        Intent i = new Intent(ctx, TodayWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void schedule(Context ctx, long delayMs) {
        ScoreWidget.alarms(ctx).setAndAllowWhileIdle(android.app.AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, refreshIntent(ctx));
    }
}
