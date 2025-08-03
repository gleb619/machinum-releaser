package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ZipUtil {

    @SneakyThrows
    public static Map<String, byte[]> readZipFile(byte[] zipData) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                var fileName = entry.getName();
                var content = zis.readAllBytes();
                files.put(fileName, content);
                zis.closeEntry();
            }
        }

        return files;
    }

    /**
     * Creates a zip file containing multiple MP3 files.
     *
     * @param files A map where the key is the name of the file and the value is the byte array content.
     * @return A byte array representing the created zip file.
     * @throws IOException If an I/O error occurs during zip file creation.
     */
    public static byte[] createZipFile(Map<String, byte[]> files) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            for (var entry : files.entrySet()) {
                var zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        return baos.toByteArray();
    }

}
