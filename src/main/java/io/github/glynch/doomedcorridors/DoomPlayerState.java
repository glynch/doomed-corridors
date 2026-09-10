/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Mutable project-runtime health and ammunition state for one player entity. */
final class DoomPlayerState implements DoomDamageable, DoomRuleConsumer, ComponentEndpointBinder {
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private int health;
    private int maximumHealth;
    private int armor;
    private int maximumArmor;
    private int armorProtectionPercent;
    private int bullets;
    private int maximumBullets;
    private int shells;
    private int maximumShells;
    private final Set<String> weapons = new LinkedHashSet<>();
    private final Map<String, DoomCombatRules.Ammunition> weaponAmmunition = new LinkedHashMap<>();
    private String activeWeapon;
    private RuntimeSignal hurtSignal;
    private RuntimeSignal diedSignal;
    private boolean invulnerable;
    private boolean configured;

    /** Retains explicit source-asset references until application preparation loads their rules. */
    DoomPlayerState(ResourceReference actorCatalog, ResourceReference combatRules) {
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
    }

    /** Returns the actor-catalog source selected by the authored player component. */
    @Override
    public ResourceReference actorCatalog() {
        return actorCatalog;
    }

    /** Returns the combat-rules source selected by the authored player component. */
    @Override
    public ResourceReference combatRules() {
        return combatRules;
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        hurtSignal = validEndpoints.signal(DoomedCorridorsDescriptors.HURT_SIGNAL);
        diedSignal = validEndpoints.signal(DoomedCorridorsDescriptors.DIED_SIGNAL);
    }

    /** Initializes resources exactly once from validated provider rules before world activation. */
    @Override
    public void configure(DoomCombatRules rules) {
        DoomCombatRules validRules = Objects.requireNonNull(rules, "rules");
        if (configured) {
            throw new IllegalStateException("player state is already configured");
        }
        health = validRules.startingHealth();
        maximumHealth = validRules.maximumHealth();
        armor = validRules.startingArmor();
        maximumArmor = validRules.maximumArmor();
        bullets = validRules.startingBullets();
        maximumBullets = validRules.maximumBullets();
        shells = validRules.startingShells();
        maximumShells = validRules.maximumShells();
        activeWeapon = validRules.primaryWeaponId();
        weapons.add(activeWeapon);
        validRules
                .weaponIds()
                .forEach(weaponId -> weaponAmmunition.put(weaponId, validRules.weaponAmmunition(weaponId)));
        configured = true;
    }

    /** Selects whether incoming damage is ignored for a local playtest launch. */
    void setInvulnerable(boolean invulnerable) {
        if (configured) {
            throw new IllegalStateException("player invulnerability must be selected before configuration");
        }
        this.invulnerable = invulnerable;
    }

    /** Returns current player health after application preparation. */
    @Override
    public int health() {
        requireConfigured();
        return health;
    }

    /** Returns current bullet ammunition after application preparation. */
    int bullets() {
        requireConfigured();
        return bullets;
    }

    /** Returns current shell ammunition after application preparation. */
    int shells() {
        requireConfigured();
        return shells;
    }

    /** Returns current armour points after application preparation. */
    int armor() {
        requireConfigured();
        return armor;
    }

    /** Returns the stable identifier of the currently selected weapon. */
    String activeWeapon() {
        requireConfigured();
        return activeWeapon;
    }

    /** Returns the ammunition count displayed for the currently selected weapon. */
    int activeAmmunition() {
        DoomCombatRules.Ammunition ammunition = weaponAmmunition.get(activeWeapon());
        if (ammunition == null) {
            throw new IllegalStateException("active weapon has no configured ammunition pool: " + activeWeapon());
        }
        return ammunition(ammunition);
    }

    /** Selects an owned weapon and reports whether selection changed. */
    boolean selectWeapon(String weaponId) {
        requireConfigured();
        String selected = Objects.requireNonNull(weaponId, "weaponId");
        if (!weapons.contains(selected) || activeWeapon.equals(selected)) {
            return false;
        }
        activeWeapon = selected;
        return true;
    }

    /** Returns whether this player currently owns the named weapon. */
    boolean ownsWeapon(String weaponId) {
        requireConfigured();
        return weapons.contains(Objects.requireNonNull(weaponId, "weaponId"));
    }

    /** Spends the requested positive bullet amount when available. */
    boolean spendBullets(int amount) {
        return spendAmmunition(DoomCombatRules.Ammunition.BULLETS, amount);
    }

    /** Spends the requested positive amount from one ammunition pool when available. */
    boolean spendAmmunition(DoomCombatRules.Ammunition ammunition, int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        int available = ammunition(Objects.requireNonNull(ammunition, "ammunition"));
        if (available < amount) {
            return false;
        }
        if (ammunition == DoomCombatRules.Ammunition.BULLETS) {
            bullets -= amount;
        } else {
            shells -= amount;
        }
        return true;
    }

    /** Applies positive incoming damage, emits the corresponding state signal, and returns the accepted amount. */
    @Override
    public int damage(int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        if (invulnerable) {
            return 0;
        }
        int absorbed = Math.min(armor, amount * armorProtectionPercent / 100);
        armor -= absorbed;
        int appliedToHealth = (int) Math.min(health, (long) amount - absorbed);
        health -= appliedToHealth;
        if (appliedToHealth == 0 && absorbed == 0) {
            return 0;
        }
        if (health > 0) {
            requiredHurtSignal().emit();
        } else {
            requiredDiedSignal().emit();
        }
        return appliedToHealth;
    }

    /** Applies one useful pickup and returns the exact amount accepted by the player. */
    boolean collect(
            DoomPickup.Resource resource,
            int amount,
            int limit,
            int protectionPercent,
            Optional<String> grantedWeapon) {
        requireConfigured();
        int applied =
                switch (Objects.requireNonNull(resource, "resource")) {
                    case HEALTH -> {
                        int accepted = acceptedAmount(health, maximumHealth, amount, limit);
                        health += accepted;
                        yield accepted;
                    }
                    case ARMOR -> {
                        int accepted = acceptedAmount(armor, maximumArmor, amount, limit);
                        armor += accepted;
                        if (accepted > 0) {
                            armorProtectionPercent = Math.max(armorProtectionPercent, protectionPercent);
                        }
                        yield accepted;
                    }
                    case BULLETS -> {
                        int accepted = acceptedAmount(bullets, maximumBullets, amount, limit);
                        bullets += accepted;
                        yield accepted;
                    }
                    case SHELLS -> {
                        int accepted = acceptedAmount(shells, maximumShells, amount, limit);
                        shells += accepted;
                        yield accepted;
                    }
                };
        boolean weaponGranted = grantedWeapon.map(weapons::add).orElse(false);
        grantedWeapon.filter(weapon -> weaponGranted).ifPresent(weapon -> activeWeapon = weapon);
        return applied > 0 || weaponGranted;
    }

    /** Returns the current count in one ammunition pool. */
    private int ammunition(DoomCombatRules.Ammunition ammunition) {
        return switch (ammunition) {
            case BULLETS -> bullets;
            case SHELLS -> shells;
        };
    }

    /** Computes one bounded positive resource increase without overflowing integer arithmetic. */
    private static int acceptedAmount(int current, int capacity, int amount, int limit) {
        int effectiveLimit = Math.min(capacity, limit);
        long available = Math.max(0L, (long) effectiveLimit - current);
        return (int) Math.min(available, amount);
    }

    /** Requires the reference to address one manifest-declared source asset. */
    private static ResourceReference requireSourceAsset(ResourceReference reference, String name) {
        ResourceReference validReference = Objects.requireNonNull(reference, name);
        if (validReference.kind() != ResourceReference.Kind.ASSET) {
            throw new IllegalArgumentException(name + " must reference a source asset");
        }
        return validReference;
    }

    /** Rejects resource access before application preparation. */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("player state has not been configured");
        }
    }

    /** Returns the descriptor-declared non-fatal damage signal. */
    private RuntimeSignal requiredHurtSignal() {
        if (hurtSignal == null) {
            throw new IllegalStateException("player hurt signal has not been bound");
        }
        return hurtSignal;
    }

    /** Returns the descriptor-declared terminal damage signal. */
    private RuntimeSignal requiredDiedSignal() {
        if (diedSignal == null) {
            throw new IllegalStateException("player died signal has not been bound");
        }
        return diedSignal;
    }
}
