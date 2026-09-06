package ca.inspection.home.inspection.service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Thumbnails {

    private Thumbnails() {
    }

    public static Path getOrCreate(Path source, Path thumbPath, int maxWidth) throws IOException {
        if (Files.exists(thumbPath)) return thumbPath;

        Files.createDirectories(thumbPath.getParent());

        BufferedImage full = ImageIO.read(source.toFile());
        if (full == null) throw new IOException("Unreadable image: " + source.getFileName());

        BufferedImage thumb = scaleToWidth(full, maxWidth);

        Path tmp = Files.createTempFile(thumbPath.getParent(), "thumb_", ".jpg");
        try {
            ImageIO.write(thumb, "jpeg", tmp.toFile());
            Files.move(tmp, thumbPath);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return thumbPath;
    }

    public static BufferedImage scaleToWidth(BufferedImage source, int maxWidth) {
        if (source == null) return null;
        if (source.getWidth() <= maxWidth) return toOpaque(source);

        int height = Math.max(1, (int) Math.round(
                source.getHeight() * ((double) maxWidth / source.getWidth())));

        return redraw(source, maxWidth, height);
    }

    public static BufferedImage toOpaque(BufferedImage source) {
        if (source == null || !source.getColorModel().hasAlpha()) return source;
        return redraw(source, source.getWidth(), source.getHeight());
    }

    private static BufferedImage redraw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = target.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        // TYPE_INT_RGB starts out black, so transparent pixels would print as black boxes.
        graphics2D.setColor(Color.WHITE);
        graphics2D.fillRect(0, 0, width, height);
        graphics2D.drawImage(source, 0, 0, width, height, null);
        graphics2D.dispose();

        return target;
    }
}
