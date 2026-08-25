package cn.cloudfl4re.boatrace.service;

import cn.cloudfl4re.boatrace.model.Cuboid;
import cn.cloudfl4re.boatrace.model.StartSlot;
import cn.cloudfl4re.boatrace.model.Track;
import cn.cloudfl4re.boatrace.model.TrackDraft;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TrackValidator {
    private static final Pattern ID = Pattern.compile("[a-z0-9-]{1,32}");

    private TrackValidator() {
    }

    public static boolean validId(String value) {
        return value != null && ID.matcher(value.toLowerCase(Locale.ROOT)).matches() && value.equals(value.toLowerCase(Locale.ROOT));
    }

    public static Optional<String> validate(TrackDraft draft, Collection<Track> existing) {
        if (!validId(draft.id())) {
            return Optional.of("赛道 ID 无效");
        }
        if (draft.displayName() == null || draft.displayName().isBlank() || draft.displayName().length() > 64) {
            return Optional.of("显示名不能为空且不能超过 64 个字符");
        }
        if (draft.worldId() == null || draft.start() == null) {
            return Optional.of("尚未设置起点范围");
        }
        if (!draft.start().finite()) {
            return Optional.of("起点范围包含无效坐标");
        }
        if (draft.checkpoints().isEmpty()) {
            return Optional.of("至少需要一个记录点");
        }
        if (draft.slots().isEmpty()) {
            return Optional.of("至少需要一个发车位");
        }
        if (draft.checkpoints().stream().anyMatch(gate -> !gate.finite())) {
            return Optional.of("记录点包含无效坐标");
        }
        if (draft.slots().stream().anyMatch(slot -> !slot.finite())) {
            return Optional.of("发车位包含无效坐标");
        }
        for (Track track : existing) {
            if (!track.id().equals(draft.id()) && track.worldId().equals(draft.worldId()) && track.start().overlaps(draft.start())) {
                return Optional.of("起点范围与赛道 " + track.id() + " 重叠");
            }
        }
        for (int first = 0; first < draft.checkpoints().size(); first++) {
            Cuboid gate = draft.checkpoints().get(first);
            for (int second = first + 1; second < draft.checkpoints().size(); second++) {
                if (gate.overlaps(draft.checkpoints().get(second))) {
                    return Optional.of("记录点 " + (first + 1) + " 与记录点 " + (second + 1) + " 重叠");
                }
            }
        }
        for (StartSlot slot : draft.slots()) {
            if (slot.y() < -2048.0 || slot.y() > 4096.0) {
                return Optional.of("发车位高度超出有效范围");
            }
        }
        return Optional.empty();
    }
}
