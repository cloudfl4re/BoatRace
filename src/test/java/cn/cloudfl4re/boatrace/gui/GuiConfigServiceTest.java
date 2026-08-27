package cn.cloudfl4re.boatrace.gui;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class GuiConfigServiceTest {
    @Test
    void parsesValidMenuAndPane() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader("""
            title: 冰船系统
            size: 27
            permission: boatrace.gui
            menu:
              '13':
                index: 13
                name: '<aqua>排行榜</aqua>'
                material: GOLD_INGOT
                lore:
                  - '<gray>查看排名</gray>'
                action: race-leaderboard
                permission: boatrace.gui.race
            pane:
              enable: true
              index: [0, 1, 2]
              name: §e✧ 闪闪发光的边框 ✧
              material: WHITE_STAINED_GLASS_PANE
              lore:
                - §7嘿嘿嘿～
                - §7戳我干嘛呀小坏蛋～
              isEnchant: false
              custom-model-data: 0
              hideFlag: true
              hideEnchant: true
            """));

        GuiMenuConfig config = GuiMenuConfig.parse("main", yaml);

        assertEquals("冰船系统", config.title());
        assertEquals(27, config.size());
        assertEquals(Material.GOLD_INGOT, config.buttons().get("13").material());
        assertEquals(GuiAction.RACE_LEADERBOARD, config.buttons().get("13").action());
        assertEquals("§e✧ 闪闪发光的边框 ✧", config.pane().name());
        assertEquals(Material.WHITE_STAINED_GLASS_PANE, config.pane().material());
    }

    @Test
    void rejectsInvalidSizeAndSlot() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader("""
            title: Broken
            size: 10
            menu:
              bad:
                index: 10
                name: Bad
                material: STONE
                action: close
            """));

        assertThrows(IllegalArgumentException.class, () -> GuiMenuConfig.parse("broken", yaml));
    }

    @Test
    void rejectsUnknownAction() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader("""
            title: Broken
            size: 27
            menu:
              bad:
                index: 10
                name: Bad
                material: STONE
                action: run-arbitrary-command
            """));

        assertThrows(IllegalArgumentException.class, () -> GuiMenuConfig.parse("broken", yaml));
    }
}


