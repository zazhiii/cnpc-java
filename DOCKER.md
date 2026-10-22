# 构建后端 Docker 镜像

在本目录执行（需要 JDK 17 或更高版本、Maven 和已启动的 Docker Engine）：

```sh
mvn -pl yudao-server -am -DskipTests package
docker build -t cnpcdrill-java:latest ./yudao-server
```

第一条命令会构建多模块项目，并生成 `yudao-server/target/yudao-server.jar`。第二条命令使用 `yudao-server/Dockerfile` 将这个 JAR 打入镜像。镜像只包含后端服务，前端、MySQL 和 Redis 需要单独部署。

启动示例（`application-local.yaml` 监听 10616 端口）：

```sh
docker run -d --name cnpcdrill-java --restart unless-stopped \
  -p 10616:10616 \
  -e SPRING_PROFILES_ACTIVE=local \
  cnpcdrill-java:latest
docker logs -f cnpcdrill-java
```

部署前，检查所选 profile 的 MySQL、Redis 和其他外部服务地址。容器内的 `127.0.0.1` 是容器自身；可通过环境变量覆盖 Spring 配置（例如 `SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL`、`SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME`、`SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD`、`SPRING_DATA_REDIS_HOST`）。生产环境不要直接使用仓库内的示例凭据。

## 与 Windows E11 服务一起部署

当前 E11 是独立的 HTTP 服务。后端读取图片后通过 multipart 请求 E11，因此两者无需共享文件目录。

若 Docker Desktop 和 E11 Windows 服务运行在同一台 Windows 主机：

1. 为 Windows 服务设置 `E11_HOST=0.0.0.0`，使其监听容器能够访问的主机地址；保持 `E11_PORT=18081`。重启服务后，确认 `http://127.0.0.1:18081/health/ready` 返回就绪。仅修改终端中的环境变量不会改变已安装服务的环境。
2. 在 Windows 防火墙中将 TCP 18081 的入站访问限制到 Docker Desktop 所用的私有网络或后端所在主机，不要把未鉴权的推理接口开放到公网。
3. 启动后端时额外设置 `E11_GATE_BASE_URL=http://host.docker.internal:18081`。例如在 PowerShell 中：

```powershell
docker run -d --name cnpcdrill-java --restart unless-stopped -p 10616:10616 -e SPRING_PROFILES_ACTIVE=local -e E11_GATE_BASE_URL=http://host.docker.internal:18081 cnpcdrill-java:latest
```

容器到 E11 的连通性可用临时 curl 容器检查：

```powershell
docker run --rm curlimages/curl:latest -fsS http://host.docker.internal:18081/health/ready
```

若 E11 运行在另一台 Windows 主机，将 `E11_GATE_BASE_URL` 改为其私有网络地址或内部 DNS 名称，并只允许后端主机访问 18081。后端启动不要求 E11 已就绪，但 E11 不可用时质检会失败并记录失败结果。生产环境若希望统一编排，可进一步把 E11 Python 服务和模型制作成独立 Linux 镜像，保留单 worker、模型校验和就绪检查，通过容器服务名访问。
