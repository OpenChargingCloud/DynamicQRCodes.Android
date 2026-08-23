/*
 * Copyright (c) 2024-2026 GraphDefined GmbH <achim.friedland@graphdefined.com>
 * This file is part of DynamicQRCodes.Android <https://github.com/OpenChargingCloud/DynamicQRCodes.Android>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the launcher icon to a PNG for the Google Play listing, which takes
 * a 512x512 bitmap of its own rather than reading the app bundle.
 *
 * The shapes are the ones in app/src/main/res/drawable/ic_launcher_*.xml, on
 * that file's 108 unit canvas. Those drawables are the source of truth; this
 * only rasterises them, so a change there has to be repeated here.
 *
 *   java RenderIcon.java play-store-icon-512.png 512
 */
public class RenderIcon {

    private static final Color BACKGROUND = new Color(0x45, 0x27, 0xA0);
    private static final Color FOREGROUND = Color.WHITE;
    private static final double CANVAS     = 108.0;

    public static void main(String[] args) throws Exception {

        String out  = args.length > 0 ? args[0] : "play-store-icon-512.png";
        int    size = args.length > 1 ? Integer.parseInt(args[1]) : 512;

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D    g     = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,   RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,        RenderingHints.VALUE_RENDER_QUALITY);

        // The Play listing icon is the full square; Google rounds the corners
        // itself, exactly as a launcher masks the adaptive icon.
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, size, size);

        double s = size / CANVAS;

        finderPattern(g, s, 24, 24);
        finderPattern(g, s, 66, 24);
        finderPattern(g, s, 24, 66);

        g.setColor(FOREGROUND);
        g.fill(bolt(s));

        g.dispose();

        ImageIO.write(image, "png", new File(out));

        System.out.println("Written: " + out + "  " + size + "x" + size);

    }

    /** The 7x7 module square a QR code carries in three of its corners. */
    private static void finderPattern(Graphics2D g, double s, double x, double y) {
        g.setColor(FOREGROUND);
        fill(g, s, x,     y,     18);
        g.setColor(BACKGROUND);
        fill(g, s, x + 3, y + 3, 12);
        g.setColor(FOREGROUND);
        fill(g, s, x + 6, y + 6,  6);
    }

    private static void fill(Graphics2D g, double s, double x, double y, double size) {
        g.fill(new java.awt.geom.Rectangle2D.Double(x * s, y * s, size * s, size * s));
    }

    private static Path2D.Double bolt(double s) {
        double[][] points = { {74, 56}, {60, 73}, {70, 73}, {67, 84}, {82, 67}, {72, 67} };
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0][0] * s, points[0][1] * s);
        for (int i = 1; i < points.length; i++)
            path.lineTo(points[i][0] * s, points[i][1] * s);
        path.closePath();
        return path;
    }

}
