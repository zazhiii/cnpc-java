package cn.iocoder.yudao.module.drill.service.image;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipException;

/** ZIP 目录安全检查，不解压文件内容。 */
final class ZipArchiveValidator {
    private static final long EOCD_SIGNATURE = 0x06054b50L;
    private static final long CENTRAL_SIGNATURE = 0x02014b50L;
    private static final int MAX_EOCD_SEARCH = 65_557;

    private ZipArchiveValidator() {
    }

    static String validateEntryPath(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ZIP 条目路径为空");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("\u0000") || Arrays.asList(normalized.split("/")).contains("..")) {
            throw new IllegalArgumentException("ZIP 包含非法路径");
        }
        return normalized;
    }

    /** Java ZipFile 不暴露标志位，因此直接从中央目录检查传统加密和强加密标志。 */
    static void rejectEncryptedEntries(Path archive) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(archive.toFile(), "r")) {
            long eocd = findEndOfCentralDirectory(file);
            file.seek(eocd + 4);
            int diskNumber = readUnsignedShortLE(file);
            int centralDisk = readUnsignedShortLE(file);
            int entriesOnDisk = readUnsignedShortLE(file);
            int entryCount = readUnsignedShortLE(file);
            long centralSize = readUnsignedIntLE(file);
            long centralOffset = readUnsignedIntLE(file);
            if (diskNumber != 0 || centralDisk != 0 || entriesOnDisk != entryCount) {
                throw new ZipException("不支持分卷 ZIP");
            }
            if (entryCount == 0xffff || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
                throw new ZipException("不支持 ZIP64 目录");
            }
            if (centralOffset + centralSize > eocd) {
                throw new ZipException("ZIP 中央目录越界");
            }
            file.seek(centralOffset);
            for (int index = 0; index < entryCount; index++) {
                if (readUnsignedIntLE(file) != CENTRAL_SIGNATURE) {
                    throw new ZipException("ZIP 中央目录损坏");
                }
                readUnsignedShortLE(file);
                readUnsignedShortLE(file);
                int flags = readUnsignedShortLE(file);
                if ((flags & 0x0001) != 0 || (flags & 0x0040) != 0) {
                    throw new ZipException("ZIP 包含加密条目");
                }
                file.skipBytes(18);
                int nameLength = readUnsignedShortLE(file);
                int extraLength = readUnsignedShortLE(file);
                int commentLength = readUnsignedShortLE(file);
                file.skipBytes(12);
                long next = file.getFilePointer() + nameLength + extraLength + commentLength;
                if (next > centralOffset + centralSize) {
                    throw new ZipException("ZIP 中央目录条目越界");
                }
                file.seek(next);
            }
        }
    }

    private static long findEndOfCentralDirectory(RandomAccessFile file) throws IOException {
        long length = file.length();
        if (length < 22) {
            throw new ZipException("ZIP 中央目录不存在");
        }
        long lowerBound = Math.max(0, length - MAX_EOCD_SEARCH);
        for (long position = length - 22; position >= lowerBound; position--) {
            file.seek(position);
            if (readUnsignedIntLE(file) == EOCD_SIGNATURE) {
                file.seek(position + 20);
                int commentLength = readUnsignedShortLE(file);
                if (position + 22L + commentLength == length) {
                    return position;
                }
            }
        }
        throw new ZipException("ZIP 中央目录损坏");
    }

    private static int readUnsignedShortLE(RandomAccessFile file) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
    }

    private static long readUnsignedIntLE(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }
}
