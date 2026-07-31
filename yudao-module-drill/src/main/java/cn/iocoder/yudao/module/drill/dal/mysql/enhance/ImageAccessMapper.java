package cn.iocoder.yudao.module.drill.dal.mysql.enhance;

import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessPurposeCountDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessRecordDO;
import cn.iocoder.yudao.module.drill.dal.dataobject.enhance.ImageAccessStatsDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 增强图片开放访问聚合查询。
 */
@Mapper
public interface ImageAccessMapper {

    String RECORD_COLUMNS = """
            p.id AS purpose_record_id,
            a.id AS asset_id,
            a.asset_no,
            a.batch_id,
            a.drill_code,
            a.drill_type,
            a.scene_type,
            a.photo_type,
            a.blade_no,
            v.id AS image_version_id,
            v.version_no,
            p.purpose,
            p.default_version,
            v.width,
            v.height,
            v.mime_type,
            v.file_size,
            v.sha256,
            v.profile_code,
            v.profile_version,
            p.update_time AS published_at
            """;

    String RECORD_TABLES = """
            FROM drill_image_version_purpose p
            INNER JOIN drill_image_version v ON v.id = p.image_version_id AND v.deleted = 0
            INNER JOIN drill_image_asset a ON a.id = p.asset_id AND a.deleted = 0
            """;

    @Select("""
            <script>
            SELECT
            """ + RECORD_COLUMNS + RECORD_TABLES + """
            WHERE p.deleted = 0
              AND p.publish_status = 'PUBLISHED'
              AND p.default_version = 1
              AND p.purpose = #{purpose}
            <if test="batchId != null">
              AND a.batch_id = #{batchId}
            </if>
            <if test="drillCode != null and drillCode != ''">
              AND a.drill_code = #{drillCode}
            </if>
            <if test="photoType != null and photoType != ''">
              AND a.photo_type = #{photoType}
            </if>
            <if test="updatedAfter != null">
              AND p.update_time &gt;= #{updatedAfter}
            </if>
            <if test="cursorTime != null">
              AND (p.update_time &gt; #{cursorTime}
                OR (p.update_time = #{cursorTime} AND p.id &gt; #{cursorId}))
            </if>
            ORDER BY p.update_time ASC, p.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<ImageAccessRecordDO> selectPublishedDefaultList(
            @Param("purpose") String purpose,
            @Param("batchId") Long batchId,
            @Param("drillCode") String drillCode,
            @Param("photoType") String photoType,
            @Param("updatedAfter") LocalDateTime updatedAfter,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            @Param("limit") Integer limit);

    @Select("""
            SELECT
            """ + RECORD_COLUMNS + RECORD_TABLES + """
            WHERE p.deleted = 0
              AND p.publish_status = 'PUBLISHED'
              AND p.image_version_id = #{versionId}
              AND p.purpose = #{purpose}
            LIMIT 1
            """)
    ImageAccessRecordDO selectPublishedVersion(@Param("versionId") Long versionId,
                                               @Param("purpose") String purpose);

    @Select("""
            SELECT COUNT(DISTINCT p.asset_id) AS published_asset_count,
                   COUNT(*) AS published_default_count,
                   MAX(p.update_time) AS latest_published_at
            FROM drill_image_version_purpose p
            INNER JOIN drill_image_version v ON v.id = p.image_version_id AND v.deleted = 0
            INNER JOIN drill_image_asset a ON a.id = p.asset_id AND a.deleted = 0
            WHERE p.deleted = 0
              AND p.publish_status = 'PUBLISHED'
              AND p.default_version = 1
            """)
    ImageAccessStatsDO selectOverviewStats();

    @Select("""
            SELECT p.purpose, COUNT(*) AS count
            FROM drill_image_version_purpose p
            INNER JOIN drill_image_version v ON v.id = p.image_version_id AND v.deleted = 0
            INNER JOIN drill_image_asset a ON a.id = p.asset_id AND a.deleted = 0
            WHERE p.deleted = 0
              AND p.publish_status = 'PUBLISHED'
              AND p.default_version = 1
            GROUP BY p.purpose
            """)
    List<ImageAccessPurposeCountDO> selectPurposeCounts();

    @Select("""
            SELECT
            """ + RECORD_COLUMNS + RECORD_TABLES + """
            WHERE p.deleted = 0
              AND p.publish_status = 'PUBLISHED'
              AND p.default_version = 1
            ORDER BY p.update_time DESC, p.id DESC
            LIMIT #{limit}
            """)
    List<ImageAccessRecordDO> selectRecent(@Param("limit") Integer limit);

}
