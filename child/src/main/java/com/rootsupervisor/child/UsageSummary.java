package com.rootsupervisor.child;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shows recent usage on this device only. Does not transmit or persist usage data. */
public final class UsageSummary {
    private UsageSummary() {}

    public static String lastDay(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null || ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) != AppOpsManager.MODE_ALLOWED) {
            return "Falta conceder acceso a estadísticas de uso desde Ajustes.";
        }
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return "El sistema no ofrece estadísticas de uso.";
        long now = System.currentTimeMillis();
        Map<String, UsageStats> usage = manager.queryAndAggregateUsageStats(now - 86_400_000L, now);
        if (usage == null || usage.isEmpty()) return "No hay datos de uso recientes.";
        List<UsageStats> entries = new ArrayList<>(usage.values());
        entries.removeIf(s -> s == null || s.getTotalTimeInForeground() <= 0L);
        entries.sort(Comparator.comparingLong(UsageStats::getTotalTimeInForeground).reversed());
        StringBuilder out = new StringBuilder("Últimas 24 h (aproximación de Android):\n");
        int count = Math.min(5, entries.size());
        for (int i = 0; i < count; i++) {
            UsageStats s = entries.get(i);
            long mins = s.getTotalTimeInForeground() / 60_000L;
            out.append(i+1).append(". ").append(s.getPackageName()).append(": ")
               .append(String.format(Locale.getDefault(), "%d h %02d min", mins/60L, mins%60L)).append("\n");
        }
        return count == 0 ? "Todavía no hay uso registrado." : out.toString();
    }
}
