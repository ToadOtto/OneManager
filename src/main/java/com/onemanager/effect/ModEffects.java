package com.onemanager.effect;

import com.onemanager.OneManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, OneManager.MOD_ID);

    public static final DeferredHolder<MobEffect, AncestorBurdenEffect> ANCESTOR_BURDEN = EFFECTS.register("ancestor_burden",
            AncestorBurdenEffect::new);
}
