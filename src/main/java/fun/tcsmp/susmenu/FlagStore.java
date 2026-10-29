package fun.tcsmp.susmenu;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory store of anti-cheat flags (anti-cheats usually flag from async threads). */
final class FlagStore {
    record Flag(long time, String check, String info) {}

    record Suspect(UUID id, String name, List<Flag> flags) {}

    private static final int MAX_PER_PLAYER = 1000;

    private final Map<UUID, Deque<Flag>> flags = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    void add(UUID id, String name, String check, String info) {
        if (name != null) names.put(id, name);
        Deque<Flag> d = flags.computeIfAbsent(id, k -> new ArrayDeque<>());
        synchronized (d) {
            d.addLast(new Flag(System.currentTimeMillis(), check, info));
            while (d.size() > MAX_PER_PLAYER) d.pollFirst();
        }
    }

    /** Players with at least minFlags flags inside the expire window, most flagged first. */
    List<Suspect> snapshot(int minFlags, long expireMillis) {
        long cutoff = expireMillis > 0 ? System.currentTimeMillis() - expireMillis : Long.MIN_VALUE;
        List<Suspect> out = new ArrayList<>();
        for (Map.Entry<UUID, Deque<Flag>> e : flags.entrySet()) {
            Deque<Flag> d = e.getValue();
            List<Flag> copy;
            synchronized (d) {
                while (!d.isEmpty() && d.peekFirst().time() < cutoff) d.pollFirst();
                copy = new ArrayList<>(d);
            }
            if (!copy.isEmpty() && copy.size() >= minFlags) {
                out.add(new Suspect(e.getKey(), names.getOrDefault(e.getKey(), "?"), copy));
            }
        }
        out.sort((a, b) -> Integer.compare(b.flags().size(), a.flags().size()));
        return out;
    }
}
