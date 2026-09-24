package ca.inspection.home.inspection.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

public final class Thumbnails {

    private Thumbnails() {
    }

    public static Path getOrCreate(Path source, Path thumbPath, int maxWidth) throws IOException {
        if (Files.exists(thumbPath)) return thumbPath;

        Files.createDirectories(thumbPath.getParent());

        BufferedImage thumb = readScaledToWidth(source, maxWidth);

        Path tmp = Files.createTempFile(thumbPath.getParent(), "thumb_", ".jpg");
        try {
            ImageIO.write(thumb, "jpeg", tmp.toFile());
            Files.move(tmp, thumbPath);
        } catch (FileAlreadyExistsException e) {
            // Another request built the same file first; theirs serves just as well.
            Files.deleteIfExists(tmp);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        return thumbPath;
    }

    // Subsampled decode, keeping twice the target width so the final resize still has
    // pixels to blend. Saves little decode time but most of the memory of a phone photo.
    public static BufferedImage readScaledToWidth(Path source, int maxWidth) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IOException("Unreadable image: " + source.getFileName());
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, reader.getWidth(0) / (maxWidth * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                return scaleToWidth(reader.read(0, param), maxWidth);
            } finally {
                reader.dispose();
            }
        }
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
