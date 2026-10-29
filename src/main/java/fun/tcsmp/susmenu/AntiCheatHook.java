package fun.tcsmp.susmenu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Listens to an anti-cheat's flag event through reflection, so SusMenu has no compile-time
 * dependency on Grim, Vulcan, Matrix or Spartan and never breaks when they are missing.
 */
final class AntiCheatHook implements Listener {
    private record Spec(String plugin, List<String> events) {}

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();

    static {
        SPECS.put("grim", new Spec("GrimAC", List.of(
                "ac.grim.grimac.api.event.events.FlagEvent",
                "ac.grim.grimac.api.events.FlagEvent")));
        SPECS.put("vulcan", new Spec("Vulcan", List.of(
                "me.frep.vulcan.api.event.VulcanFlagEvent")));
        SPECS.put("matrix", new Spec("Matrix", List.of(
                "me.rerere.matrix.api.events.PlayerViolationEvent")));
        SPECS.put("spartan", new Spec("Spartan", List.of(
                "me.vagdedes.spartan.api.PlayerViolationEvent")));
    }

    private final SusMenu plugin;
    private final FlagStore store;

    AntiCheatHook(SusMenu plugin, FlagStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** @return true if at least one flag event was hooked */
    boolean hook(String name, String forcedClass) {
        String key = name == null ? "grim" : name.trim().toLowerCase(Locale.ROOT);
        Spec spec = SPECS.get(key);
        if (spec == null) {
            plugin.getLogger().warning("Unknown anti-cheat '" + name + "'. Use grim, vulcan, matrix or spartan.");
            return false;
        }
        Plugin ac = Bukkit.getPluginManager().getPlugin(spec.plugin());
        if (ac == null || !ac.isEnabled()) {
            plugin.getLogger().warning(spec.plugin() + " is not installed or not enabled, so no flags will be tracked.");
            return false;
        }
        ClassLoader loader = ac.getClass().getClassLoader();
        boolean forced = forcedClass != null && !forcedClass.isBlank();

        List<Class<? extends Event>> found = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (forced) names.add(forcedClass.trim());
        else names.addAll(spec.events());
        for (String n : names) {
            Class<? extends Event> c = load(loader, n);
            if (c != null) found.add(c);
        }
        if (found.isEmpty() && !forced) found.addAll(scan(ac, loader));

        if (found.isEmpty()) {
            plugin.getLogger().warning("Couldn't find " + spec.plugin() + "'s flag event. Flags won't be tracked. "
                    + "Set 'event-class' in config.yml if you know the class name.");
            return false;
        }
        for (Class<? extends Event> c : found) {
            Bukkit.getPluginManager().registerEvent(c, this, EventPriority.MONITOR, (l, e) -> handle(e), plugin, false);
            plugin.getLogger().info("Hooked into " + spec.plugin() + " via " + c.getName());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> load(ClassLoader loader, String name) {
        try {
            Class<?> c = Class.forName(name, false, loader);
            if (Event.class.isAssignableFrom(c) && !Modifier.isAbstract(c.getModifiers())) {
                return (Class<? extends Event>) c;
            }
        } catch (Throwable ignored) {
            // class doesn't exist in this version
        }
        return null;
    }

    /** Looks inside the anti-cheat's jar for a *FlagEvent / *ViolationEvent class. */
    private List<Class<? extends Event>> scan(Plugin ac, ClassLoader loader) {
        List<Class<? extends Event>> out = new ArrayList<>();
        try {
            CodeSource cs = ac.getClass().getProtectionDomain().getCodeSource();
            if (cs == null) return out;
            File file = new File(cs.getLocation().toURI());
            try (JarFile jar = new JarFile(file)) {
                for (Enumeration<JarEntry> en = jar.entries(); en.hasMoreElements(); ) {
                    String path = en.nextElement().getName();
                    if (!path.endsWith(".class") || path.contains("$")) continue;
                    String simple = path.substring(path.lastIndexOf('/') + 1, path.length() - 6);
                    if (!(simple.endsWith("FlagEvent") || simple.endsWith("ViolationEvent"))) continue;
                    if (simple.contains("Post") || simple.contains("Command") || simple.contains("Punish")) continue;
                    Class<? extends Event> c = load(loader, path.substring(0, path.length() - 6).replace('/', '.'));
                    if (c != null) out.add(c);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Couldn't scan " + ac.getName() + " for its flag event: " + t.getMessage());
        }
        return out;
    }

    private void handle(Event e) {
        try {
            Object who = call(e, "getPlayer", "getUser", "getOffender");
            UUID id = null;
            String pname = null;
            if (who instanceof Player p) {
                id = p.getUniqueId();
                pname = p.getName();
            } else if (who != null) {
                Object u = call(who, "getUniqueId", "getUuid", "getUUID");
                Object n = call(who, "getName", "getUsername");
                if (u instanceof UUID uu) id = uu;
                if (n != null) pname = n.toString();
            }
            if (id == null) return;
            String check = describeCheck(e);
            String info = str(call(e, "getVerbose", "getInfo", "getDetails", "getInformation", "getDebug"));
            store.add(id, pname, check, info);
        } catch (Throwable ignored) {
            // never break the anti-cheat's own event
        }
    }

    private static String describeCheck(Event e) {
        Object c = call(e, "getCheck", "getCheckName", "getHackType", "getType", "getCheckType");
        if (c == null) return "Unknown";
        if (c instanceof String s) return pretty(s);
        if (c instanceof Enum<?> en) return pretty(en.name());
        String name = str(call(c, "getCheckName", "getDisplayName", "getName", "name"));
        String type = str(call(c, "getType", "getCheckType"));
        if (name == null) name = c.toString();
        if (type != null && type.length() <= 3 && !type.equalsIgnoreCase(name)) name = name + " " + type;
        return pretty(name);
    }

    private static String pretty(String s) {
        String t = s.replace('_', ' ').trim();
        if (t.isEmpty()) return "Unknown";
        if (t.equals(t.toUpperCase(Locale.ROOT))) {
            StringBuilder sb = new StringBuilder();
            for (String w : t.toLowerCase(Locale.ROOT).split(" ")) {
                if (w.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            return sb.toString();
        }
        return t;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    /** Calls the first no-arg method from names that exists and returns something. */
    private static Object call(Object o, String... names) {
        if (o == null) return null;
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                    // fine, try anyway
                }
                Object r = m.invoke(o);
                if (r != null) return r;
            } catch (Throwable ignored) {
                // method doesn't exist, try the next name
            }
        }
        return null;
    }
}
