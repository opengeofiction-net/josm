// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MapGridPaintable;
import org.openstreetmap.josm.gui.layer.MapGridPaintable.GridType;

/**
 * Moves the origin of the grid drawn over the map to a given position (e.g. the position of a mouse click), so that
 * grid lines pass through it. Enables the grid.
 * @see MapGridPaintable
 * @since xxx
 */
public class SetGridOriginAction extends AbstractAction {

    private final EastNorth position;

    /**
     * Constructs a new {@code SetGridOriginAction}.
     * @param position the new grid origin, in projected coordinates
     */
    public SetGridOriginAction(EastNorth position) {
        super(tr("Set grid origin here"));
        putValue(SHORT_DESCRIPTION, tr("Move the grid so that grid lines pass through this point, and show the grid."));
        this.position = position;
        setEnabled(position != null && position.isValid());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        setOrigin(position);
        if (MainApplication.isDisplayingMapView()) {
            MainApplication.getMap().mapView.repaint();
        }
    }

    /**
     * Sets the grid origin (in the coordinates of the current grid type) and enables the grid.
     * @param position the new grid origin, in projected coordinates
     */
    public static void setOrigin(EastNorth position) {
        if (MapGridPaintable.TYPE.get() == GridType.PROJECTED) {
            MapGridPaintable.ORIGIN_X.put(position.east());
            MapGridPaintable.ORIGIN_Y.put(position.north());
        } else {
            LatLon ll = ProjectionRegistry.getProjection().eastNorth2latlon(position);
            MapGridPaintable.ORIGIN_X.put(ll.lon());
            MapGridPaintable.ORIGIN_Y.put(ll.lat());
        }
        MapGridPaintable.ENABLED.put(true);
    }
}
