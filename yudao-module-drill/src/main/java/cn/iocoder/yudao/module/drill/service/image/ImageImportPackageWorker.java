package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.drill.config.ImageImportProperties;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.ImageImportRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportJobDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportPackageDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportJobMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportPackageMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportRecordMapper;
import cn.iocoder.yudao.module.drill.service.image.model.ArchiveImageImportRequest;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.*;

@Component
@Slf4j
public class ImageImportPackageWorker {
    @Resource private ImageImportPackageMapper packageMapper;
    @Resource private ImageImportJobMapper imageImportJobMapper;
    @Resource private ImageImportRecordMapper recordMapper;
    @Resource private ImageImportService imageImportService;
    @Resource private ImageImportJobService imageImportJobService;
    @Resource private ImageBatchService imageBatchService;
    @Resource private FileService fileService;
    @Resource private ImageImportProperties properties;
    @Resource(name = "imageImportExecutor") private Executor imageImportExecutor;
    private final String workerId = UUID.randomUUID().toString();

    @PostConstruct
    public void recoverAtStartup() {
        TenantUtils.executeIgnore(this::recoverExpired);
    }

    @Scheduled(fixedDelayString = "${drill.image-import.poll-interval:2s}")
    public void dispatch() {
        TenantUtils.executeIgnore(() -> {
            recoverExpired();
            cleanupPendingArchives();
            cancelStaleUploads();
            int limit = Math.max(1, properties.getExecutor().getCoreSize());
            for (ImageImportPackageDO candidate : packageMapper.selectPendingCandidates(limit)) {
                if (!packageMapper.claim(candidate.getId(), workerId,
                        LocalDateTime.now().plus(properties.getPackageTimeout()))) {
                    continue;
                }
                Long tenantId = candidate.getTenantId();
                try {
                    imageImportExecutor.execute(() -> TenantUtils.execute(tenantId, () -> process(candidate.getId())));
                } catch (RuntimeException ex) {
                    resetPending(candidate.getId(), "WORKER_BUSY", "导入线程池繁忙");
                }
            }
        });
    }

    void process(Long packageId) {
        ImageImportPackageDO packageDO = packageMapper.selectById(packageId);
        if (packageDO == null || !IMPORT_PACKAGE_PROCESSING.equals(packageDO.getStatus())) return;
        Path temp = null;
        try {
            if (packageDO.getArchiveFileId() == null || packageDO.getFileConfigId() == null
                    || StrUtil.isBlank(packageDO.getObjectKey())) {
                throw new ArchiveException("ARCHIVE_NOT_UPLOADED", "ZIP 暂存对象不存在");
            }
            temp = Files.createTempFile("drill-import-", ".zip");
            long actualSize = fileService.downloadFile(packageDO.getFileConfigId(), packageDO.getObjectKey(),
                    temp, properties.getMaxArchiveBytes());
            if (actualSize <= 0) throw new ArchiveException("ARCHIVE_EMPTY", "ZIP 文件为空");
            String archiveSha = sha256(temp);
            try {
                ZipArchiveValidator.rejectEncryptedEntries(temp);
            } catch (ZipException ex) {
                throw new ArchiveException("ARCHIVE_CENTRAL_DIRECTORY_INVALID", ex.getMessage());
            }
            PackageScan scan = processArchive(packageDO, temp, actualSize);
            List<ImageImportRecordDO> records = recordMapper.selectByPackageId(packageId);
            int created = count(records, IMPORT_RESULT_CREATED);
            int duplicate = count(records, IMPORT_RESULT_DUPLICATE);
            int failed = count(records, IMPORT_RESULT_FAILED);
            ImageImportPackageDO update = new ImageImportPackageDO();
            update.setId(packageId);
            update.setActualSize(actualSize);
            update.setSha256(archiveSha);
            update.setTotalImageCount(records.size());
            update.setCreatedCount(created);
            update.setDuplicateCount(duplicate);
            update.setFailedCount(failed);
            update.setIgnoredJsonCount(scan.ignoredJson);
            update.setWarningCount(scan.warnings + (int) records.stream()
                    .filter(item -> item.getWarningCode() != null).count());
            update.setStatus(failed == 0 && created + duplicate > 0 ? IMPORT_PACKAGE_SUCCESS
                    : created + duplicate > 0 ? IMPORT_PACKAGE_PARTIAL_SUCCESS : IMPORT_PACKAGE_FAILED);
            update.setFinishedAt(LocalDateTime.now());
            update.setWorkerId(null);
            update.setLeaseUntil(null);
            update.setErrorCode(failed > 0 ? "IMAGE_ENTRY_FAILED" : null);
            update.setErrorMessage(failed > 0 ? failed + " 张图片导入失败" : null);
            packageMapper.updateById(update);
            packageMapper.clearProcessingState(packageId, failed == 0);
            if (IMPORT_PACKAGE_SUCCESS.equals(update.getStatus())) {
                imageImportJobService.cleanupArchive(packageDO);
            }
        } catch (Exception ex) {
            log.warn("[process][packageId({}) ZIP 导入失败]", packageId, ex);
            if (!(ex instanceof ArchiveException) && value(packageDO.getRetryCount()) < properties.getMaxRetries()) {
                resetPending(packageDO.getId(), "ARCHIVE_TRANSIENT_FAILURE",
                        StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "ZIP 处理暂时失败"), 500));
            } else {
                failPackage(packageDO, ex);
            }
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (Exception ignored) { }
            }
            imageImportJobService.refreshJob(packageDO.getJobId());
        }
    }

    private PackageScan processArchive(ImageImportPackageDO packageDO, Path temp, long archiveBytes) throws Exception {
        PackageScan scan = new PackageScan();
        List<ZipEntry> images = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        try (ZipFile zip = new ZipFile(temp.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int entryCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (++entryCount > properties.getMaxEntries()) {
                    throw new ArchiveException("ARCHIVE_ENTRY_LIMIT", "ZIP 条目数量超过限制");
                }
                String path;
                try {
                    path = ZipArchiveValidator.validateEntryPath(entry.getName());
                } catch (IllegalArgumentException ex) {
                    throw new ArchiveException("ARCHIVE_PATH_INVALID", ex.getMessage());
                }
                if (!names.add(path)) {
                    throw new ArchiveException("ARCHIVE_DUPLICATE_ENTRY", "ZIP 存在重复条目: " + path);
                }
                if (entry.getSize() < 0) {
                    throw new ArchiveException("ARCHIVE_SIZE_UNKNOWN", "ZIP 条目大小未知");
                }
                expanded += entry.getSize();
                if (expanded > properties.getMaxExpandedBytes()
                        || expanded > archiveBytes * (long) properties.getMaxCompressionRatio()) {
                    throw new ArchiveException("ARCHIVE_EXPANSION_LIMIT", "ZIP 展开大小或压缩比超过限制");
                }
                String extension = StrUtil.subAfter(path, '.', true).toLowerCase(Locale.ROOT);
                if (Set.of("jpg", "jpeg", "png").contains(extension)) {
                    images.add(entry);
                } else if ("json".equals(extension)) {
                    scan.ignoredJson++;
                } else {
                    scan.warnings++;
                }
            }
            if (images.isEmpty()) {
                throw new ArchiveException("ARCHIVE_NO_IMAGES", "ZIP 中没有可导入图片");
            }
            for (ZipEntry image : images) {
                String entryPath = image.getName().replace('\\', '/');
                ImageImportRecordDO existing = recordMapper.selectByPackageAndEntry(packageDO.getId(), entryPath);
                if (existing != null && (IMPORT_RESULT_CREATED.equals(existing.getResult())
                        || IMPORT_RESULT_DUPLICATE.equals(existing.getResult()))) {
                    continue;
                }
                ArchiveImageImportRequest request = buildRequest(packageDO, image);
                if (image.getSize() > properties.getMaxImageBytes()) {
                    imageImportService.recordArchiveFailure(request, "IMAGE_FILE_TOO_LARGE",
                            "图片大小超过 " + properties.getMaxImageBytes() / 1024 / 1024 + " MB");
                    continue;
                }
                try (InputStream input = zip.getInputStream(image)) {
                    request.setContent(readBounded(input, properties.getMaxImageBytes()));
                    ImageImportRespVO.Item ignored = imageImportService.importArchiveImage(request);
                } catch (Exception ex) {
                    imageImportService.recordArchiveFailure(request, "IMAGE_ENTRY_READ_FAILED",
                            StrUtil.blankToDefault(ex.getMessage(), "图片条目读取失败"));
                }
                heartbeat(packageDO.getId());
            }
        }
        return scan;
    }

    private ArchiveImageImportRequest buildRequest(ImageImportPackageDO packageDO, ZipEntry entry) {
        ArchiveImageImportRequest request = new ArchiveImageImportRequest();
        request.setJobId(packageDO.getJobId());
        request.setPackageId(packageDO.getId());
        request.setBatchId(packageDO.getBatchId());
        request.setEntryPath(entry.getName().replace('\\', '/'));
        request.setOriginalName(StrUtil.subAfter(request.getEntryPath(), '/', true));
        request.setWellType(packageDO.getWellType());
        return request;
    }

    private byte[] readBounded(InputStream input, long maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int length;
        while ((length = input.read(buffer)) >= 0) {
            total += length;
            if (total > maxBytes) throw new ArchiveException("IMAGE_FILE_TOO_LARGE", "图片超过允许大小");
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            byte[] buffer = new byte[64 * 1024];
            while (input.read(buffer) >= 0) {
                // DigestInputStream 在读取时更新摘要。
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void heartbeat(Long packageId) {
        ImageImportPackageDO update = new ImageImportPackageDO();
        update.setId(packageId);
        update.setHeartbeatTime(LocalDateTime.now());
        update.setLeaseUntil(LocalDateTime.now().plus(properties.getPackageTimeout()));
        packageMapper.updateById(update);
    }

    private void failPackage(ImageImportPackageDO packageDO, Exception ex) {
        ImageImportPackageDO update = new ImageImportPackageDO();
        update.setId(packageDO.getId());
        update.setStatus(IMPORT_PACKAGE_FAILED);
        update.setFinishedAt(LocalDateTime.now());
        update.setWorkerId(null);
        update.setLeaseUntil(null);
        update.setErrorCode(ex instanceof ArchiveException archive ? archive.code : "ARCHIVE_PROCESS_FAILED");
        update.setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "ZIP 处理失败"), 500));
        packageMapper.updateById(update);
        packageMapper.clearProcessingState(packageDO.getId(), false);
    }

    private void recoverExpired() {
        for (ImageImportPackageDO item : packageMapper.selectExpired(LocalDateTime.now())) {
            if (value(item.getRetryCount()) < properties.getMaxRetries()) {
                resetPending(item.getId(), "TASK_LEASE_EXPIRED", "处理租约过期，已重新排队");
            } else {
                failPackage(item, new ArchiveException("TASK_LEASE_EXPIRED", "处理租约多次过期"));
                imageImportJobService.refreshJob(item.getJobId());
            }
        }
    }

    private void resetPending(Long id, String code, String message) {
        ImageImportPackageDO current = packageMapper.selectById(id);
        packageMapper.requeue(id, value(current == null ? null : current.getRetryCount()) + 1,
                code, message);
    }

    private void cleanupPendingArchives() {
        for (ImageImportPackageDO packageDO : packageMapper.selectCleanupPending(20)) {
            imageImportJobService.cleanupArchive(packageDO);
        }
    }

    private void cancelStaleUploads() {
        for (ImageImportJobDO job : imageImportJobMapper.selectUploadingBefore(
                LocalDateTime.now().minus(properties.getUploadTimeout()))) {
            for (ImageImportPackageDO packageDO : packageMapper.selectByJobId(job.getId())) {
                imageImportJobService.cleanupArchive(packageDO);
            }
            ImageImportJobDO update = new ImageImportJobDO();
            update.setId(job.getId());
            update.setStatus(IMPORT_JOB_CANCELLED);
            update.setFinishedAt(LocalDateTime.now());
            update.setErrorSummary("上传阶段超过 " + properties.getUploadTimeout() + " 无活动，已自动取消");
            imageImportJobMapper.updateById(update);
            try {
                imageBatchService.cancelImport(job.getBatchId());
            } catch (Exception ex) {
                log.warn("[cancelStaleUploads][batchId({}) 批次取消失败]", job.getBatchId(), ex);
            }
        }
    }

    private int count(List<ImageImportRecordDO> records, String result) {
        return (int) records.stream().filter(item -> result.equals(item.getResult())).count();
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private static class PackageScan { int ignoredJson; int warnings; }

    private static class ArchiveException extends RuntimeException {
        private final String code;
        ArchiveException(String code, String message) { super(message); this.code = code; }
    }

}
