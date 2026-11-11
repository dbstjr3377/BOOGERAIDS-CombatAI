package com.boogeraids.combatai.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.function.Supplier;

/** 사운드/파티클 헬퍼. 게임 참가자 전원에게만 재생. */
public final class Fx {
    private final Supplier<Collection<? extends Player>> audience;

    public Fx(Supplier<Collection<? extends Player>> audience) {
        this.audience = audience;
    }

    public void sound(String key, float volume, float pitch) {
        Sound s = Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
        for (Player p : audience.get()) p.playSound(s, Sound.Emitter.self());
    }

    public void dust(Location l, int rgb, float size, int count, double spread) {
        l.getWorld().spawnParticle(Particle.DUST, l, count, spread, spread, spread, 0,
                new Particle.DustOptions(Color.fromRGB(rgb), size), true);
    }

    public void particle(Particle p, Location l, int count, double spread, double speed) {
        l.getWorld().spawnParticle(p, l, count, spread, spread, spread, speed, null, true);
    }

    /** a → b 직선 위에 먼지 파티클. */
    public void line(Location a, Location b, int rgb, float size, double step) {
        double dist = a.distance(b);
        if (dist < 1e-4) return;
        int n = (int) Math.ceil(dist / step);
        Particle.DustOptions opt = new Particle.DustOptions(Color.fromRGB(rgb), size);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            Location p = a.clone().add(b.clone().subtract(a).toVector().multiply(t));
            a.getWorld().spawnParticle(Particle.DUST, p, 1, 0, 0, 0, 0, opt, true);
        }
    }
}
