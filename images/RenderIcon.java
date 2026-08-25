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
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Produces every launcher/store bitmap from the original chargy shield.
 *
 * Run from the repository root:
 *
 *   java images/RenderIcon.java
 *
 * Adaptive layers use a 108 dp canvas and keep the complete logo inside the
 * guaranteed 66 dp safe zone. Store and legacy assets use the 75% keyline.
 */
public class RenderIcon {

    private static final Path ROOT   = Path.of("").toAbsolutePath();
    private static final Path SOURCE = ROOT.resolve("images/chargy-qrcode-shield.png");

    private static final Color BACKGROUND = Color.WHITE;

    private record Density(String directory, int legacySize, int adaptiveSize) { }

    private static final Density[] DENSITIES = {
        new Density("mipmap-mdpi",     48, 108),
        new Density("mipmap-hdpi",     72, 162),
        new Density("mipmap-xhdpi",    96, 216),
        new Density("mipmap-xxhdpi",  144, 324),
        new Density("mipmap-xxxhdpi", 192, 432)
    };

    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(SOURCE.toFile());
        if (source == null)
            throw new IOException("Unsupported source image: " + SOURCE);

        Bounds visible = alphaBounds(source);

        write(ROOT.resolve("images/play-store-icon-512.png"),
              renderColor(source, visible, 512, 0.75, Background.SQUARE));
        write(ROOT.resolve("images/icon-1024.png"),
              renderColor(source, visible, 1024, 0.75, Background.SQUARE));

        Path resources = ROOT.resolve("app/src/main/res");
        for (Density density : DENSITIES) {
            Path directory = resources.resolve(density.directory());

            BufferedImage foreground = renderColor(source, visible,
                                                   density.adaptiveSize(), 66.0 / 108.0,
                                                   Background.TRANSPARENT);

            write(directory.resolve("ic_launcher_foreground.png"), foreground);
            write(directory.resolve("ic_launcher_monochrome.png"), monochrome(foreground));

            write(directory.resolve("ic_launcher.png"),
                  renderColor(source, visible, density.legacySize(), 0.75,
                              Background.SQUARE));
            write(directory.resolve("ic_launcher_round.png"),
                  renderColor(source, visible, density.legacySize(), 0.75,
                              Background.CIRCLE));
        }

        System.out.printf("Source alpha bounds: %d,%d .. %d,%d%n",
                          visible.left(), visible.top(), visible.right(), visible.bottom());
        System.out.println("Launcher and store icon assets regenerated.");
    }

    private enum Background { TRANSPARENT, SQUARE, CIRCLE }

    private record Bounds(int left, int top, int right, int bottom) {
        int width()  { return right  - left + 1; }
        int height() { return bottom - top  + 1; }
    }

    private static Bounds alphaBounds(BufferedImage source) {
        int left = source.getWidth();
        int top = source.getHeight();
        int right = -1;
        int bottom = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }

        if (right < left || bottom < top)
            throw new IllegalArgumentException("Source image is fully transparent");

        return new Bounds(left, top, right, bottom);
    }

    private static BufferedImage renderColor(BufferedImage source,
                                             Bounds visible,
                                             int size,
                                             double visibleHeightFraction,
                                             Background background) {
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();

        g.setComposite(AlphaComposite.Src);
        if (background == Background.SQUARE) {
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, size, size);
        }
        else if (background == Background.CIRCLE) {
            g.setColor(BACKGROUND);
            g.fillOval(0, 0, size, size);
        }

        // Transparent pixels around the shield must reveal the icon
        // background instead of replacing it with transparency.
        g.setComposite(AlphaComposite.SrcOver);

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                           RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        int height = (int) Math.round(size * visibleHeightFraction);
        int width  = (int) Math.round(height * visible.width() / (double) visible.height());
        int left   = (size - width) / 2;
        int top    = (size - height) / 2;

        g.drawImage(source,
                    left, top, left + width, top + height,
                    visible.left(), visible.top(), visible.right() + 1, visible.bottom() + 1,
                    null);
        g.dispose();
        return output;
    }

    /**
     * Android applies the user's theme colour to the alpha mask. Keep the
     * shield and white QR panel opaque and turn the black QR modules into
     * negative space so the brand remains recognisable in one colour.
     */
    private static BufferedImage monochrome(BufferedImage color) {
        BufferedImage output = new BufferedImage(color.getWidth(), color.getHeight(),
                                                 BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < color.getHeight(); y++) {
            for (int x = 0; x < color.getWidth(); x++) {
                int argb = color.getRGB(x, y);
                int alpha = argb >>> 24;
                int red   = (argb >>> 16) & 0xff;
                int green = (argb >>>  8) & 0xff;
                int blue  =  argb         & 0xff;
                int strongestChannel = Math.max(red, Math.max(green, blue));

                // Smooth only the transition around black QR modules. The
                // blue shield starts well above this range and stays opaque.
                double blackMask = Math.max(0.0, Math.min(1.0,
                                                          (strongestChannel - 32) / 64.0));
                int maskAlpha = (int) Math.round(alpha * blackMask);
                output.setRGB(x, y, (maskAlpha << 24) | 0x00ffffff);
            }
        }

        return output;
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
        System.out.printf("Written: %s  %dx%d%n", ROOT.relativize(path),
                          image.getWidth(), image.getHeight());
    }
}
