package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.config.ImageImportProperties;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.job.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.*;
import cn.iocoder.yudao.module.drill.dal.mysql.image.*;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.*;

@Service
public class ImageImportJobServiceImpl implements ImageImportJobService {
    private static final DateTimeFormatter JOB_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource private ImageImportJobMapper imageImportJobMapper;
    @Resource private ImageImportPackageMapper packageMapper;
    @Resource private ImageImportRecordMapper recordMapper;
    @Resource private ImageBatchService imageBatchService;
    @Resource private FileService fileService;
    @Resource private ImageImportProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImageImportJobRespVO createJob(ImageImportJobCreateReqVO reqVO) {
        if (reqVO.getPackages().size() > properties.getMaxPackagesPerJob()) {
            throw exception(IMAGE_IMPORT_MANIFEST_INVALID, "ZIP 数量超过 " + properties.getMaxPackagesPerJob());
        }
        long totalBytes = reqVO.getPackages().stream().map(ImageImportJobCreateReqVO.PackageItem::getSize)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        if (totalBytes > properties.getMaxJobBytes()) {
            throw exception(IMAGE_IMPORT_MANIFEST_INVALID, "任务总大小超过限制");
        }
        Long batchId = imageBatchService.createZipBatch(reqVO.getName(), reqVO.getPackages().size());
        ImageImportJobDO job = new ImageImportJobDO();
        job.setJobNo("ZIP-" + LocalDateTime.now().format(JOB_NO_FORMATTER) + "-" + RandomUtil.randomNumbers(5));
        job.setBatchId(batchId);
        job.setName(reqVO.getName().trim());
        job.setRootName(reqVO.getRootName().trim());
        job.setStatus(IMPORT_JOB_UPLOADING);
        job.setTotalPackageCount(reqVO.getPackages().size());
        job.setUploadedPackageCount(0);
        job.setSuccessPackageCount(0);
        job.setFailedPackageCount(0);
        job.setTotalImageCount(0);
        job.setCreatedCount(0);
        job.setDuplicateCount(0);
        job.setFailedCount(0);
        job.setUploadedBytes(0L);
        imageImportJobMapper.insert(job);

        Set<String> paths = new HashSet<>();
        for (ImageImportJobCreateReqVO.PackageItem item : reqVO.getPackages()) {
            String relativePath = normalizeRelativePath(item.getRelativePath());
            String wellType = resolveWellType(relativePath);
            ImageImportPackageDO packageDO = new ImageImportPackageDO();
            packageDO.setJobId(job.getId());
            packageDO.setBatchId(batchId);
            packageDO.setClientPackageId(item.getClientPackageId());
            boolean duplicatePath = !paths.add(relativePath);
            packageDO.setRelativePath(duplicatePath
                    ? relativePath + "#duplicate-" + item.getClientPackageId() : relativePath);
            packageDO.setWellType(wellType);
            packageDO.setOriginalName(item.getFileName());
            packageDO.setDeclaredSize(Math.max(0L, item.getSize()));
            packageDO.setLastModified(item.getLastModified());
            packageDO.setTotalImageCount(0);
            packageDO.setCreatedCount(0);
            packageDO.setDuplicateCount(0);
            packageDO.setFailedCount(0);
            packageDO.setIgnoredJsonCount(0);
            packageDO.setWarningCount(0);
            packageDO.setRetryCount(0);
            packageDO.setCleanupPending(false);
            if (duplicatePath) {
                packageDO.setStatus(IMPORT_PACKAGE_FAILED);
                packageDO.setErrorCode("DUPLICATE_RELATIVE_PATH");
                packageDO.setErrorMessage("导入清单存在重复相对路径");
            } else if (item.getSize() <= 0) {
                packageDO.setStatus(IMPORT_PACKAGE_FAILED);
                packageDO.setErrorCode("ARCHIVE_EMPTY");
                packageDO.setErrorMessage("ZIP 文件为空");
            } else if (item.getSize() > properties.getMaxArchiveBytes()) {
                packageDO.setStatus(IMPORT_PACKAGE_FAILED);
                packageDO.setErrorCode("ARCHIVE_TOO_LARGE");
                packageDO.setErrorMessage("ZIP 文件超过允许大小");
            } else {
                packageDO.setStatus(IMPORT_PACKAGE_UPLOADING);
            }
            packageMapper.insert(packageDO);
        }
        refreshJob(job.getId());
        return getJob(job.getId());
    }

    @Override
    public FilePresignedUrlRespVO presignPackage(Long packageId) {
        ImageImportPackageDO packageDO = getPackage(packageId);
        ImageImportJobDO job = getJobDO(packageDO.getJobId());
        if (!IMPORT_JOB_UPLOADING.equals(job.getStatus())
                && !IMPORT_JOB_WAITING_RETRY.equals(job.getStatus())) {
            throw exception(IMAGE_IMPORT_JOB_STATE_INVALID);
        }
        if (!IMPORT_PACKAGE_UPLOADING.equals(packageDO.getStatus())) {
            throw exception(IMAGE_IMPORT_PACKAGE_STATE_INVALID);
        }
        FilePresignedUrlRespVO presigned = fileService.presignPutUrl(packageDO.getOriginalName(),
                "drill-imports/job-" + packageDO.getJobId() + "/packages");
        ImageImportPackageDO update = new ImageImportPackageDO();
        update.setId(packageId);
        update.setFileConfigId(presigned.getConfigId());
        update.setObjectKey(presigned.getPath());
        update.setOriginalUrl(presigned.getUrl());
        packageMapper.updateById(update);
        return presigned;
    }

    @Override
    public void completePackageUpload(Long packageId, ImageImportUploadCompleteReqVO reqVO) {
        ImageImportPackageDO packageDO = getPackage(packageId);
        ImageImportJobDO job = getJobDO(packageDO.getJobId());
        if (!IMPORT_JOB_UPLOADING.equals(job.getStatus())
                && !IMPORT_JOB_WAITING_RETRY.equals(job.getStatus())) {
            throw exception(IMAGE_IMPORT_JOB_STATE_INVALID);
        }
        if (IMPORT_PACKAGE_UPLOADED.equals(packageDO.getStatus())) {
            return;
        }
        if (!IMPORT_PACKAGE_UPLOADING.equals(packageDO.getStatus())) {
            throw exception(IMAGE_IMPORT_PACKAGE_STATE_INVALID);
        }
        if (!Objects.equals(packageDO.getFileConfigId(), reqVO.getConfigId())
                || !Objects.equals(packageDO.getObjectKey(), reqVO.getPath())
                || !Objects.equals(packageDO.getOriginalUrl(), reqVO.getUrl())
                || !Objects.equals(packageDO.getDeclaredSize(), reqVO.getSize())) {
            throw exception(IMAGE_IMPORT_UPLOAD_MISMATCH);
        }
        FileCreateReqVO fileReq = new FileCreateReqVO();
        fileReq.setConfigId(reqVO.getConfigId());
        fileReq.setPath(reqVO.getPath());
        fileReq.setUrl(reqVO.getUrl());
        fileReq.setName(packageDO.getOriginalName());
        fileReq.setType("application/zip");
        fileReq.setSize(reqVO.getSize());
        Long fileId = fileService.createFile(fileReq);
        ImageImportPackageDO update = new ImageImportPackageDO();
        update.setId(packageId);
        update.setArchiveFileId(fileId);
        update.setActualSize(reqVO.getSize());
        update.setStatus(IMPORT_PACKAGE_UPLOADED);
        update.setErrorCode(null);
        update.setErrorMessage(null);
        packageMapper.updateById(update);
        refreshJob(packageDO.getJobId());
    }

    @Override
    public void startJob(Long jobId) {
        ImageImportJobDO job = getJobDO(jobId);
        if (!IMPORT_JOB_UPLOADING.equals(job.getStatus())) {
            throw exception(IMAGE_IMPORT_JOB_STATE_INVALID);
        }
        List<ImageImportPackageDO> packages = packageMapper.selectByJobId(jobId);
        if (packages.stream().noneMatch(item -> IMPORT_PACKAGE_UPLOADED.equals(item.getStatus()))) {
            throw exception(IMAGE_IMPORT_JOB_STATE_INVALID);
        }
        for (ImageImportPackageDO item : packages) {
            if (IMPORT_PACKAGE_UPLOADED.equals(item.getStatus())) {
                ImageImportPackageDO update = new ImageImportPackageDO();
                update.setId(item.getId());
                update.setStatus(IMPORT_PACKAGE_PENDING);
                packageMapper.updateById(update);
            }
        }
        ImageImportJobDO update = new ImageImportJobDO();
        update.setId(jobId);
        update.setStatus(IMPORT_JOB_PROCESSING);
        update.setStartedAt(LocalDateTime.now());
        imageImportJobMapper.updateById(update);
    }

    @Override
    public ImageImportJobRespVO getJob(Long jobId) {
        ImageImportJobDO job = getJobDO(jobId);
        ImageImportJobRespVO result = BeanUtils.toBean(job, ImageImportJobRespVO.class);
        List<ImageImportPackageDO> packages = packageMapper.selectByJobId(jobId);
        result.setPackages(packages.stream().map(this::convertPackage).toList());
        result.setCanStart(IMPORT_JOB_UPLOADING.equals(job.getStatus()) && packages.stream()
                .anyMatch(item -> IMPORT_PACKAGE_UPLOADED.equals(item.getStatus())));
        result.setCanRetry(IMPORT_JOB_WAITING_RETRY.equals(job.getStatus()));
        result.setCanFinishPartial(IMPORT_JOB_WAITING_RETRY.equals(job.getStatus()));
        return result;
    }

    @Override
    public PageResult<ImageImportPackageRespVO> getPackagePage(ImageImportPackagePageReqVO reqVO) {
        getJobDO(reqVO.getJobId());
        PageResult<ImageImportPackageDO> page = packageMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::convertPackage).toList(), page.getTotal());
    }

    @Override
    public ImageImportJobRespVO getJobByBatchId(Long batchId) {
        ImageImportJobDO job = imageImportJobMapper.selectByBatchId(batchId);
        if (job == null) {
            throw exception(IMAGE_IMPORT_JOB_NOT_EXISTS);
        }
        return getJob(job.getId());
    }

    @Override
    public void retryPackage(Long packageId) {
        ImageImportPackageDO packageDO = getPackage(packageId);
        ImageImportJobDO job = getJobDO(packageDO.getJobId());
        if (!IMPORT_JOB_WAITING_RETRY.equals(job.getStatus())
                || !(IMPORT_PACKAGE_FAILED.equals(packageDO.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(packageDO.getStatus())
                || IMPORT_PACKAGE_UPLOADED.equals(packageDO.getStatus()))
                || packageDO.getArchiveFileId() == null) {
            throw exception(IMAGE_IMPORT_PACKAGE_STATE_INVALID);
        }
        packageMapper.requeue(packageId, value(packageDO.getRetryCount()) + 1, null, null);
        imageImportJobMapper.resumeProcessing(job.getId());
    }

    @Override
    public FilePresignedUrlRespVO resetPackageUpload(Long packageId) {
        ImageImportPackageDO packageDO = getPackage(packageId);
        ImageImportJobDO job = getJobDO(packageDO.getJobId());
        if (!IMPORT_JOB_WAITING_RETRY.equals(job.getStatus())
                || !(IMPORT_PACKAGE_FAILED.equals(packageDO.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(packageDO.getStatus()))) {
            throw exception(IMAGE_IMPORT_PACKAGE_STATE_INVALID);
        }
        cleanupArchive(packageDO);
        ImageImportPackageDO cleaned = getPackage(packageId);
        if (cleaned.getArchiveFileId() != null || StrUtil.isNotBlank(cleaned.getObjectKey())) {
            throw exception(IMAGE_IMPORT_PACKAGE_STATE_INVALID);
        }
        packageMapper.resetForUpload(packageId, value(packageDO.getRetryCount()));
        return presignPackage(packageId);
    }

    @Override
    public void finishPartial(Long jobId) {
        refreshJob(jobId);
        ImageImportJobDO job = getJobDO(jobId);
        if (!IMPORT_JOB_WAITING_RETRY.equals(job.getStatus())) {
            throw exception(IMAGE_IMPORT_JOB_STATE_INVALID);
        }
        if (value(job.getTotalImageCount()) > 0) {
            imageBatchService.finishImport(job.getBatchId(), true);
        } else {
            imageBatchService.cancelImport(job.getBatchId());
        }
        for (ImageImportPackageDO packageDO : packageMapper.selectByJobId(jobId)) {
            cleanupArchive(packageDO);
        }
        ImageImportJobDO update = new ImageImportJobDO();
        update.setId(jobId);
        update.setStatus(value(job.getCreatedCount()) > 0
                ? IMPORT_JOB_PARTIAL_COMPLETED : IMPORT_JOB_FAILED);
        update.setFinishedAt(LocalDateTime.now());
        imageImportJobMapper.updateById(update);
    }

    @Override
    public void refreshJob(Long jobId) {
        ImageImportJobDO job = getJobDO(jobId);
        List<ImageImportPackageDO> packages = packageMapper.selectByJobId(jobId);
        List<ImageImportRecordDO> records = recordMapper.selectByJobId(jobId);
        Map<Long, List<ImageImportRecordDO>> byPackage = new HashMap<>();
        for (ImageImportRecordDO record : records) {
            byPackage.computeIfAbsent(record.getPackageId(), ignored -> new ArrayList<>()).add(record);
        }
        for (ImageImportPackageDO packageDO : packages) {
            List<ImageImportRecordDO> packageRecords = byPackage.getOrDefault(packageDO.getId(), List.of());
            ImageImportPackageDO counts = new ImageImportPackageDO();
            counts.setId(packageDO.getId());
            counts.setTotalImageCount(packageRecords.size());
            counts.setCreatedCount(count(packageRecords, IMPORT_RESULT_CREATED));
            counts.setDuplicateCount(count(packageRecords, IMPORT_RESULT_DUPLICATE));
            counts.setFailedCount(count(packageRecords, IMPORT_RESULT_FAILED));
            packageMapper.updateById(counts);
            packageDO.setTotalImageCount(counts.getTotalImageCount());
            packageDO.setCreatedCount(counts.getCreatedCount());
            packageDO.setDuplicateCount(counts.getDuplicateCount());
            packageDO.setFailedCount(counts.getFailedCount());
        }
        int failedPackages = (int) packages.stream().filter(item -> IMPORT_PACKAGE_FAILED.equals(item.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(item.getStatus())).count();
        int created = count(records, IMPORT_RESULT_CREATED);
        int duplicate = count(records, IMPORT_RESULT_DUPLICATE);
        int failed = count(records, IMPORT_RESULT_FAILED);
        imageBatchService.replaceImportStatistics(job.getBatchId(), records.size(), created, failed,
                duplicate, created, failedPackages);

        ImageImportJobDO update = new ImageImportJobDO();
        update.setId(jobId);
        update.setUploadedPackageCount((int) packages.stream().filter(item -> item.getActualSize() != null).count());
        update.setSuccessPackageCount((int) packages.stream().filter(item -> IMPORT_PACKAGE_SUCCESS.equals(item.getStatus())).count());
        update.setFailedPackageCount(failedPackages);
        update.setTotalImageCount(records.size());
        update.setCreatedCount(created);
        update.setDuplicateCount(duplicate);
        update.setFailedCount(failed);
        update.setUploadedBytes(packages.stream().map(ImageImportPackageDO::getActualSize)
                .filter(Objects::nonNull).mapToLong(Long::longValue).sum());

        boolean allTerminal = packages.stream().allMatch(item -> IMPORT_PACKAGE_SUCCESS.equals(item.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(item.getStatus()) || IMPORT_PACKAGE_FAILED.equals(item.getStatus()));
        imageImportJobMapper.updateById(update);
        if (IMPORT_JOB_PROCESSING.equals(job.getStatus()) && allTerminal) {
            boolean complete = failedPackages == 0 && failed == 0 && !records.isEmpty();
            String target = complete ? IMPORT_JOB_COMPLETED : IMPORT_JOB_WAITING_RETRY;
            if (imageImportJobMapper.transitionStatus(jobId, IMPORT_JOB_PROCESSING, target, LocalDateTime.now())
                    && complete) {
                imageBatchService.finishImport(job.getBatchId(), false);
            }
        }
    }

    @Override
    public void cleanupArchive(ImageImportPackageDO packageDO) {
        if (packageDO.getArchiveFileId() == null && StrUtil.isBlank(packageDO.getObjectKey())) {
            return;
        }
        try {
            if (packageDO.getArchiveFileId() != null) {
                fileService.deleteFile(packageDO.getArchiveFileId());
            } else {
                fileService.deleteObject(packageDO.getFileConfigId(), packageDO.getObjectKey());
            }
            packageMapper.clearArchiveReferences(packageDO.getId());
        } catch (Exception ex) {
            ImageImportPackageDO update = new ImageImportPackageDO();
            update.setId(packageDO.getId());
            update.setCleanupPending(true);
            update.setErrorMessage(StrUtil.maxLength("暂存 ZIP 清理失败: " + ex.getMessage(), 500));
            packageMapper.updateById(update);
        }
    }

    private ImageImportJobDO getJobDO(Long id) {
        ImageImportJobDO job = imageImportJobMapper.selectById(id);
        if (job == null) throw exception(IMAGE_IMPORT_JOB_NOT_EXISTS);
        return job;
    }

    private ImageImportPackageDO getPackage(Long id) {
        ImageImportPackageDO packageDO = packageMapper.selectById(id);
        if (packageDO == null) throw exception(IMAGE_IMPORT_PACKAGE_NOT_EXISTS);
        return packageDO;
    }

    private ImageImportPackageRespVO convertPackage(ImageImportPackageDO packageDO) {
        ImageImportPackageRespVO result = BeanUtils.toBean(packageDO, ImageImportPackageRespVO.class);
        boolean failed = IMPORT_PACKAGE_FAILED.equals(packageDO.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(packageDO.getStatus());
        result.setCanRetry(failed && packageDO.getArchiveFileId() != null);
        result.setCanResetUpload(IMPORT_PACKAGE_FAILED.equals(packageDO.getStatus())
                || IMPORT_PACKAGE_PARTIAL_SUCCESS.equals(packageDO.getStatus()));
        return result;
    }

    private String normalizeRelativePath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || normalized.contains("\u0000") || Arrays.asList(normalized.split("/")).contains("..")) {
            throw exception(IMAGE_IMPORT_MANIFEST_INVALID, "非法相对路径");
        }
        return normalized;
    }

    private String resolveWellType(String relativePath) {
        int slash = relativePath.indexOf('/');
        if (slash <= 0) throw exception(IMAGE_IMPORT_MANIFEST_INVALID, "ZIP 必须位于一级井类型目录下");
        return StrUtil.maxLength(relativePath.substring(0, slash), 64);
    }

    private int count(List<ImageImportRecordDO> records, String result) {
        return (int) records.stream().filter(item -> result.equals(item.getResult())).count();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
