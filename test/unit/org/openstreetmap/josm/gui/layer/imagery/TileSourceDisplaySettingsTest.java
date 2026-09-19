// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

/**
 * Unit tests of {@link TileSourceDisplaySettings}.
 */
@BasicPreferences
class TileSourceDisplaySettingsTest {

    /**
     * The tile borders setting fires a change event and survives a round trip through the session properties.
     */
    @Test
    void testShowTileBorders() {
        TileSourceDisplaySettings settings = new TileSourceDisplaySettings();
        assertFalse(settings.isShowTileBorders());
        List<String> changes = new ArrayList<>();
        settings.addSettingsChangeListener(e -> changes.add(e.getChangedSetting()));
        settings.setShowTileBorders(true);
        assertTrue(settings.isShowTileBorders());
        assertEquals(1, changes.size());

        Map<String, String> data = settings.toPropertiesMap();
        assertEquals("true", data.get("show-tile-borders"));
        TileSourceDisplaySettings copy = new TileSourceDisplaySettings();
        assertNotEquals(settings, copy);
        copy.applyFromPropertiesMap(data);
        assertTrue(copy.isShowTileBorders());
        assertEquals(settings, copy);
        assertEquals(settings.hashCode(), copy.hashCode());

        // a session without the setting keeps the current value
        data.remove("show-tile-borders");
        copy.applyFromPropertiesMap(data);
        assertTrue(copy.isShowTileBorders());
    }
}
