// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.mapmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.WindowFocusListener;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.mapmode.ParallelWayAction.Mode;
import org.openstreetmap.josm.actions.mapmode.ParallelWayAction.Modifier;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.MapModeUtils;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.Logging;

/**
 * Unit tests for class {@link ParallelWayAction}.
 */
@Main
@Projection
class ParallelWayActionTest {
    private MapFrame map;
    private ParallelWayAction mapMode;
    private DataSet dataSet;

    @BeforeEach
    void setup() {
        OsmDataLayer layer = new OsmDataLayer(new DataSet(), "ParallelWayActionTest", null);
        MainApplication.getLayerManager().addLayer(layer);
        this.map = MainApplication.getMap();
        this.mapMode = new ParallelWayAction(this.map);
        this.dataSet = layer.getDataSet();
    }

    /**
     * Unit test of {@link ParallelWayAction#enterMode} and {@link ParallelWayAction#exitMode}.
     */
    @Test
    void testMode() {
        final MapMode oldMapMode = this.map.mapMode;
        assertTrue(this.map.selectMapMode(mapMode));
        assertEquals(this.mapMode, this.map.mapMode);
        assertTrue(this.map.selectMapMode(oldMapMode));
    }

    /**
     * Non-regression test for <a href="https://josm.openstreetmap.de/ticket/20908">#20908</a>
     */
    @Test
    void testNonRegression20908() {
        Logging.clearLastErrorAndWarnings();
        this.map.selectMapMode(this.map.mapModeDraw);
        MapModeUtils.clickAt(LatLon.ZERO);
        MapModeUtils.clickAt(2, new LatLon(0.0001, 0));
        assertEquals(3, this.dataSet.allPrimitives().size());
        this.map.selectMapMode(mapMode);
        MapModeUtils.dragFromTo(new LatLon(0.00005, 0), new LatLon(0.00005, 0.0001));
        assertEquals(6, this.dataSet.allPrimitives().size());
        UndoRedoHandler.getInstance().undo();
        assertEquals(3, this.dataSet.allPrimitives().size());
        this.map.mapMode.mousePressed(MapModeUtils.mouseClickAt(new LatLon(0.00005, 0.0001)));
        assertTrue(Logging.getLastErrorAndWarnings().isEmpty(), String.join("\n", Logging.getLastErrorAndWarnings()));
    }

    /**
     * Several selected ways connected by common nodes are offset together, one result way per source way.
     */
    @Test
    void testMultipleSelectedWays() {
        Node a = new Node(LatLon.ZERO);
        Node b = new Node(new LatLon(0, 0.0001));
        Node c = new Node(new LatLon(0, 0.0002));
        Way w1 = new Way();
        w1.addNode(a);
        w1.addNode(b);
        Way w2 = new Way();
        w2.addNode(b);
        w2.addNode(c);
        for (Node n : new Node[] {a, b, c}) {
            this.dataSet.addPrimitive(n);
        }
        this.dataSet.addPrimitive(w1);
        this.dataSet.addPrimitive(w2);
        this.dataSet.setSelected(w1, w2);
        this.map.selectMapMode(mapMode);
        MapModeUtils.dragFromTo(new LatLon(0, 0.00005), new LatLon(0.00005, 0.00005));
        assertEquals(2, this.dataSet.getWays().size() - 2, "two parallel ways expected: " + this.dataSet.getWays());
        assertEquals(3 + 3 + 2 + 2, this.dataSet.allPrimitives().size());
    }

    /**
     * The selection changed while in the mode (e.g. by Selection > Non-branching way sequences, after a first
     * parallel way has been created) is used as source.
     */
    @Test
    void testSelectionChangedInMode() {
        Node a = new Node(LatLon.ZERO);
        Node b = new Node(new LatLon(0, 0.0001));
        Node c = new Node(new LatLon(0, 0.0002));
        Way w1 = new Way();
        w1.addNode(a);
        w1.addNode(b);
        Way w2 = new Way();
        w2.addNode(b);
        w2.addNode(c);
        for (Node n : new Node[] {a, b, c}) {
            this.dataSet.addPrimitive(n);
        }
        this.dataSet.addPrimitive(w1);
        this.dataSet.addPrimitive(w2);
        this.dataSet.setSelected(w1);
        this.map.selectMapMode(mapMode);
        // first parallel of the single selected way
        MapModeUtils.dragFromTo(new LatLon(0, 0.00005), new LatLon(0.00005, 0.00005));
        assertEquals(3, this.dataSet.getWays().size());
        // now the selection is extended by other means, the next drag must use both ways
        this.dataSet.setSelected(w1, w2);
        MapModeUtils.dragFromTo(new LatLon(0, 0.00005), new LatLon(-0.00005, 0.00005));
        assertEquals(5, this.dataSet.getWays().size(), this.dataSet.getWays().toString());
    }

    /**
     * A drag whose mouse release was lost is dropped by the next press, which then starts a new drag.
     * <p>
     * Non-regression test for <a href="https://josm.openstreetmap.de/ticket/25012">#25012</a>: the press cleared the
     * offset path but left the mode at dragging, so the next drag event threw a {@link NullPointerException}.
     */
    @Test
    void testNonRegression25012() {
        Node a = new Node(LatLon.ZERO);
        Node b = new Node(new LatLon(0, 0.0001));
        Way w = new Way();
        w.addNode(a);
        w.addNode(b);
        this.dataSet.addPrimitive(a);
        this.dataSet.addPrimitive(b);
        this.dataSet.addPrimitive(w);
        this.map.selectMapMode(mapMode);

        // a drag whose release never arrives, e.g. because the focus moved to another application
        mapMode.mousePressed(MapModeUtils.mouseClickAt(new LatLon(0, 0.00005)));
        Awaitility.await().pollDelay(Durations.FIVE_HUNDRED_MILLISECONDS).until(() -> true);
        mapMode.mouseDragged(MapModeUtils.mouseClickAt(new LatLon(0, 0.00005)));
        mapMode.mouseDragged(MapModeUtils.mouseClickAt(new LatLon(0.00005, 0.00005)));
        assertEquals(3, this.dataSet.allPrimitives().size(), "nothing is created before the release");

        MapModeUtils.dragFromTo(new LatLon(0, 0.00005), new LatLon(-0.00005, 0.00005));
        assertEquals(2, this.dataSet.getWays().size(), this.dataSet.getWays().toString());
        assertEquals(6, this.dataSet.allPrimitives().size());
    }

    /**
     * Losing the focus cancels the drag in progress, and the late release does not create or select anything.
     * <p>
     * Non-regression test for <a href="https://josm.openstreetmap.de/ticket/25012">#25012</a>: the preview stayed on
     * screen until the next click.
     * @throws ReflectiveOperationException if the private fields cannot be accessed
     */
    @Test
    void testFocusLostCancelsDrag25012() throws ReflectiveOperationException {
        Node a = new Node(LatLon.ZERO);
        Node b = new Node(new LatLon(0, 0.0001));
        Way w = new Way();
        w.addNode(a);
        w.addNode(b);
        this.dataSet.addPrimitive(a);
        this.dataSet.addPrimitive(b);
        this.dataSet.addPrimitive(w);
        this.map.selectMapMode(mapMode);

        mapMode.mousePressed(MapModeUtils.mouseClickAt(new LatLon(0, 0.00005)));
        Awaitility.await().pollDelay(Durations.FIVE_HUNDRED_MILLISECONDS).until(() -> true);
        mapMode.mouseDragged(MapModeUtils.mouseClickAt(new LatLon(0, 0.00005)));
        mapMode.mouseDragged(MapModeUtils.mouseClickAt(new LatLon(0.00005, 0.00005)));
        assertEquals(Mode.DRAGGING, TestUtils.getPrivateField(mapMode, "mode"));

        ((WindowFocusListener) TestUtils.getPrivateField(mapMode, "focusListener")).windowLostFocus(null);
        assertEquals(Mode.NORMAL, TestUtils.getPrivateField(mapMode, "mode"), "the preview is still drawn");

        // the release arrives later, e.g. on a platform where it goes to the window of the press anyway, here back
        // on the way, where a click would select it
        this.dataSet.clearSelection();
        mapMode.mouseReleased(MapModeUtils.mouseClickAt(new LatLon(0, 0.00005)));
        assertEquals(3, this.dataSet.allPrimitives().size(), "the cancelled drag has been committed");
        assertTrue(this.dataSet.getSelected().isEmpty(), "the release has been taken as a click");

        MapModeUtils.dragFromTo(new LatLon(0, 0.00005), new LatLon(-0.00005, 0.00005));
        assertEquals(6, this.dataSet.allPrimitives().size());
    }

    /**
     * Unit test of {@link Mode} enum.
     */
    @Test
    void testEnumMode() {
        TestUtils.superficialEnumCodeCoverage(Mode.class);
    }

    /**
     * Unit test of {@link Modifier} enum.
     */
    @Test
    void testEnumModifier() {
        TestUtils.superficialEnumCodeCoverage(Modifier.class);
    }
}
