
-- ============================================
-- 1. 预测记录表
-- ============================================

CREATE TABLE drill_model_prediction_record (
                                   id BIGINT NOT NULL AUTO_INCREMENT COMMENT '预测记录主键',

                                   blade_image_id BIGINT NOT NULL COMMENT '刀翼图片ID',

                                   model_name VARCHAR(64) NOT NULL COMMENT '模型名称',
                                   model_version VARCHAR(64) NOT NULL COMMENT '模型版本',

                                   status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                                       COMMENT '预测状态：PENDING/RUNNING/SUCCESS/FAILED',

                                   error_message TEXT DEFAULT NULL COMMENT '预测失败原因',

                                   started_at DATETIME(3) DEFAULT NULL COMMENT '预测开始时间',
                                   finished_at DATETIME(3) DEFAULT NULL COMMENT '预测完成时间',

                                   created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

                                   PRIMARY KEY (id),

                                   KEY idx_blade_image_id (blade_image_id),
                                   KEY idx_status (status),
                                   KEY idx_created_at (created_at)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '刀翼图片预测记录表';


-- ============================================
-- 2. 齿预测结果表
-- ============================================

CREATE TABLE drill_model_tooth_prediction (
                                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',

                                  prediction_id BIGINT NOT NULL COMMENT '预测记录ID',

                                  tooth_index INT NOT NULL COMMENT '本次预测中的齿序号',

                                  polygon JSON NOT NULL COMMENT '齿的多边形轮廓坐标',

                                  wear_type VARCHAR(32) DEFAULT NULL COMMENT '磨损类型编码',
                                  wear_level VARCHAR(32) DEFAULT NULL COMMENT '磨损等级编码',

                                  detection_confidence DECIMAL(5,4) DEFAULT NULL
                                      COMMENT '齿检测置信度',

                                  type_confidence DECIMAL(5,4) DEFAULT NULL
                                      COMMENT '磨损类型置信度',

                                  level_confidence DECIMAL(5,4) DEFAULT NULL
                                      COMMENT '磨损等级置信度',

                                  created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

                                  PRIMARY KEY (id),

                                  UNIQUE KEY uk_prediction_tooth (prediction_id, tooth_index),

                                  CONSTRAINT fk_tooth_drill_model_prediction_record
                                      FOREIGN KEY (prediction_id)
                                          REFERENCES drill_model_prediction_record (id)
                                          ON DELETE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '齿预测结果表';



INSERT INTO drill_model_prediction_record (
    id, blade_image_id, model_name, model_version,
    status, error_message, started_at, finished_at
) VALUES
      (
          1, 101, 'PDC-Wear-YOLO', 'v1.0.0',
          'SUCCESS', NULL,
          '2026-11-11 09:00:00.000',
          '2026-11-11 09:00:03.250'
      ),
      (
          2, 102, 'PDC-Wear-YOLO', 'v1.0.0',
          'SUCCESS', NULL,
          '2026-11-11 09:05:00.000',
          '2026-11-11 09:05:04.120'
      ),
      (
          3, 103, 'PDC-Wear-YOLO', 'v1.0.0',
          'FAILED', '模型推理失败：图片格式异常',
          '2026-11-11 09:10:00.000',
          '2026-11-11 09:10:01.500'
      ),
      (
          4, 101, 'PDC-Wear-YOLO', 'v1.1.0',
          'SUCCESS', NULL,
          '2026-11-11 09:20:00.000',
          '2026-11-11 09:20:02.870'
      );

INSERT INTO drill_model_tooth_prediction (
    prediction_id,
    tooth_index,
    polygon,
    wear_type,
    wear_level,
    detection_confidence,
    type_confidence,
    level_confidence
) VALUES

-- ============================================
-- 预测记录1：刀翼图片101，模型v1.0.0
-- 识别出4颗齿
-- ============================================

(
    1, 1,
    '[[120,150],[160,145],[175,180],[155,210],[118,195]]',
    'NORMAL', 'NONE',
    0.9850, 0.9620, 0.9780
),
(
    1, 2,
    '[[220,160],[260,155],[280,190],[255,220],[215,205]]',
    'ABRASION', 'MILD',
    0.9720, 0.9130, 0.8950
),
(
    1, 3,
    '[[320,180],[360,175],[380,215],[350,240],[315,220]]',
    'CHIPPING', 'MODERATE',
    0.9560, 0.8870, 0.9020
),
(
    1, 4,
    '[[420,200],[460,195],[475,230],[450,260],[415,240]]',
    'FRACTURE', 'SEVERE',
    0.9410, 0.9250, 0.9180
),

-- ============================================
-- 预测记录2：刀翼图片102，模型v1.0.0
-- 识别出3颗齿
-- ============================================

(
    2, 1,
    '[[100,120],[145,115],[160,150],[135,180],[95,165]]',
    'NORMAL', 'NONE',
    0.9910, 0.9750, 0.9810
),
(
    2, 2,
    '[[200,140],[245,135],[265,175],[235,200],[195,180]]',
    'ABRASION', 'MODERATE',
    0.9680, 0.9210, 0.9060
),
(
    2, 3,
    '[[310,160],[355,155],[375,190],[345,220],[305,200]]',
    'CHIPPING', 'MILD',
    0.9520, 0.8940, 0.9120
),

-- ============================================
-- 预测记录4：刀翼图片101，模型v1.1.0
-- 同一图片使用新版本模型重新预测
-- ============================================

(
    4, 1,
    '[[122,148],[162,144],[178,182],[156,212],[119,197]]',
    'NORMAL', 'NONE',
    0.9920, 0.9780, 0.9850
),
(
    4, 2,
    '[[218,158],[262,153],[282,192],[257,222],[213,207]]',
    'ABRASION', 'MILD',
    0.9860, 0.9450, 0.9320
),
(
    4, 3,
    '[[318,178],[362,173],[382,217],[352,242],[313,222]]',
    'CHIPPING', 'MODERATE',
    0.9790, 0.9280, 0.9410
),
(
    4, 4,
    '[[418,198],[462,193],[477,232],[452,262],[413,242]]',
    'FRACTURE', 'SEVERE',
    0.9650, 0.9530, 0.9470
);
