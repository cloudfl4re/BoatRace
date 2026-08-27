package cn.cloudfl4re.boatrace.gui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutEditorTest {
    @Test
    void movesButtonAndKeepsOtherSlots() {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        indexes.put("race", 20);
        indexes.put("status", 22);

        Map<String, Integer> moved = GuiLayoutEditor.move(indexes, "race", 30, 54);

        assertEquals(30, moved.get("race"));
        assertEquals(22, moved.get("status"));
    }

    @Test
    void rejectsOutOfRangeSlot() {
        Map<String, Integer> indexes = Map.of("race", 20);
        assertThrows(IllegalArgumentException.class, () -> GuiLayoutEditor.move(indexes, "race", 54, 54));
    }
}
