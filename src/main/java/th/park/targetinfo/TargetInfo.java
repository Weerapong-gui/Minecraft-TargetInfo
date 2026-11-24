package th.park.targetinfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Shows "❤ 15/20 ⛨ 4" above the mob or player you are aiming at. Each viewer gets their own text display,
 * hidden from everyone else, moved along with the target every tick rather than riding it
 * (a passenger would stop the target being teleported).
 */
public final class TargetInfo extends JavaPlugin implements Listener {
    private record View(UUID target, TextDisplay display, int lastSeenTick) {}

    private final Map<UUID, View> views = new HashMap<>();
    private double range;
    private int lingerTicks;
    private boolean players;
    private int tick;
    /** DataStack hides its "xN" stack labels beyond this many blocks per viewer; 0 = always shown. */
    private double stackLabelRange;
    private static final org.bukkit.NamespacedKey STACK_LABEL = new org.bukkit.NamespacedKey("datastack", "label");

    @Override public void onEnable() {
        saveDefaultConfig();
        range = Math.max(4, getConfig().getDouble("range", 24));
        lingerTicks = Math.max(0, getConfig().getInt("linger-ticks", 30));
        players = getConfig().getBoolean("show-players", true);
        var ds = getServer().getPluginManager().getPlugin("DataStack");
        stackLabelRange = ds == null ? 0 : Math.max(0, ds.getConfig().getDouble("label-range", 10));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1, 1);
    }

    @Override public void onDisable() {
        for (View v : views.values()) v.display().remove();
        views.clear();
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        View v = views.remove(e.getPlayer().getUniqueId());
        if (v != null) v.display().remove();
    }

    private void tick() {
        tick++;
        for (Player p : getServer().getOnlinePlayers()) {
            LivingEntity aimed = aimed(p);
            View v = views.get(p.getUniqueId());
            if (aimed != null) {
                if (v == null || !v.target().equals(aimed.getUniqueId()) || !v.display().isValid()
                        || v.display().getWorld() != aimed.getWorld()) {
                    if (v != null) v.display().remove();
                    v = new View(aimed.getUniqueId(), spawn(p, aimed), tick);
                } else {
                    v = new View(v.target(), v.display(), tick);
                }
                views.put(p.getUniqueId(), v);
            }
            if (v == null) continue;
            Entity target = getServer().getEntity(v.target());
            boolean expired = tick - v.lastSeenTick() > lingerTicks;
            if (expired || !(target instanceof LivingEntity le) || !le.isValid() || le.isDead()
                    || !v.display().isValid() || le.getWorld() != p.getWorld()) {
                v.display().remove();
                views.remove(p.getUniqueId());
                continue;
            }
            update(p, v.display(), le);
        }
        // views of players who left without a quit event (reload) are cleared here
        for (Iterator<Map.Entry<UUID, View>> it = views.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (getServer().getPlayer(e.getKey()) == null) { e.getValue().display().remove(); it.remove(); }
        }
    }

    /** The living thing under the crosshair, stopping at blocks; null if none worth showing. */
    private LivingEntity aimed(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return null;
        Entity e = p.getTargetEntity((int) range);
        if (!(e instanceof LivingEntity le) || le instanceof ArmorStand) return null;
        if (le.isInvisible() || le.hasPotionEffect(PotionEffectType.INVISIBILITY)) return null;
        if (le instanceof Player other && (!players || other.getGameMode() == GameMode.SPECTATOR)) return null;
        return le;
    }

    private TextDisplay spawn(Player viewer, LivingEntity target) {
        TextDisplay d = target.getWorld().spawn(at(viewer, target), TextDisplay.class, t -> {
            t.setVisibleByDefault(false); // set before it enters the world, so nobody else ever sees it
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setTeleportDuration(1);
            t.text(text(target));
        });
        viewer.showEntity(this, d);
        return d;
    }

    /** Whether viewer currently sees a name tag over target, so the numbers go above it. */
    /**
     * Whether viewer currently sees a name over target, so the numbers go above it. Like the client:
     * an always-visible name (a DataStack label only within its label range), or any custom name
     * (e.g. from a name tag) while the target is under the crosshair within the viewer's reach.
     */
    private boolean nameTagShown(Player viewer, LivingEntity target) {
        if (target instanceof Player) return true;
        if (target.customName() == null) return false;
        if (withinReach(viewer, target)) return true;
        if (!target.isCustomNameVisible()) return false;
        if (stackLabelRange > 0 && target.getPersistentDataContainer().has(STACK_LABEL)) {
            return viewer.getLocation().distanceSquared(target.getLocation()) <= stackLabelRange * stackLabelRange;
        }
        return true;
    }

    /** Eye to the nearest point of the target's hitbox, against the viewer's entity interaction range. */
    private static boolean withinReach(Player viewer, LivingEntity target) {
        var attr = viewer.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double reach = attr != null ? attr.getValue() : 3.0;
        var eye = viewer.getEyeLocation().toVector();
        var box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.getMinX() - eye.getX(), 0), eye.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - eye.getY(), 0), eye.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - eye.getZ(), 0), eye.getZ() - box.getMaxZ());
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private Location at(Player viewer, LivingEntity target) {
        boolean nameTag = nameTagShown(viewer, target);
        return target.getLocation().add(0, Fmt.lift(target.getHeight(), nameTag), 0).setDirection(new org.bukkit.util.Vector(0, 0, 1));
    }

    private void update(Player viewer, TextDisplay d, LivingEntity target) {
        d.teleport(at(viewer, target));
        Component now = text(target);
        if (!now.equals(d.text())) d.text(now);
    }

    static Component text(LivingEntity e) {
        AttributeInstance maxAttr = e.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : e.getHealth();
        Component c = Component.text("❤ ", NamedTextColor.RED)
            .append(Component.text(Fmt.hp(e.getHealth()) + "/" + Fmt.hp(max), NamedTextColor.WHITE));
        double absorb = e.getAbsorptionAmount();
        if (absorb > 0) c = c.append(Component.text(" +" + Fmt.hp(absorb), NamedTextColor.YELLOW));
        AttributeInstance armorAttr = e.getAttribute(Attribute.ARMOR);
        double armor = armorAttr != null ? armorAttr.getValue() : 0;
        if (armor > 0) c = c.append(Component.text("  ⛨ " + Fmt.hp(armor), NamedTextColor.AQUA));
        return c;
    }
}
