import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.CubicCurve2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ConnectorLayer
 *
 * A transparent panel that floats above the JDesktopPane and draws animated
 * Bézier cables between JInternalFrames — like node-editor wiring in Blender.
 *
 * HOW IT WORKS
 * ─────────────
 *  1.  PlayerUI wraps its existing desktop in a JLayeredPane.
 *  2.  ConnectorLayer is added on top (PALETTE_LAYER).
 *  3.  For every "connection" you register (source frame → target frame),
 *      the layer queries each frame's current screen-relative position and
 *      size, picks the nearest edge midpoints as ports, then draws a cubic
 *      Bézier between them.
 *  4.  A javax.swing.Timer repaints at ~30 fps so cables follow frames as
 *      they are dragged around.
 *  5.  Port dots glow with the connection colour so the wiring looks intentional.
 *
 * USAGE (in PlayerUI.setUi, after addItemsToContainers):
 *
 *   ConnectorLayer cl = ConnectorLayer.install(desktop);
 *   cl.connect(playerFrame,  playlistFrame,   ConnectorLayer.COLOR_AUDIO);
 *   cl.connect(playerFrame,  addSongsFrame,   ConnectorLayer.COLOR_DATA);
 *   cl.connect(playerFrame,  consoleFrame,    ConnectorLayer.COLOR_LOG);
 *   // call cl.connect() for every frame pair you want wired.
 */
public class ConnectorLayer extends JPanel {

    private Color cableColor = new Color(100, 200, 255, 200);

    public void setCableColor(Color color) {
        this.cableColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 200);
    }

    private static final int   CABLE_WIDTH  = 2;
    private static final int   PORT_RADIUS  = 5;
    private static final float CTRL_PULL    = 0.45f;   // how far control points reach (0–1 of distance)
    private static final int   FPS          = 30;

    // ── Internal record of one cable ─────────────────────────────────────────
    public static class Connection {
        public final JInternalFrame from;
        public final JInternalFrame to;

        Connection(JInternalFrame from, JInternalFrame to) {
            this.from = from;
            this.to   = to;
        }
    }

    private final List<Connection> connections = new ArrayList<>();
    private final JDesktopPane     desktop;
    private final Timer            repaintTimer;

    // ── Construction ─────────────────────────────────────────────────────────

    private ConnectorLayer(JDesktopPane desktop) {
        this.desktop = desktop;
        setOpaque(false);
        setLayout(null);

        // Repaint loop — cheap since we only do vector drawing
        repaintTimer = new Timer(1000 / FPS, e -> repaint());
        repaintTimer.start();
    }

    /**
     * Installs a ConnectorLayer over the given desktop and returns it.
     *
     * The desktop is reparented into a JLayeredPane so the overlay can sit
     * on top without interfering with frame dragging.  Call this once, then
     * call {@link #connect} for each cable you want.
     *
     * @param  desktop  the existing JDesktopPane from PlayerUI
     * @return the installed ConnectorLayer (keep a reference to add connections)
     */
    public static ConnectorLayer install(JDesktopPane desktop) {
        // The desktop's current parent is the mainContainer (a JPanel with BorderLayout).
        Container parent = desktop.getParent();

        // Build a layered pane to hold desktop + overlay
        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(new OverlayLayout(layered));   // children fill the whole area

        // Remove desktop from its current parent and put it in the layered pane
        parent.remove(desktop);
        ConnectorLayer overlay = new ConnectorLayer(desktop) {
            @Override public boolean contains(int x, int y) { return false; }
        };
        layered.add(desktop,          JLayeredPane.DEFAULT_LAYER);
        layered.add(overlay,          JLayeredPane.PALETTE_LAYER);

        // Make the overlay track the layered pane's size
        layered.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                overlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                desktop.setBounds(0, 0, layered.getWidth(), layered.getHeight());
            }
        });

        // Put the layered pane back where the desktop was
        if (parent instanceof JPanel jp) {
            jp.add(layered, BorderLayout.CENTER);
            jp.revalidate();
        }

        // Let mouse events fall through to desktop so dragging still works
        overlay.setFocusable(false);

        return overlay;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void connect(JInternalFrame from, JInternalFrame to) {
        connections.add(new Connection(from, to));
    }

    /** Remove all cables connected to a specific frame (e.g. when it closes). */
    public void disconnect(JInternalFrame frame) {
        connections.removeIf(c -> c.from == frame || c.to == frame);
    }

    /** Remove all cables. */
    public void disconnectAll() {
        connections.clear();
    }

    /** Returns an unmodifiable view of all current connections (for console listing). */
    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    /**
     * Removes all cables that run between frameA and frameB (in either direction).
     * @return number of cables removed
     */
    public int disconnectPair(JInternalFrame a, JInternalFrame b) {
        int before = connections.size();
        connections.removeIf(c ->
                (c.from == a && c.to == b) || (c.from == b && c.to == a));
        return before - connections.size();
    }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        for (Connection c : connections) {
            if (!c.from.isVisible() || !c.to.isVisible()) continue;
            if (c.from.isIcon() || c.to.isIcon()) continue;

            Point[] ports = nearestEdgePorts(c.from, c.to);
            if (ports == null) continue;

            Point p0 = ports[0];
            Point p3 = ports[1];

            int dx = (int) ((p3.x - p0.x) * CTRL_PULL);
            int dy = (int) ((p3.y - p0.y) * CTRL_PULL);

            Point p1 = controlPoint(p0, ports[2], dx, dy);
            Point p2 = controlPoint(p3, ports[3], -dx, -dy);

            for (int glow = 4; glow >= 0; glow--) {
                float alpha = (glow == 0) ? 0.9f : 0.06f * (5 - glow);
                int   width = CABLE_WIDTH + glow * 2;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2.setColor(cableColor);
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new CubicCurve2D.Float(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y));
            }

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            drawPort(g2, p0, cableColor);
            drawPort(g2, p3, cableColor);
        }

        g2.dispose();
    }

    // ── Geometry helpers ─────────────────────────────────────────────────────

    /**
     * Returns [fromPort, toPort, fromEdgeDir, toEdgeDir] where edge dir is
     * a Point encoding the outward-facing unit vector of the chosen edge:
     *   right=(1,0), left=(-1,0), bottom=(0,1), top=(0,-1)
     *
     * Picks whichever pair of edge-midpoints is closest, biasing toward
     * left/right exits so cables flow horizontally (looks nicer).
     */
    private Point[] nearestEdgePorts(JInternalFrame a, JInternalFrame b) {
        Rectangle ra = boundsOnDesktop(a);
        Rectangle rb = boundsOnDesktop(b);
        if (ra == null || rb == null) return null;

        // Candidate ports: right, left, bottom-centre, top-centre of each frame
        Point[] aPorts = edgeMidpoints(ra);
        Point[] bPorts = edgeMidpoints(rb);
        // Corresponding outward direction for each port index (right,left,bottom,top)
        Point[] dirs   = { new Point(1,0), new Point(-1,0), new Point(0,1), new Point(0,-1) };

        double best = Double.MAX_VALUE;
        Point fromPt = null, toPt = null, fromDir = null, toDir = null;

        for (int i = 0; i < aPorts.length; i++) {
            for (int j = 0; j < bPorts.length; j++) {
                double d = aPorts[i].distance(bPorts[j]);
                if (d < best) {
                    best    = d;
                    fromPt  = aPorts[i];
                    toPt    = bPorts[j];
                    fromDir = dirs[i];
                    toDir   = dirs[j];
                }
            }
        }

        return new Point[]{ fromPt, toPt, fromDir, toDir };
    }

    /** right, left, bottom-centre, top-centre midpoints of a rectangle */
    private static Point[] edgeMidpoints(Rectangle r) {
        return new Point[]{
                new Point(r.x + r.width,       r.y + r.height / 2),   // right
                new Point(r.x,                 r.y + r.height / 2),   // left
                new Point(r.x + r.width  / 2,  r.y + r.height),       // bottom
                new Point(r.x + r.width  / 2,  r.y),                  // top
        };
    }

    /**
     * Given a port point and the edge direction it sits on, build a Bézier
     * control point that pulls the curve outward from the port before bending.
     */
    private static Point controlPoint(Point port, Point edgeDir, int fallbackDx, int fallbackDy) {
        int pull = 80;
        if (edgeDir.x != 0) {
            return new Point(port.x + edgeDir.x * pull, port.y);
        } else if (edgeDir.y != 0) {
            return new Point(port.x, port.y + edgeDir.y * pull);
        }
        // fallback
        return new Point(port.x + fallbackDx, port.y + fallbackDy);
    }

    /**
     * Returns the frame's bounds in desktop-relative coordinates.
     * JInternalFrame.getBounds() is already relative to the desktop.
     */
    private static Rectangle boundsOnDesktop(JInternalFrame f) {
        if (!f.isShowing()) return null;
        return f.getBounds();
    }

    private static void drawPort(Graphics2D g2, Point p, Color color) {
        // Outer dark ring
        g2.setColor(new Color(10, 10, 12, 200));
        g2.fillOval(p.x - PORT_RADIUS - 1, p.y - PORT_RADIUS - 1,
                (PORT_RADIUS + 1) * 2,  (PORT_RADIUS + 1) * 2);
        // Coloured fill
        g2.setColor(color);
        g2.fillOval(p.x - PORT_RADIUS, p.y - PORT_RADIUS,
                PORT_RADIUS * 2,  PORT_RADIUS * 2);
        // Bright centre dot
        g2.setColor(Color.WHITE);
        g2.fillOval(p.x - 2, p.y - 2, 4, 4);
    }
}