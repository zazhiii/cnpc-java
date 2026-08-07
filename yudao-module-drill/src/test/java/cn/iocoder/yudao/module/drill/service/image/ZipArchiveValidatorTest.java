package cn.iocoder.yudao.module.drill.service.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipArchiveValidatorTest {

    Path tempDir;

    @BeforeEach
    void createWorkspaceTempDirectory() throws IOException {
        tempDir = Path.of("target", "zip-validator-tests", UUID.randomUUID().toString());
        Files.createDirectories(tempDir);
    }

    @Test
    void shouldNormalizeSafeEntryPath() {
        assertEquals("井类型/a.jpg", ZipArchiveValidator.validateEntryPath("井类型\\a.jpg"));
    }

    @Test
    void shouldRejectTraversalAbsoluteDriveAndNullPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> ZipArchiveValidator.validateEntryPath("../../secret.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> ZipArchiveValidator.validateEntryPath("/absolute/a.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> ZipArchiveValidator.validateEntryPath("C:/windows/a.jpg"));
        assertThrows(IllegalArgumentException.class,
                () -> ZipArchiveValidator.validateEntryPath("a\u0000.jpg"));
    }

    @Test
    void shouldAcceptNormalCentralDirectory() throws Exception {
        Path zip = createZip("a.jpg", new byte[]{1, 2, 3});
        ZipArchiveValidator.rejectEncryptedEntries(zip);
    }

    @Test
    void shouldRejectEncryptedFlagInCentralDirectory() throws Exception {
        Path zip = createZip("a.jpg", new byte[]{1, 2, 3});
        byte[] content = Files.readAllBytes(zip);
        int central = findSignature(content, new byte[]{0x50, 0x4b, 0x01, 0x02});
        content[central + 8] = (byte) (content[central + 8] | 0x01);
        Files.write(zip, content);
        assertThrows(ZipException.class, () -> ZipArchiveValidator.rejectEncryptedEntries(zip));
    }

    @Test
    void shouldRejectDamagedCentralDirectory() throws Exception {
        Path damaged = tempDir.resolve("damaged.zip");
        Files.write(damaged, new byte[]{0x50, 0x4b, 0x03, 0x04, 1, 2, 3});
        assertThrows(ZipException.class, () -> ZipArchiveValidator.rejectEncryptedEntries(damaged));
    }

    private Path createZip(String name, byte[] content) throws IOException {
        Path zip = tempDir.resolve("sample-" + System.nanoTime() + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(name));
            output.write(content);
            output.closeEntry();
        }
        return zip;
    }

    private int findSignature(byte[] content, byte[] signature) {
        for (int index = 0; index <= content.length - signature.length; index++) {
            boolean matches = true;
            for (int offset = 0; offset < signature.length; offset++) {
                if (content[index + offset] != signature[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return index;
        }
        throw new IllegalStateException("central directory not found");
    }
}
