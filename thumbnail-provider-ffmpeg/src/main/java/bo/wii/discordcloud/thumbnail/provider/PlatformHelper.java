package bo.wii.discordcloud.thumbnail.provider;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlatformHelper {

    /**
     * Returns name of the subdirectory inside {@code ffmpeg/} that matches the current platform,
     * e.g. {@code linux-x64}, {@code linux-arm64}, {@code windows-x64}.
     * Returns {@code null} for unsupported platforms, in which case PATH is used.
     */
    protected static String detectPlatformSubdir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();

        boolean isWindows = os.contains("win");
        boolean isLinux = os.contains("linux") || os.contains("nix") || os.contains("nux");
        boolean isX64 = arch.contains("amd64") || arch.contains("x86_64");
        boolean isArm64 = arch.contains("aarch64") || (arch.contains("arm") && arch.contains("64"));

        if (isLinux && isX64) return "linux-x64";
        if (isLinux && isArm64) return "linux-arm64";
        if (isWindows && isX64) return "windows-x64";
        return null; // fallback to PATH
    }

    /**
     * Attempts to locate the bundled ffmpeg/ffprobe binary for the current platform
     *
     * @param executableName The file name without extension, e.g. {@code "ffmpeg"}
     * @return The full path to the bundled binary, or just the name (fallback to PATH)
     */
    protected static String resolveBundledExecutable(String executableName) {
        String platformDir = detectPlatformSubdir();
        if (platformDir == null) {
            return executableName; // fallback to PATH (unknown platform)
        }

        boolean isWindows = platformDir.startsWith("windows");
        String fileName = isWindows ? executableName + ".exe" : executableName;

        try {
            Path jarPath = Path.of(
                    FfmpegThumbnailGenerator.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            // This jar is in plugins/, so we need to go back first and then enter ffmpeg/
            Path candidate = jarPath
                    .getParent() // plugins/
                    .getParent() // discord-cloud-X.Y/
                    .resolve("ffmpeg")
                    .resolve(platformDir)
                    .resolve(fileName);

            if (Files.exists(candidate)) {
                // Set the executable flag
                boolean executableFlagSet = candidate.toFile().setExecutable(true, false);
                if (!executableFlagSet) {
                    System.out.println("[FFmpeg] Warning: could not mark bundled binary as executable: " + candidate);
                }
                System.out.println("[FFmpeg] Using bundled binary: " + candidate);
                return candidate.toAbsolutePath().toString();
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            // Fallback below
        }

        System.out.println("Cannot find bundled FFmpeg for platform " + platformDir + ". Trying PATH (" + executableName + ")");
        return executableName;
    }
}
