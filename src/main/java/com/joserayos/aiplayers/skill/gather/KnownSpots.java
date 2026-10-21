package com.joserayos.aiplayers.skill.gather;

import com.joserayos.aiplayers.agent.AgentEntity;
import com.joserayos.aiplayers.perception.ResourceMap;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Bridges block matchers and the agent's remembered resource map (which stores block-type names). */
public final class KnownSpots {
    private KnownSpots() {}

    public static Predicate<String> typeFilter(BlockMatcher m) {
        return type -> {
            Identifier id = Identifier.tryParse("minecraft:" + type);
            if (id == null) return false;
            return BuiltInRegistries.BLOCK.getOptional(id).map(b -> m.test().test(b.defaultBlockState())).orElse(false);
        };
    }

    public static Optional<ResourceMap.Spot> nearest(AgentEntity agent, BlockMatcher m) {
        return agent.getResourceMap().nearest(agent.dimensionId(), agent.getX(), agent.getY(), agent.getZ(), typeFilter(m));
    }

    /** Known spot that is not within `skip` blocks of the agent (it already looked there). */
    public static Optional<ResourceMap.Spot> nearestElsewhere(AgentEntity agent, BlockMatcher m, double skip) {
        Predicate<String> f = typeFilter(m);
        var map = agent.getResourceMap();
        var spot = map.nearest(agent.dimensionId(), agent.getX(), agent.getY(), agent.getZ(), f);
        if (spot.isPresent() && spot.get().dist(agent.getX(), agent.getY(), agent.getZ()) <= skip) {
            // Already here and the scan found nothing usable: the spot is stale.
            map.forget(agent.dimensionId(), agent.getBlockX(), agent.getBlockY(), agent.getBlockZ(), skip, f);
            spot = map.nearest(agent.dimensionId(), agent.getX(), agent.getY(), agent.getZ(), f);
        }
        return spot;
    }
}
