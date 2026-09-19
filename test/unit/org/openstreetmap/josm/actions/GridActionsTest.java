// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MapGridPaintable;
import org.openstreetmap.josm.gui.layer.MapGridPaintable.GridType;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;

/**
 * Unit tests of {@link SetGridOriginAction} and {@link AlignGridRotationAction}.
 */
@Main
@Projection
class GridActionsTest {

    @AfterEach
    void reset() {
        for (String key : new String[] {"enabled", "type", "rotation", "origin-x", "origin-y"}) {
            Config.getPref().put("draw.grid." + key, null);
        }
    }

    /**
     * The origin is stored in the coordinates of the grid type, and the grid gets enabled.
     */
    @Test
    void testSetOrigin() {
        EastNorth en = ProjectionRegistry.getProjection().latlon2eastNorth(new LatLon(50, 10));
        MapGridPaintable.TYPE.put(GridType.LATLON);
        new SetGridOriginAction(en).actionPerformed(new ActionEvent(this, 0, ""));
        assertEquals(10, MapGridPaintable.ORIGIN_X.get(), 1e-7);
        assertEquals(50, MapGridPaintable.ORIGIN_Y.get(), 1e-7);
        assertTrue(MapGridPaintable.ENABLED.get());

        MapGridPaintable.TYPE.put(GridType.PROJECTED);
        new SetGridOriginAction(en).actionPerformed(new ActionEvent(this, 0, ""));
        assertEquals(en.east(), MapGridPaintable.ORIGIN_X.get(), 1e-6);
        assertEquals(en.north(), MapGridPaintable.ORIGIN_Y.get(), 1e-6);
        assertFalse(new SetGridOriginAction(null).isEnabled());
    }

    /**
     * The rotation is reduced to [0, 90) and is the same for all four directions of a square grid
     */
    @Test
    void testRotationOf() {
        EastNorth o = new EastNorth(0, 0);
        assertEquals(0, AlignGridRotationAction.rotationOf(o, new EastNorth(10, 0)), 1e-9);
        assertEquals(0, AlignGridRotationAction.rotationOf(o, new EastNorth(0, 10)), 1e-9);
        assertEquals(0, AlignGridRotationAction.rotationOf(o, new EastNorth(-10, 0)), 1e-9);
        assertEquals(45, AlignGridRotationAction.rotationOf(o, new EastNorth(10, 10)), 1e-9);
        assertEquals(45, AlignGridRotationAction.rotationOf(o, new EastNorth(-10, 10)), 1e-9);
        double c30 = Math.cos(Math.toRadians(30));
        double s30 = Math.sin(Math.toRadians(30));
        assertEquals(30, AlignGridRotationAction.rotationOf(o, new EastNorth(c30, s30)), 1e-9);
        assertEquals(30, AlignGridRotationAction.rotationOf(o, new EastNorth(-c30, -s30)), 1e-9);
        // a road at compass heading 115 degrees is a grid rotated by 65 degrees
        double heading = Math.toRadians(115);
        assertEquals(65, AlignGridRotationAction.rotationOf(o, new EastNorth(Math.sin(heading), Math.cos(heading))), 1e-9);
    }

    /**
     * The direction comes from a single selected way or two selected nodes; anything else disables the action.
     */
    @Test
    void testAlignToSelection() {
        DataSet ds = new DataSet();
        OsmDataLayer layer = new OsmDataLayer(ds, "GridActionsTest", null);
        MainApplication.getLayerManager().addLayer(layer);
        try {
            Node a = new Node(new LatLon(50, 10));
            Node b = new Node(new LatLon(50.01, 10.01));
            Node c = new Node(new LatLon(50.02, 10));
            Way w = new Way();
            w.addNode(a);
            w.addNode(c);
            w.addNode(b);
            ds.addPrimitive(a);
            ds.addPrimitive(b);
            ds.addPrimitive(c);
            ds.addPrimitive(w);

            ds.setSelected();
            assertNull(AlignGridRotationAction.getSelectedDirection(ds));
            assertFalse(new AlignGridRotationAction().isEnabled());
            ds.setSelected(a);
            assertNull(AlignGridRotationAction.getSelectedDirection(ds));
            ds.setSelected(a, b, c);
            assertNull(AlignGridRotationAction.getSelectedDirection(ds));

            ds.setSelected(a, b);
            AlignGridRotationAction action = new AlignGridRotationAction();
            assertTrue(action.isEnabled());
            MapGridPaintable.TYPE.put(GridType.LATLON);
            action.actionPerformed(new ActionEvent(this, 0, ""));
            double expected = AlignGridRotationAction.rotationOf(a.getEastNorth(), b.getEastNorth());
            assertEquals(expected, MapGridPaintable.ROTATION.get(), 1e-9);
            assertEquals(GridType.PROJECTED, MapGridPaintable.TYPE.get());
            assertTrue(MapGridPaintable.ENABLED.get());

            // the way: first to last node (a to b), not the first segment
            ds.setSelected(w);
            EastNorth[] dir = AlignGridRotationAction.getSelectedDirection(ds);
            assertEquals(a.getEastNorth(), dir[0]);
            assertEquals(b.getEastNorth(), dir[1]);
        } finally {
            MainApplication.getLayerManager().removeLayer(layer);
        }
    }
}
