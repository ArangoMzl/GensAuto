package dev.gensauto;

import dev.gensauto.data.ToolText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolTextTest {
    @Test void keyIgnoresAccentsAndCase() {
        assertEquals("PICO DE MINERIA", ToolText.key("Pico de Minería"));
        assertEquals("PICO DE MINERIA", ToolText.key("PICO DE MINERÍA"));
        assertEquals("AZADA COSECHADORA", ToolText.key(" Azada  Cosechadora "));
    }

    @Test void readsPickaxeLore() {
        var lore = ToolText.parseLore(List.of("Herramienta Especial", "", "Mina bloques y gana tokens",
                "| ★ Dueño: ArangoMzl", "| ⊠ Nivel: 31 (||||||||||||) (74.22%)", "| ♨ Prestigio: 5 ✦",
                "| ⬢ Renacimiento: 2 ✦", "", "| ENCANTAMIENTOS", "▪ Prisa Minera Máxeado"));
        assertNotNull(lore);
        assertEquals(31, lore.level());
        assertEquals(74.22, lore.percent(), 1e-9);
        assertEquals(5, lore.prestige());
        assertEquals(2, lore.rebirth());
    }

    @Test void ignoresNormalItems() {
        assertNull(ToolText.parseLore(List.of("Un bloque cualquiera")));
    }

    @Test void readsActionBars() {
        var hoe = ToolText.parseActionBar("AZADA COSECHADORA | ★ Nivel 71 (22.53%) | ❂ Esencias Farm. (+72.1M/m)");
        assertNotNull(hoe);
        assertEquals("AZADA COSECHADORA", hoe.tool());
        assertEquals(71, hoe.level());
        assertEquals(22.53, hoe.percent(), 1e-9);
        assertEquals("Esencias Farm.", hoe.rateLabel());
        assertEquals("+72.1M/m", hoe.rate());

        var pick = ToolText.parseActionBar("PICO DE MINERÍA | ★ Nivel 27 (33.01%) | ♨ Tokens Farm. (+0/m)");
        assertNotNull(pick);
        assertEquals(27, pick.level());
        assertEquals("+0/m", pick.rate());

        assertNull(ToolText.parseActionBar("Has ganado un coinflip de 69T de Dinero!"));
    }

    @Test void readsActionBarsWithOtherSeparatorsAndIcons() {
        var bar = ToolText.parseActionBar(" AZADA COSECHADORA ┃  Nivel 73 (3.61%) ┃  Esencias Farm. (+1.6B/m)");
        assertNotNull(bar);
        assertEquals("AZADA COSECHADORA", ToolText.key(bar.tool()));
        assertEquals(73, bar.level());
        assertEquals(3.61, bar.percent(), 1e-9);
        assertEquals("Esencias Farm.", bar.rateLabel());
        assertEquals("+1.6B/m", bar.rate());

        var noRate = ToolText.parseActionBar("PICO DE MINERÍA | Nivel 27 (33.01%)");
        assertNotNull(noRate);
        assertNull(noRate.rate());
    }

    @Test void readsPrestigeMenuItem() {
        var req = ToolText.parsePrestigeItem("Prestigiar | Azada Cosechadora (Prestigio 4)", List.of(
                "Al prestigiar tu herramienta vuelve al", "| Requisitos para prestigiar:", "▪ Nivel de Herramienta: 125"));
        assertNotNull(req);
        assertEquals("AZADA COSECHADORA", ToolText.key(req.tool()));
        assertEquals(4, req.prestige());
        assertEquals(125, req.level());
    }

    @Test void formatsDurations() {
        assertEquals("30s", ToolText.duration(30));
        assertEquals("45m", ToolText.duration(45 * 60));
        assertEquals("3h 12m", ToolText.duration(3 * 3600 + 12 * 60));
    }
}
