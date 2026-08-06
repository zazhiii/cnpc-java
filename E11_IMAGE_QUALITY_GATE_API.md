# E11 图片质量检测接口

## 接口

- 方法：`POST`
- 地址：`/admin-api/drill/image-quality/check`
- Content-Type：`multipart/form-data`
- 鉴权：沿用管理端登录 Token，Header 为 `Authorization: Bearer <token>`

表单字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `file` | file | 是 | - | JPEG、PNG 或 WebP，最大 30 MB、5000 万像素 |
| `confidence` | number | 否 | `0.75` | E11 置信度阈值，范围 `[0, 1]` |
| `minDetections` | integer | 否 | `1` | 合格所需的最少有效切削齿数量，范围 `[1, 300]` |

调用示例：

```bash
curl -X POST "http://localhost:48080/admin-api/drill/image-quality/check" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@sample.jpg" \
  -F "confidence=0.75" \
  -F "minDetections=1"
```

## 正常响应

模型通过（退出码 `0`）和模型拒绝（退出码 `10`）均返回统一成功响应；调用方读取 `data.passed` 判断图片是否合格。

```json
{
  "code": 0,
  "data": {
    "passed": true,
    "reason": "passed",
    "modelVersion": "E11",
    "threshold": 0.75,
    "minDetections": 1,
    "acceptedCount": 2,
    "candidateCount": 3,
    "rejectionCounts": {
      "below_threshold": 1,
      "invalid_polygon": 0,
      "unexpected_label": 0
    },
    "confidenceSummary": {
      "minimum": 0.82,
      "maximum": 0.94,
      "mean": 0.88
    },
    "image": {"width": 1920, "height": 1080},
    "detections": [
      {
        "label": "CUTTING_TOOTH",
        "confidence": 0.94,
        "polygon": [[123.4, 56.7], [180.0, 62.1], [170.2, 130.9]]
      }
    ],
    "inferenceSpeedMs": {"preprocess": 4.2, "inference": 86.3, "postprocess": 5.1},
    "durationMs": 536
  },
  "msg": ""
}
```

服务不会向客户端返回模型路径或临时图片路径。模型不可用、超时、并发繁忙、参数错误或输出不符合契约时，返回 `1050000070` 至 `1050000077` 的业务错误码。

## 部署配置

先安装 Python 3.10 或更高版本，再在 `e11_quality_gate_windows_20260804` 目录执行 `install.cmd`（交付包固定的 `onnxruntime==1.28.0` 不支持当前机器上的 Python 3.9）。默认配置适用于从 `yudao-boot-mini` 目录启动服务，也可通过环境变量覆盖：

- `E11_GATE_PYTHON`：虚拟环境 Python 的绝对路径；
- `E11_GATE_SCRIPT`：`e11_quality_gate.py` 的绝对路径；
- `E11_GATE_MODEL`：`e11.onnx` 的绝对路径；
- `E11_GATE_DEVICE`：默认 `cpu`。

超时、并发数和排队等待时间在 `application.yaml` 的 `drill.e11-quality-gate` 下配置。
