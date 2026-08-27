package cn.cloudfl4re.boatrace.gui;

import java.util.Locale;

public enum GuiAction {
    OPEN_MAIN("open-main"), OPEN_RACE_CONTROL("open-race-control"), OPEN_TRACK_LIST("open-track-list"),
    OPEN_TRACK_EDITOR("open-track-editor"), OPEN_CONFIRM("open-confirm"), RACE_CREATE("race-create"),
    RACE_JOIN("race-join"), RACE_START("race-start"), RACE_LEAVE("race-leave"), RACE_CANCEL("race-cancel"),
    RACE_STATUS("race-status"), RACE_LAST("race-last"), RACE_LEADERBOARD("race-leaderboard"),
    RACE_PAUSE("race-pause"), RACE_RESUME("race-resume"), RACE_END("race-end"), TRACK_CREATE("track-create"),
    TRACK_LIST("track-list"), TRACK_INFO("track-info"), TRACK_EDIT("track-edit"), TRACK_DELETE("track-delete"),
    EDIT_POS1("edit-pos1"), EDIT_POS2("edit-pos2"), EDIT_START("edit-start"), CHECKPOINT_ADD("checkpoint-add"),
    CHECKPOINT_SET("checkpoint-set"), CHECKPOINT_REMOVE("checkpoint-remove"), CHECKPOINT_MOVE("checkpoint-move"),
    SLOT_ADD("slot-add"), SLOT_REMOVE("slot-remove"), PREVIEW_TOGGLE("preview-toggle"), EDIT_SAVE("edit-save"),
    EDIT_CANCEL("edit-cancel"), BACK("back"), CLOSE("close"), CONFIRM("confirm"), CANCEL_CONFIRM("cancel-confirm");

    private final String id;

    GuiAction(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static GuiAction parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("action 不能为空");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('.', '-').replace('_', '-');
        for (GuiAction action : values()) {
            if (action.id.equals(normalized)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未注册 action: " + raw);
    }

    public static GuiAction fromId(String raw) {
        return parse(raw);
    }
}

