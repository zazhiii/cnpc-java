package cn.iocoder.yudao.module.drill.service.enhance;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.drill.config.ImageEnhanceProperties;
import cn.iocoder.yudao.module.drill.controller.admin.enhance.vo.ImageAccessOverviewRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageCursorRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageItemRespVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageListReqVO;
import cn.iocoder.yudao.module.drill.controller.open.enhance.vo.OpenImageVersionRespVO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessPurposeCountDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessStatsDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageVersionDO;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageAccessMapper;
import cn.iocoder.yudao.module.drill.dal.mysql.enhance.ImageVersionMapper;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.drill.enums.EnhanceConstants.PUBLISH_PUBLISHED;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ACCESS_CONTENT_MISSING;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ACCESS_CURSOR_INVALID;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ACCESS_INVALID_PURPOSE;
import static cn.iocoder.yudao.module.drill.enums.ErrorCodeConstants.IMAGE_ACCESS_NOT_PUBLISHED;

@Service
public class ImageAccessServiceImpl implements ImageAccessService {

    private static final Set<String> PURPOSES = Set.of(
            "INFERENCE", "TRAINING", "EVALUATION", "MANUAL_VIEW");
    private static final List<String> SCOPES = List.of("drill:image:read", "drill:image:download");
    private static final String API_VERSION = "v1";

    @Resource
    private ImageAccessMapper imageAccessMapper;
    @Resource
    private ImageVersionMapper imageVersionMapper;
    @Resource
    private FileService fileService;
    @Resource
    private ImageEnhanceProperties properties;

    @Override
    public ImageAccessOverviewRespVO getOverview(String requestRootUrl) {
        ImageAccessStatsDO stats = imageAccessMapper.selectOverviewStats();
        List<ImageAccessPurposeCountDO> counts = imageAccessMapper.selectPurposeCounts();
        List<ImageAccessRecordDO> recent = imageAccessMapper.selectRecent(10);

        String rootUrl = resolveRootUrl(requestRootUrl);
        ImageAccessOverviewRespVO result = new ImageAccessOverviewRespVO();
        result.setPublicBaseUrl(rootUrl + "/open-api/" + API_VERSION);
        result.setTokenEndpoint(rootUrl + "/admin-api/system/oauth2/token");
        result.setApiVersion(API_VERSION);
        result.setDownloadMode("PROXY");
        result.setRequiredScopes(SCOPES);
        result.setPublishedAssetCount(stats == null || stats.getPublishedAssetCount() == null
                ? 0L : stats.getPublishedAssetCount());
        result.setPublishedDefaultCount(stats == null || stats.getPublishedDefaultCount() == null
                ? 0L : stats.getPublishedDefaultCount());
        result.setLatestPublishedAt(stats == null ? null : stats.getLatestPublishedAt());

        Map<String, Long> purposeCounts = new LinkedHashMap<>();
        PURPOSES.forEach(purpose -> purposeCounts.put(purpose, 0L));
        counts.forEach(item -> purposeCounts.put(item.getPurpose(), item.getCount()));
        result.setPurposeCounts(purposeCounts);

        List<ImageAccessOverviewRespVO.RecentVersion> recentVersions = new ArrayList<>(recent.size());
        for (ImageAccessRecordDO record : recent) {
            recentVersions.add(BeanUtils.toBean(record, ImageAccessOverviewRespVO.RecentVersion.class));
        }
        result.setRecentVersions(recentVersions);
        return result;
    }

    @Override
    public OpenImageCursorRespVO getPublishedImages(Long tenantId, OpenImageListReqVO reqVO) {
        validatePurpose(reqVO.getPurpose());
        Cursor cursor = decodeCursor(reqVO.getCursor());
        int limit = reqVO.getLimit() == null ? 100 : Math.min(reqVO.getLimit(), 500);
        List<ImageAccessRecordDO> records = withTenant(tenantId, () ->
                imageAccessMapper.selectPublishedDefaultList(
                        reqVO.getPurpose(), reqVO.getBatchId(), reqVO.getDrillCode(), reqVO.getPhotoType(),
                        reqVO.getUpdatedAfter(), cursor == null ? null : cursor.time(),
                        cursor == null ? null : cursor.id(), limit + 1));
        boolean hasMore = records.size() > limit;
        if (hasMore) {
            records = new ArrayList<>(records.subList(0, limit));
        }
        OpenImageCursorRespVO result = new OpenImageCursorRespVO();
        result.setItems(BeanUtils.toBean(records, OpenImageItemRespVO.class));
        result.setHasMore(hasMore);
        if (hasMore && !records.isEmpty()) {
            ImageAccessRecordDO last = records.get(records.size() - 1);
            result.setNextCursor(encodeCursor(last.getPublishedAt(), last.getPurposeRecordId()));
        }
        return result;
    }

    @Override
    public OpenImageVersionRespVO getPublishedVersion(Long tenantId, Long versionId, String purpose,
                                                      String requestRootUrl) {
        validatePurpose(purpose);
        ImageAccessRecordDO record = withTenant(tenantId,
                () -> imageAccessMapper.selectPublishedVersion(versionId, purpose));
        if (record == null) {
            throw exception(IMAGE_ACCESS_NOT_PUBLISHED);
        }
        OpenImageVersionRespVO result = new OpenImageVersionRespVO();
        result.setImage(BeanUtils.toBean(record, OpenImageItemRespVO.class));
        result.setStatus(PUBLISH_PUBLISHED);
        result.setContentUrl(resolvePublicApiBaseUrl(requestRootUrl)
                + "/drill-image-versions/" + versionId + "/content?purpose=" + purpose);
        return result;
    }

    @Override
    public ImageContent getOpenContent(Long tenantId, Long versionId, String purpose) {
        validatePurpose(purpose);
        return withTenant(tenantId, () -> {
            ImageAccessRecordDO record = imageAccessMapper.selectPublishedVersion(versionId, purpose);
            if (record == null) {
                throw exception(IMAGE_ACCESS_NOT_PUBLISHED);
            }
            return loadContent(imageVersionMapper.selectById(versionId));
        });
    }

    @Override
    public ImageContent getAdminContent(Long versionId) {
        ImageVersionDO version = imageVersionMapper.selectById(versionId);
        if (version == null || !PUBLISH_PUBLISHED.equals(version.getPublishStatus())) {
            throw exception(IMAGE_ACCESS_NOT_PUBLISHED);
        }
        return loadContent(version);
    }

    @Override
    public String resolvePublicApiBaseUrl(String requestRootUrl) {
        return resolveRootUrl(requestRootUrl) + "/open-api/" + API_VERSION;
    }

    private ImageContent loadContent(ImageVersionDO version) {
        if (version == null) {
            throw exception(IMAGE_ACCESS_NOT_PUBLISHED);
        }
        try {
            byte[] bytes = fileService.getFileContent(version.getFileConfigId(), version.getObjectKey());
            if (bytes == null) {
                throw exception(IMAGE_ACCESS_CONTENT_MISSING);
            }
            String extension = extension(version.getMimeType());
            return new ImageContent(bytes, version.getMimeType(), (long) bytes.length, version.getSha256(),
                    "enhanced-v" + version.getVersionNo() + extension);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(IMAGE_ACCESS_CONTENT_MISSING);
        }
    }

    private void validatePurpose(String purpose) {
        if (!PURPOSES.contains(purpose)) {
            throw exception(IMAGE_ACCESS_INVALID_PURPOSE, purpose);
        }
    }

    private Cursor decodeCursor(String encoded) {
        if (StrUtil.isBlank(encoded)) {
            return null;
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = value.split(":", 2);
            Instant instant = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            return new Cursor(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()), Long.parseLong(parts[1]));
        } catch (Exception ex) {
            throw exception(IMAGE_ACCESS_CURSOR_INVALID);
        }
    }

    private String encodeCursor(LocalDateTime time, Long id) {
        long epochMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String value = epochMillis + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveRootUrl(String requestRootUrl) {
        String configured = properties.getOpenApi().getPublicBaseUrl();
        return StrUtil.removeSuffix(StrUtil.blankToDefault(configured, requestRootUrl), "/");
    }

    private String extension(String mimeType) {
        if ("image/png".equalsIgnoreCase(mimeType)) {
            return ".png";
        }
        if ("image/webp".equalsIgnoreCase(mimeType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private <T> T withTenant(Long tenantId, Supplier<T> supplier) {
        if (tenantId == null || tenantId <= 0) {
            throw exception(IMAGE_ACCESS_NOT_PUBLISHED);
        }
        Long previousTenantId = TenantContextHolder.getTenantId();
        Boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(false);
            return supplier.get();
        } finally {
            TenantContextHolder.setTenantId(previousTenantId);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private record Cursor(LocalDateTime time, Long id) {
    }

}
