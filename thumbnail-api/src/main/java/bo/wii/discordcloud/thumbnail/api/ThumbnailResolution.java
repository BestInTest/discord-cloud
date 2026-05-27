package bo.wii.discordcloud.thumbnail.api;

public class ThumbnailResolution {
    
    public static final ThumbnailResolution HD_720P = new ThumbnailResolution(1280, 720);
    public static final ThumbnailResolution HD_1080P = new ThumbnailResolution(1920, 1080);
    public static final ThumbnailResolution SD_480P = new ThumbnailResolution(854, 480);
    public static final ThumbnailResolution SD_360P = new ThumbnailResolution(640, 360);
    
    private final int width;
    private final int height;
    
    public ThumbnailResolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be greater than 0");
        }
        this.width = width;
        this.height = height;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
    @Override
    public String toString() {
        return width + "x" + height;
    }
}

