# appointment-manager

从零实现的预约管理学习项目。先完成可部署、可验证的后端业务，再接入 Agent，让不同入口共用同一套预约规则。

目前采用 **模块化单体：按业务分包，模块内部轻量分层**。一个 Maven 工程构建一个应用，医生资料属于 `doctor` 模块，排班时段和预约属于 `appointment` 模块。详细的职责、依赖关系和阅读顺序见 [架构说明](docs/architecture.md)。

## 已实现的业务

- 创建、查询医生。
- 为医生发布未来的预约时段；拒绝时间重叠，允许首尾相接。
- 查询医生的时段及当前可用状态。
- 使用 `customerName + slotId` 创建预约；医生姓名和时间由服务器查询。
- 查询预约列表和详情。
- 取消预约并保留记录；重复取消同一预约返回相同结果。
- 取消后允许重新预约；再次取消旧预约不会释放新预约。
- 事务中的行锁协调并发请求，数据库唯一索引限制同一时段只有一条有效预约。

一个时段接待一位用户。Agent 接入属于后续学习内容，目前没有模型调用功能。

## 技术与运行方式

| 部分 | 使用的技术 |
| --- | --- |
| 语言与构建 | Java 21、Maven |
| HTTP 与校验 | Spring Boot 3.5.16、Spring Web、Bean Validation |
| 数据库访问 | Spring Data JPA、Hibernate、MySQL 8.4 |
| 表结构管理 | Flyway；Hibernate 使用 `ddl-auto=validate` 校验映射 |
| 持续集成 | GitHub Actions，使用真实 MySQL 执行测试并生成 JAR |
| 部署 | Docker 多阶段构建、Docker Compose |
| 部署验证 | Python 3 标准库脚本，验证取消、重新预约和应用重启后的持久化 |

当前学习流程是在本地编辑代码，推送到 `main` 后由 GitHub Actions 编译和测试，再在 Ubuntu 服务器更新并部署。该流程不要求本地安装 Java、Maven 或 MySQL。

CI 与部署是两步：当前工作流不会自动更新服务器。Docker 镜像构建使用 `-DskipTests`，部署前先确认对应提交的 GitHub Actions 检查通过。测试结果以实际构建日志为准。

## API

| 方法 | 路径 | 用途 | 成功状态 |
| --- | --- | --- | --- |
| POST | `/api/doctors` | 创建医生 | 201 |
| GET | `/api/doctors` | 查询医生列表 | 200 |
| GET | `/api/doctors/{id}` | 查询医生详情 | 200 |
| POST | `/api/doctors/{doctorId}/slots` | 发布预约时段 | 201 |
| GET | `/api/doctors/{doctorId}/slots` | 查询时段及可用状态 | 200 |
| POST | `/api/appointments` | 创建预约 | 201 |
| GET | `/api/appointments` | 查询预约列表 | 200 |
| GET | `/api/appointments/{id}` | 查询预约详情 | 200 |
| POST | `/api/appointments/{id}/cancel` | 取消预约 | 200 |

创建预约的请求体为：

```json
{
  "customerName": "张三",
  "slotId": 12
}
```

其中 `slotId` 必须替换为服务器返回的真实时段 ID。成功响应包含 `id`、`slotId`、`customerName`、`doctorName`、`startTime`、`endTime`、`status`。时间按 UTC 返回；请求时间可携带时区偏移。

时段列表的 `available` 是查询当时计算出的结果。创建预约时，应用会在事务内再次检查时段是否开始、是否被占用。

参数与业务错误响应使用 ProblemDetail，并提供 `code` 供调用方判断错误类型；具体错误说明使用中文。

| 情况 | HTTP 状态 | `code` |
| --- | --- | --- |
| 非法输入，包括请求格式校验失败 | 400 | `INVALID_ARGUMENT` |
| 资源不存在 | 404 | `NOT_FOUND` |
| 时段重叠或已被占用 | 409 | `CONFLICT` |

### 在服务器尝试完整流程

应用启动后，在服务器终端运行下面的示例。它会生成明天的时段并创建真实测试记录，避免固定日期过期。

```bash
python3 - <<'PY'
import json
from datetime import datetime, timedelta, timezone
from urllib.request import Request, urlopen

base_url = "http://127.0.0.1:8080"

def post(path, body):
    request = Request(
        base_url + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=10) as response:
        return json.load(response)

doctor = post("/api/doctors", {"name": "示例医生"})
start = (datetime.now(timezone.utc) + timedelta(days=1)).replace(microsecond=0)
slot = post(f"/api/doctors/{doctor['id']}/slots", {
    "startTime": start.isoformat(),
    "endTime": (start + timedelta(minutes=30)).isoformat(),
})
appointment = post("/api/appointments", {
    "customerName": "示例用户",
    "slotId": slot["id"],
})
print(json.dumps(appointment, ensure_ascii=False, indent=2))
print(f"预约详情：{base_url}/api/appointments/{appointment['id']}")
PY
```

完整的取消、重新预约和重启检查使用下面的部署验证脚本。

## 在 Ubuntu 服务器部署

当前服务器环境为 Ubuntu 24.04，已安装 Docker 与 Docker Compose。进入 **workspace 下已有的 appointment-manager 项目目录** 执行以下操作。路径以服务器上的实际目录为准。

### 首次配置数据库密码

Compose 需要项目根目录的 `.env`，包含 `.env.example` 中的两个键：

```dotenv
MYSQL_ROOT_PASSWORD=
DB_PASSWORD=
```

首次部署且还没有 `.env` 时，可以运行以下命令生成两个不同的随机密码。密码写入文件，不打印到终端；已有文件时命令会拒绝覆盖。

```bash
python3 - <<'PY'
import os
import secrets

flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
descriptor = os.open(".env", flags, 0o600)
with os.fdopen(descriptor, "w", encoding="utf-8") as output:
    output.write(f"MYSQL_ROOT_PASSWORD={secrets.token_hex(32)}\n")
    output.write(f"DB_PASSWORD={secrets.token_hex(32)}\n")
print("已创建 .env。")
PY
```

已有部署继续使用原 `.env`。MySQL 已初始化的密码需要通过数据库操作修改，单独改 `.env` 不会修改已有数据库用户密码。`.env` 已被 Git 忽略，不应提交。

### 更新与启动

对应提交的 CI 检查通过后执行：

```bash
git pull --ff-only
sudo docker compose up -d --build
sudo docker compose ps
```

应用启动时由 Flyway 按顺序执行尚未执行的迁移，再由 Hibernate 校验表结构。已执行的迁移保持原样，后续表结构修改添加新的版本文件。

Compose 将应用绑定到服务器的 `127.0.0.1:8080`，可以在服务器终端访问。数据库数据保存在 `mysql-data` 命名卷中。

查看应用日志：

```bash
sudo docker compose logs --tail=100 app
```

### 验证部署和数据持久化

```bash
sudo python3 scripts/verify_persistence.py
```

[验证脚本](scripts/verify_persistence.py) 会创建医生和时段、预约、取消、重新预约，再重启 `app` 服务，检查取消历史与新预约是否仍存在。脚本会保留这些测试记录，只重启应用服务。

## 验证代码

[GitHub Actions 工作流](.github/workflows/build.yml) 在推送到 `main` 时执行：

```bash
mvn --batch-mode --no-transfer-progress package
```

工作流提供 MySQL 8.4 测试数据库，以及 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量。集成测试通过真实 HTTP 与数据库检查预约流程、并发占用和数据库约束；单元测试检查业务输入和模型规则。[ArchUnit 架构测试](src/test/java/com/op1ed/appointmentmanager/architecture/ArchitectureTest.java) 分析编译后的类，检查跨模块依赖与业务层对 HTTP 的依赖边界。ArchUnit 仅作为测试依赖，不参与应用运行。

如果自行在其他环境运行 Maven，同样需要 Java 21、Maven、可访问的 MySQL，以及上述三个数据库环境变量。具体测试数量和是否通过，以该次 Maven 输出为准。
