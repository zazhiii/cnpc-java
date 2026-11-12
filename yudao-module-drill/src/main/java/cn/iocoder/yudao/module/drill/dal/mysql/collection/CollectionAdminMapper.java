package cn.iocoder.yudao.module.drill.dal.mysql.collection;

import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.PredictionSummary;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.TaskSummary;
import cn.iocoder.yudao.module.drill.controller.admin.collection.vo.CollectionAdminVO.Tooth;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface CollectionAdminMapper {

    @Select("""
            <script>
            SELECT t.id, t.task_no, t.task_type, t.status, t.user_id, t.update_time,
                   f.drill_code, f.drill_model, f.well_number
            FROM drill_collection_task t
            LEFT JOIN drill_collection_task_form f ON f.id = (
                SELECT MAX(current_form.id)
                FROM drill_collection_task_form current_form
                WHERE current_form.task_id = t.id AND current_form.deleted = 0
            )
            WHERE t.deleted = 0
            <if test="keyword != null">
              AND (t.task_no LIKE CONCAT('%', #{keyword}, '%')
                OR f.drill_code LIKE CONCAT('%', #{keyword}, '%')
                OR f.well_number LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="taskType != null and taskType != ''">AND t.task_type = #{taskType}</if>
            <if test="status != null and status != ''">AND t.status = #{status}</if>
            <if test="updatedFrom != null">AND t.update_time &gt;= #{updatedFrom}</if>
            <if test="updatedBefore != null">AND t.update_time &lt; #{updatedBefore}</if>
            ORDER BY t.update_time DESC, t.id DESC
            </script>
            """)
    IPage<TaskSummary> selectTaskPage(IPage<TaskSummary> page, @Param("keyword") String keyword,
                                      @Param("taskType") String taskType, @Param("status") String status,
                                      @Param("updatedFrom") LocalDateTime updatedFrom,
                                      @Param("updatedBefore") LocalDateTime updatedBefore);

    @Select("""
            SELECT id, model_name, model_version, status, started_at, finished_at, created_at
            FROM drill_model_prediction_record
            WHERE blade_image_id = #{photoId}
            ORDER BY created_at DESC, id DESC
            """)
    IPage<PredictionSummary> selectPredictionPage(IPage<PredictionSummary> page, @Param("photoId") Long photoId);

    @Select("""
            SELECT id, model_name, model_version, status, error_message, started_at, finished_at, created_at
            FROM drill_model_prediction_record
            WHERE blade_image_id = #{photoId} AND id = #{predictionId}
            """)
    PredictionSummary selectPrediction(@Param("photoId") Long photoId, @Param("predictionId") Long predictionId);

    @Select("""
            SELECT tooth_index, wear_type, wear_level, detection_confidence, type_confidence, level_confidence
            FROM drill_model_tooth_prediction
            WHERE prediction_id = #{predictionId}
            ORDER BY tooth_index ASC
            """)
    List<Tooth> selectTeeth(@Param("predictionId") Long predictionId);
}
