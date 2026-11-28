package io.github.gradientglass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.DyeColor;

final class GlassBlendGroups {
    static final List<String> DEFAULT = List.of("stained_blocks", "stained_panes");
    private static final Map<String, Long> MEMBERS = createMembers();
    private final long[] groups = new long[2 * GlassCell.TONE_COUNT];

    GlassBlendGroups(List<? extends String> definitions) {
        for (String definition : definitions) {
            long merged = members(definition);
            for (long remaining = merged; remaining != 0; remaining &= remaining - 1) {
                merged |= groups[Long.numberOfTrailingZeros(remaining)];
            }
            for (long remaining = merged; remaining != 0; remaining &= remaining - 1) {
                groups[Long.numberOfTrailingZeros(remaining)] = merged;
            }
        }
    }

    boolean connects(int first, int second) {
        return first == second || (groups[first] & (1L << second)) != 0;
    }

    static boolean isValid(Object value) {
        return value instanceof String definition && members(definition) != 0;
    }

    private static long members(String definition) {
        long mask = 0;
        for (String part : definition.split(",", -1)) {
            String id = part.strip();
            if (id.startsWith("minecraft:")) {
                id = id.substring("minecraft:".length());
            }
            Long member = MEMBERS.get(id);
            if (member == null) {
                return 0;
            }
            mask |= member;
        }
        return mask;
    }

    private static Map<String, Long> createMembers() {
        Map<String, Long> members = new HashMap<>();
        long blocks = 0;
        long panes = 0;
        for (DyeColor dye : DyeColor.values()) {
            long block = 1L << GlassCell.kind(dye.getId(), false);
            long pane = 1L << GlassCell.kind(dye.getId(), true);
            members.put(dye.getName() + "_stained_glass", block);
            members.put(dye.getName() + "_stained_glass_pane", pane);
            blocks |= block;
            panes |= pane;
        }
        members.put("stained_blocks", blocks);
        members.put("stained_panes", panes);
        members.put("glass", 1L << GlassCell.kind(GlassCell.CLEAR, false));
        members.put("glass_pane", 1L << GlassCell.kind(GlassCell.CLEAR, true));
        members.put("tinted_glass", 1L << GlassCell.kind(GlassCell.TINTED, false));
        return Map.copyOf(members);
    }
}
