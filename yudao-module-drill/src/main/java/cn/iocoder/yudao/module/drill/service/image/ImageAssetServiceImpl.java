package cn.iocoder.yudao.module.drill.service.image;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.drill.controller.admin.image.vo.asset.*;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageAssetDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageBatchDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.image.ImageImportRecordDO;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageAssetMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.image.ImageImportRecordMapper;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileCreateReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.drill.enums.ImageImportConstants.*;

@Service
@Slf4j
public class ImageAssetServiceImpl implements ImageAssetService {

    private static final DateTimeFormatter ASSET_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String ARCHIVE_STATUS_ACTIVE = "ACTIVE";
    private static final String UNKNOWN_POLICY_SKIP = "SKIP";

    @Resource
    private ImageAssetMapper imageAssetMapper;
    @Resource
    private ImageImportRecordMapper imageImportRecordMapper;
    @Resource
    private ImageBatchService imageBatchService;
    @Resource
    private ImageFileValidator imageFileValidator;
    @Resource
    private FileService fileService;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public PageResult<ImageAssetDO> getAssetPage(ImageAssetPageReqVO pageReqVO) {
        return imageAssetMapper.selectPage(pageReqVO);
    }

    @Override
    public ImageAssetDO getAsset(Long id) {
        ImageAssetDO asset = imageAssetMapper.selectById(id);
        if (asset == null) {
            throw exception(IMAGE_ASSET_NOT_EXISTS);
        }
        return asset;
    }

    @Override
    public ImageImportRespVO uploadAndImport(ImageAssetUploadReqVO uploadReqVO) {
        ImageBatchDO batch = imageBatchService.validateBatchForImport(uploadReqVO.getBatchId());
        MultipartFile[] files = uploadReqVO.getFiles();
        if (files == null || files.length == 0) {
            throw exception(IMAGE_FILE_EMPTY);
        }
        ImageImportRespVO result = new ImageImportRespVO();
        for (MultipartFile file : files) {
            result.setTotalCount(result.getTotalCount() + 1);
            String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "unnamed-image");
            try {
                byte[] content = IoUtil.readBytes(file.getInputStream());
                Candidate candidate = fromUploadedFile(originalName, content);
                ImageImportRespVO.Item item = importDirectFile(batch, candidate, file.getContentType());
                addItem(result, item, candidate.getMetadataStatus());
            } catch (Exception ex) {
                ImageImportRespVO.Item item = failureItem(null, originalName, ex);
                result.getItems().add(item);
                result.setFailedCount(result.getFailedCount() + 1);
                insertImportRecord(batch.getId(), item, file.getSize(), null);
                log.warn("[uploadAndImport][batchId({}) file({}) 导入失败]", batch.getId(), originalName, ex);
            }
        }
        updateBatchStatistics(batch.getId(), result);
        return result;
    }

    @Override
    public FilePresignedUrlRespVO createPresignedUpload(ImagePresignedUploadReqVO reqVO) {
        imageBatchService.validateBatchForImport(reqVO.getBatchId());
        String directory = buildOriginalDirectory(reqVO.getBatchId());
        return fileService.presignPutUrl(reqVO.getName(), directory);
    }

    @Override
    public ImageImportRespVO completeImport(ImageImportCompleteReqVO reqVO) {
        ImageBatchDO batch = imageBatchService.validateBatchForImport(reqVO.getBatchId());
        ImageImportRespVO result = new ImageImportRespVO();
        for (ImageImportCompleteReqVO.FileItem file : reqVO.getFiles()) {
            result.setTotalCount(result.getTotalCount() + 1);
            try {
                validateObjectPath(batch.getId(), file.getPath());
                byte[] content = fileService.getFileContent(file.getConfigId(), file.getPath());
                if (content == null) {
                    throw exception(IMAGE_OBJECT_NOT_FOUND);
                }
                if (content.length != file.getSize()) {
                    throw exception(IMAGE_SIZE_MISMATCH);
                }
                String actualSha256 = DigestUtil.sha256Hex(content);
                if (!actualSha256.equalsIgnoreCase(file.getSha256())) {
                    throw exception(IMAGE_HASH_MISMATCH);
                }
                Candidate candidate = fromCompleteRequest(reqVO, file, content, actualSha256);
                ImageImportRespVO.Item item = importPresignedFile(batch, candidate, file);
                addItem(result, item, candidate.getMetadataStatus());
            } catch (Exception ex) {
                ImageImportRespVO.Item item = failureItem(file.getClientFileId(), file.getName(), ex);
                result.getItems().add(item);
                result.setFailedCount(result.getFailedCount() + 1);
                insertImportRecord(batch.getId(), item, file.getSize(), file.getSha256());
                log.warn("[completeImport][batchId({}) path({}) 导入失败]", batch.getId(), file.getPath(), ex);
            }
        }
        updateBatchStatistics(batch.getId(), result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAssetMetadata(ImageAssetUpdateMetadataReqVO updateReqVO) {
        ImageAssetDO asset = getAsset(updateReqVO.getId());
        BeanUtils.copyProperties(updateReqVO, asset);
        asset.setMetadataStatus(resolveMetadataStatus(asset.getDrillCode(), asset.getPhotoType(),
                asset.getBladeNo(), "PENDING"));
        imageAssetMapper.updateById(asset);
        imageBatchService.refreshMetadataStatistics(asset.getBatchId());
    }

    private ImageImportRespVO.Item importDirectFile(ImageBatchDO batch, Candidate candidate,
                                                     String clientMimeType) {
        ImageAssetDO duplicate = imageAssetMapper.selectBySha256(candidate.getSha256());
        if (duplicate != null) {
            ImageImportRespVO.Item item = duplicateItem(candidate, duplicate);
            insertImportRecord(batch.getId(), item, (long) candidate.getContent().length, candidate.getSha256());
            return item;
        }
        String url = fileService.createFile(candidate.getContent(), candidate.getOriginalName(),
                buildOriginalDirectory(batch.getId()), clientMimeType);
        FileDO file = fileService.getFileByUrl(url);
        if (file == null) {
            throw exception(IMAGE_FILE_RECORD_MISSING);
        }
        return createAssetAndRecordInTransaction(batch, candidate, file, SOURCE_LOCAL_UPLOAD);
    }

    private ImageImportRespVO.Item importPresignedFile(ImageBatchDO batch, Candidate candidate,
                                                        ImageImportCompleteReqVO.FileItem requestFile) {
        ImageAssetDO duplicate = imageAssetMapper.selectBySha256(candidate.getSha256());
        if (duplicate != null) {
            ImageImportRespVO.Item item = duplicateItem(candidate, duplicate);
            insertImportRecord(batch.getId(), item, requestFile.getSize(), candidate.getSha256());
            return item;
        }
        FileCreateReqVO fileCreateReqVO = new FileCreateReqVO();
        fileCreateReqVO.setConfigId(requestFile.getConfigId());
        fileCreateReqVO.setPath(requestFile.getPath());
        fileCreateReqVO.setUrl(requestFile.getUrl());
        fileCreateReqVO.setName(requestFile.getName());
        fileCreateReqVO.setType(candidate.getMimeType());
        fileCreateReqVO.setSize(requestFile.getSize());
        Long fileId = fileService.createFile(fileCreateReqVO);
        FileDO file = fileService.getFile(fileId);
        return createAssetAndRecordInTransaction(batch, candidate, file, SOURCE_PRESIGNED_UPLOAD);
    }

    private ImageImportRespVO.Item createAssetAndRecordInTransaction(ImageBatchDO batch, Candidate candidate,
                                                                      FileDO file, String sourceType) {
        return transactionTemplate.execute(status -> createAssetAndRecord(batch, candidate, file, sourceType));
    }

    private ImageImportRespVO.Item createAssetAndRecord(ImageBatchDO batch, Candidate candidate,
                                                         FileDO file, String sourceType) {
        ImageAssetDO asset = new ImageAssetDO()
                .setAssetNo(generateAssetNo())
                .setBatchId(batch.getId())
                .setProjectId(batch.getProjectId())
                .setDrillCode(candidate.getDrillCode())
                .setDrillType(candidate.getDrillType())
                .setSceneType(StrUtil.blankToDefault(candidate.getSceneType(), batch.getSceneType()))
                .setPhotoType(candidate.getPhotoType())
                .setBladeNo(candidate.getBladeNo())
                .setSourceType(sourceType)
                .setSourceBizId(candidate.getClientFileId())
                .setOriginalFileId(file.getId())
                .setFileConfigId(file.getConfigId())
                .setObjectKey(file.getPath())
                .setOriginalUrl(file.getUrl())
                .setOriginalName(candidate.getOriginalName())
                .setMimeType(candidate.getMimeType())
                .setFileSize((long) candidate.getContent().length)
                .setWidth(candidate.getWidth())
                .setHeight(candidate.getHeight())
                .setSha256(candidate.getSha256())
                .setMetadataStatus(candidate.getMetadataStatus())
                .setArchiveStatus(ARCHIVE_STATUS_ACTIVE)
                .setRemark(candidate.getRemark());
        imageAssetMapper.insert(asset);
        ImageImportRespVO.Item item = new ImageImportRespVO.Item(
                candidate.getClientFileId(), candidate.getOriginalName(), asset.getId(), null,
                IMPORT_RESULT_CREATED, null, null);
        insertImportRecord(batch.getId(), item, asset.getFileSize(), asset.getSha256());
        return item;
    }

    private Candidate fromUploadedFile(String originalName, byte[] content) {
        ImageFileValidator.ImageInfo imageInfo = imageFileValidator.validate(content, originalName);
        Candidate candidate = new Candidate();
        candidate.setOriginalName(originalName);
        candidate.setContent(content);
        candidate.setSha256(DigestUtil.sha256Hex(content));
        candidate.setWidth(imageInfo.getWidth());
        candidate.setHeight(imageInfo.getHeight());
        candidate.setMimeType(imageInfo.getMimeType());
        candidate.setMetadataStatus(METADATA_STATUS_PENDING);
        return candidate;
    }

    private Candidate fromCompleteRequest(ImageImportCompleteReqVO reqVO,
                                          ImageImportCompleteReqVO.FileItem file,
                                          byte[] content, String actualSha256) {
        ImageFileValidator.ImageInfo imageInfo = imageFileValidator.validate(content, file.getName());
        Candidate candidate = new Candidate();
        candidate.setClientFileId(file.getClientFileId());
        candidate.setOriginalName(file.getName());
        candidate.setContent(content);
        candidate.setSha256(actualSha256);
        candidate.setWidth(imageInfo.getWidth());
        candidate.setHeight(imageInfo.getHeight());
        candidate.setMimeType(imageInfo.getMimeType());
        candidate.setDrillCode(file.getDrillCode());
        candidate.setDrillType(file.getDrillType());
        candidate.setSceneType(file.getSceneType());
        candidate.setPhotoType(file.getPhotoType());
        candidate.setBladeNo(file.getBladeNo());
        candidate.setRemark(file.getRemark());
        candidate.setMetadataStatus(resolveMetadataStatus(candidate.getDrillCode(), candidate.getPhotoType(),
                candidate.getBladeNo(), reqVO.getUnknownMetadataPolicy()));
        return candidate;
    }

    private String resolveMetadataStatus(String drillCode, String photoType, String bladeNo,
                                         String unknownMetadataPolicy) {
        boolean complete = StrUtil.isAllNotBlank(drillCode, photoType)
                && (!"BLADE".equalsIgnoreCase(photoType) || StrUtil.isNotBlank(bladeNo));
        if (complete) {
            return METADATA_STATUS_COMPLETE;
        }
        if (UNKNOWN_POLICY_SKIP.equalsIgnoreCase(unknownMetadataPolicy)) {
            throw exception(IMAGE_METADATA_INCOMPLETE);
        }
        return METADATA_STATUS_PENDING;
    }

    private void validateObjectPath(Long batchId, String path) {
        String expectedPrefix = buildOriginalDirectory(batchId) + "/";
        if (!path.replace('\\', '/').startsWith(expectedPrefix)) {
            throw exception(IMAGE_OBJECT_NOT_FOUND);
        }
    }

    private void addItem(ImageImportRespVO result, ImageImportRespVO.Item item, String metadataStatus) {
        result.getItems().add(item);
        if (IMPORT_RESULT_CREATED.equals(item.getResult())) {
            result.setCreatedCount(result.getCreatedCount() + 1);
        } else if (IMPORT_RESULT_DUPLICATE.equals(item.getResult())) {
            result.setDuplicateCount(result.getDuplicateCount() + 1);
        }
    }

    private void updateBatchStatistics(Long batchId, ImageImportRespVO result) {
        int pendingMetadata = (int) result.getItems().stream()
                .filter(item -> IMPORT_RESULT_CREATED.equals(item.getResult()))
                .map(ImageImportRespVO.Item::getAssetId)
                .map(imageAssetMapper::selectById)
                .filter(asset -> asset != null && METADATA_STATUS_PENDING.equals(asset.getMetadataStatus()))
                .count();
        imageBatchService.updateImportStatistics(batchId, result.getTotalCount(), result.getCreatedCount(),
                result.getFailedCount(), result.getDuplicateCount(), pendingMetadata);
    }

    private ImageImportRespVO.Item duplicateItem(Candidate candidate, ImageAssetDO duplicate) {
        return new ImageImportRespVO.Item(candidate.getClientFileId(), candidate.getOriginalName(),
                null, duplicate.getId(), IMPORT_RESULT_DUPLICATE, null, "文件内容已存在");
    }

    private ImageImportRespVO.Item failureItem(String clientFileId, String originalName, Exception ex) {
        String errorCode = "IMAGE_IMPORT_FAILED";
        String errorMessage = StrUtil.blankToDefault(ex.getMessage(), "图片导入失败");
        if (ex instanceof ServiceException serviceException) {
            errorCode = String.valueOf(serviceException.getCode());
        }
        return new ImageImportRespVO.Item(clientFileId, originalName, null, null,
                IMPORT_RESULT_FAILED, errorCode, StrUtil.maxLength(errorMessage, 500));
    }

    private void insertImportRecord(Long batchId, ImageImportRespVO.Item item, Long fileSize, String sha256) {
        imageImportRecordMapper.insert(new ImageImportRecordDO()
                .setBatchId(batchId)
                .setClientFileId(item.getClientFileId())
                .setOriginalName(item.getOriginalName())
                .setFileSize(fileSize)
                .setSha256(sha256)
                .setResult(item.getResult())
                .setAssetId(item.getAssetId())
                .setDuplicateAssetId(item.getDuplicateAssetId())
                .setErrorCode(item.getErrorCode())
                .setErrorMessage(item.getErrorMessage()));
    }

    private String buildOriginalDirectory(Long batchId) {
        return "drill-images/batch-" + batchId + "/original";
    }

    private String generateAssetNo() {
        return "AST-" + LocalDateTime.now().format(ASSET_NO_FORMATTER)
                + "-" + RandomUtil.randomNumbers(5);
    }

    @Data
    private static class Candidate {
        private String clientFileId;
        private String originalName;
        private byte[] content;
        private String sha256;
        private int width;
        private int height;
        private String mimeType;
        private String drillCode;
        private String drillType;
        private String sceneType;
        private String photoType;
        private String bladeNo;
        private String metadataStatus;
        private String remark;
    }

}
