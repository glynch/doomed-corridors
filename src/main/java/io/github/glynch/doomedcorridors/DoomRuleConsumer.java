/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.jscene3d.project.value.ResourceReference;

/** Internal initialization contract used only after descriptor capabilities select a runtime component. */
interface DoomRuleConsumer {
    /** Returns the actor-catalog source selected by the authored component. */
    ResourceReference actorCatalog();

    /** Returns the combat-rules source selected by the authored component. */
    ResourceReference combatRules();

    /** Initializes the component exactly once from validated provider rules. */
    void configure(DoomCombatRules rules);
}
