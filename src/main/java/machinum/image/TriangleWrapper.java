package machinum.image;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import machinum.exception.AppException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TriangleWrapper {

    public static final Integer WIREFRAME_MODE_WITHOUT_STROKE = 0;
    public static final Integer WIREFRAME_MODE_WITH_STROKE = 1;
    public static final Integer WIREFRAME_MODE_STROKE_ONLY = 2;
    public static final String DEFAULT_BINARY_PATH = "triangle";

    private final String binaryPath;

    public TriangleWrapper(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    @SneakyThrows
    public static File addTriangleEffect(TriangleSettings settings) {
        var wrapper = new TriangleWrapper(DEFAULT_BINARY_PATH);
        wrapper.runTriangle(settings);

        return new File(settings.getOutputPath());
    }

    @SneakyThrows
    public static File addTriangleEffect(Image image) {
        var output = File.createTempFile("machinum", "_tr.jpg");
        Files.write(output.toPath(), image.getData());
        var imagePath = output.getAbsolutePath();
        var settings = defaultSettings(imagePath, imagePath);

        return addTriangleEffect(settings);
    }

    public static TriangleSettings defaultSettings(String inputPath, String outputPath) {
        return TriangleSettings
                .builder()
                .inputPath(inputPath)
                .outputPath(outputPath)
                .blurRadius(2)
                .noiseFactor(0)
                .blurFactor(1)
                .edgeFactor(6)
                .pointRate(0.075f)
                .pointsThreshold(10)
                .maximumNumberOfPoints(3_500)
                .sobelFilterThreshold(10)
                .wireframeMode(WIREFRAME_MODE_WITHOUT_STROKE)
                .strokeWidth(1)
                .build();
    }

    /**
     * The following flags are supported:
     * <p>
     * | Flag | Default | Description |
     * | --- | --- | --- |
     * | `in` | n/a | Source image |
     * | `out` | n/a | Destination image |
     * | `bl` | 2 | Blur radius |
     * | `nf` | 0 | Noise factor |
     * | `bf` | 1 | Blur factor |
     * | `ef` | 6 | Edge factor |
     * | `pr` | 0.075 | Point rate |
     * | `pth` | 10 | Points threshold |
     * | `pts` | 2500 | Maximum number of points |
     * | `so` | 10 | Sobel filter threshold |
     * | `sl` | false | Use solid stroke color (yes/no) |
     * | `wf` | 0 | Wireframe mode (0: without stroke, 1: with stroke, 2: stroke only) |
     * | `st` | 1 | Stroke width |
     * | `gr` | false | Output in grayscale mode |
     * | `web` | false | Open the SVG file in the web browser |
     * | `bg` | ' ' | Background color (specified as hex value) |
     * | `cw` | system spec. | Number of files to process concurrently
     *
     * @param triangleSettings@throws IOException
     * @throws InterruptedException
     */
    public void runTriangle(TriangleSettings triangleSettings) throws IOException, InterruptedException {

        var binaryFile = new File(this.binaryPath).getCanonicalFile();
        if (!binaryFile.exists()) {
            throw new AppException("File not found, please install one for Delaunay triangulation: %s".formatted(
                    binaryFile.getAbsolutePath()));
        }

        List<String> command = new ArrayList<>();
        command.add(binaryFile.getAbsolutePath());
        command.add("-in");
        command.add(triangleSettings.getInputPath());
        command.add("-out");
        command.add(triangleSettings.getOutputPath());
        command.add("-bl=" + triangleSettings.getBlurRadius());
        command.add("-nf=" + triangleSettings.getNoiseFactor());
        command.add("-bf=" + triangleSettings.getBlurFactor());
        command.add("-ef=" + triangleSettings.getEdgeFactor());
        command.add("-pr=" + triangleSettings.getPointRate());
        command.add("-pth=" + triangleSettings.getPointsThreshold());
        command.add("-pts=" + triangleSettings.getMaximumNumberOfPoints());
        command.add("-so=" + triangleSettings.getSobelFilterThreshold());
        command.add("-wf=" + triangleSettings.getWireframeMode());
        command.add("-st=" + triangleSettings.getStrokeWidth());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO(); // Redirect output to console
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Triangle binary execution failed with exit code: " + exitCode);
        }
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class TriangleSettings {

        String inputPath;
        String outputPath;
        int blurRadius;
        int noiseFactor;
        int blurFactor;
        int edgeFactor;
        float pointRate;
        int pointsThreshold;
        int maximumNumberOfPoints;
        int sobelFilterThreshold;
        int wireframeMode;
        int strokeWidth;

    }

}


