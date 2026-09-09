# 图片资产质检接口

图片必须先通过数据资产模块完成导入。质检接口只接收 `drill_image_asset.id`，服务端通过资产记录的 `file_config_id` 和 `object_key` 读取原始文件，不再接收 multipart 文件。

## 执行质检

- 地址：`POST /admin-api/drill/image-quality/check`
- Content-Type：`application/json`
- 鉴权：已登录用户

单图请求：

```json
{
  "assetId": 1001,
  "profileId": 10
}
```

多选请求（最多 20 张）：

```json
{
  "assetIds": [1001, 1002, 1003],
  "confidence": 0.8,
  "minDetections": 2
}
```

`assetId` 与 `assetIds` 必须且只能提供一个。`profileId` 省略时使用当前租户的默认启用方案。`confidence` 和 `minDetections` 仅适用于 E11，并覆盖方案中保存的同名参数。

响应始终使用批量包络，单图时 `items` 只有一项：

```json
{
  "code": 0,
  "data": {
    "totalCount": 1,
    "successCount": 1,
    "failedCount": 0,
    "items": [
      {
        "recordId": 88,
        "assetId": 1001,
        "profileId": 10,
        "profileCode": "E11_STANDARD",
        "profileVersion": "1.0.0",
        "modelCode": "E11",
        "executionStatus": "SUCCESS",
        "passed": true,
        "reason": "passed",
        "modelVersion": "E11",
        "threshold": 0.75,
        "minDetections": 1,
        "acceptedCount": 3,
        "candidateCount": 3,
        "durationMs": 1320,
        "effectiveConfig": {
          "confidence": 0.75,
          "minDetections": 1
        }
      }
    ]
  },
  "msg": ""
}
```

`passed=false` 是模型正常执行后的业务结论，记录状态仍为 `SUCCESS`。文件丢失、模型超时或推理异常会生成 `FAILED` 记录；多选请求会继续处理后续资产。

## 质检方案

方案管理根路径为 `/admin-api/drill/quality-profile`，提供 `list/get/create/update/copy/enable/disable/set-default/delete`。当前模型编码为 `E11`，配置结构如下：

```json
{
  "confidence": 0.75,
  "minDetections": 1
}
```

方案状态为 `DRAFT`、`ENABLED`、`DISABLED`。只有草稿可修改，只有启用方案可设为默认，默认方案不能停用或删除。

## 质检记录

- `GET /admin-api/drill/image-quality-record/page`：按 `assetId`、`profileId`、`modelCode`、`executionStatus`、`passed` 分页查询。
- `GET /admin-api/drill/image-quality-record/get?id=88`：查询完整记录。
- `GET /admin-api/drill/image-quality-record/latest?assetId=1001`：查询资产最新记录。

## E11 运行配置

先在 `e11_quality_gate_windows_20260804` 目录执行 `install.cmd`，再运行 `e11-server.cmd`。FastAPI 启动时只加载一次模型并完成预热；Spring Boot 通过 HTTP 调用常驻服务，不再逐图片启动 Python。

- `E11_GATE_BASE_URL`：FastAPI 根地址，默认 `http://127.0.0.1:18081`；
- `connect-timeout`：连接超时，默认 3 秒；
- `timeout`：上传和推理读取超时，默认 30 秒；
- `health-timeout`：readiness 超时，默认 2 秒；
- `max-concurrent`：Java 调用端并发保护，默认 1；
- `acquire-timeout`：Java 调用端排队超时，默认 5 秒。

Python 服务使用 `E11_MODEL_PATH`、`E11_MODEL_SHA256`、`E11_DEVICE=cpu`、`E11_MAX_CONCURRENT`、`E11_ACQUIRE_TIMEOUT`、`E11_MAX_IMAGE_BYTES` 和 `E11_MAX_PIXEL_COUNT` 配置。模型服务未启动或模型加载失败不会阻止 Spring Boot 启动；E11 请求会返回服务不可用并生成失败记录。

Python 服务接口：

- `GET /health/live`：进程存活；
- `GET /health/ready`：模型完整性校验、加载和预热均成功；
- `POST /v1/e11/predict`：内部 multipart 推理接口，仅允许 JPEG、PNG、WebP。
