# appointment-manager

从零实现的预约管理学习项目。先完成可部署、可验证的后端业务，再接入 Agent，让不同入口共用同一套预约规则。

目前采用 **模块化单体：按业务分包，模块内部轻量分层**。一个 Maven 工程构建一个应用，账号和登录属于 `identity` 模块，医生资料属于 `doctor` 模块，排班时段和预约属于 `appointment` 模块，工具调用和待确认动作属于 `agent` 模块。详细的职责、依赖关系和阅读顺序见 [架构说明](docs/architecture.md)。

## 已实现的业务

- 注册普通用户，登录取得访问令牌，查询当前身份，退出并撤销当前令牌。
- 管理员创建医生、发布时段；医生和时段查询公开。
- 为医生发布未来的预约时段；拒绝时间重叠，允许首尾相接。
- 查询医生的时段及当前可用状态。
- 使用 `customerName + slotId` 创建预约；医生姓名和时间由服务器查询。
- 普通用户分页查询、查看和取消自己的预约；管理员可以管理全部预约。
- 按状态、预约人姓名和开始时间筛选，记录与总数都受账号权限限制。
- 取消预约并保留记录；重复取消同一预约返回相同结果。
- 取消后允许重新预约；再次取消旧预约不会释放新预约。
- 将有效的未来预约改到另一个空闲时段，保留预约 ID 与账号归属；失败时保留原预约。
- 分页查询创建、改期和取消的操作记录；业务变更与记录在同一事务中保存。
- 事务中的行锁协调并发请求，数据库唯一索引限制同一时段只有一条有效预约。
- 九个 Agent 工具：分页查询医生、空闲时段、预约和操作记录，生成预约、取消、改期预览。
- 动作预览保存到数据库，默认五分钟有效；只有生成动作的账号可以读取和确认。
- 确认时复用业务服务；重复确认返回首次结果，预约、操作记录和确认结果在同一事务中保存。
- DeepSeek 多轮对话与受限工具调用循环；生成动作预览后立即停止，用户通过独立接口确认。
- 会话和完整轮次保存到数据库，同一会话中的请求 ID 可重放已保存结果；历史按完整轮次裁剪。
- 模型连接超时、模型与工具调用次数、上下文大小、查询结果大小和并发请求均有限制。

一个时段接待一位用户。已实现 DeepSeek HTTP 适配器和自然语言对话入口；默认关闭真实模型调用，配置 API Key 并启用后才能使用。CI 用本地模拟模型服务验证，不调用收费 API。工具参数和确认规则见 [Agent 工具与动作确认](docs/agent-tools.md)，启用方法和会话流程见 [DeepSeek 对话说明](docs/deepseek-chat.md)。

## 技术与运行方式

| 部分 | 使用的技术 |
| --- | --- |
| 语言与构建 | Java 21、Maven |
| HTTP 与校验 | Spring Boot 3.5.16、Spring Web、Bean Validation |
| 身份与权限 | Spring Security、BCrypt 密码散列、数据库访问令牌、预约归属校验 |
| 数据库访问 | Spring Data JPA、Hibernate、MySQL 8.4 |
| 表结构管理 | Flyway；Hibernate 使用 `ddl-auto=validate` 校验映射 |
| 持续集成 | GitHub Actions，使用真实 MySQL 执行测试并生成 JAR |
| 部署 | Docker 多阶段构建、Docker Compose |
| 部署验证 | Python 3 标准库脚本，验证业务数据、操作记录和 Agent 动作的重启持久化 |
| Agent | DeepSeek Chat Completions、应用层模型接口、受限工具循环、持久化会话与请求结果 |

当前学习流程是在本地编辑代码，推送到 `main` 后由 GitHub Actions 编译和测试，再在 Ubuntu 服务器更新并部署。该流程不要求本地安装 Java、Maven 或 MySQL。

CI 与部署是两步：当前工作流不会自动更新服务器。Docker 镜像构建使用 `-DskipTests`，部署前先确认对应提交的 GitHub Actions 检查通过。测试结果以实际构建日志为准。

## API

| 方法 | 路径 | 用途 | 权限 | 成功状态 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册普通用户 | 公开 | 201 |
| POST | `/api/auth/login` | 登录取得访问令牌 | 公开 | 200 |
| GET | `/api/auth/me` | 查询当前身份 | 登录 | 200 |
| POST | `/api/auth/logout` | 撤销当前令牌 | 登录 | 204 |
| POST | `/api/doctors` | 创建医生 | 管理员 | 201 |
| GET | `/api/doctors` | 查询医生列表 | 公开 | 200 |
| GET | `/api/doctors/{id}` | 查询医生详情 | 公开 | 200 |
| POST | `/api/doctors/{doctorId}/slots` | 发布预约时段 | 管理员 | 201 |
| GET | `/api/doctors/{doctorId}/slots` | 查询时段及可用状态 | 公开 | 200 |
| POST | `/api/appointments` | 创建预约，绑定当前账号 | 登录 | 201 |
| GET | `/api/appointments` | 分页查询与筛选预约 | 本人 / 管理员 | 200 |
| GET | `/api/appointments/{id}` | 查询预约详情 | 本人 / 管理员 | 200 |
| POST | `/api/appointments/{id}/cancel` | 取消预约 | 本人 / 管理员 | 200 |
| POST | `/api/appointments/{id}/reschedule` | 改到目标时段，保留预约 ID | 本人 / 管理员 | 200 |
| GET | `/api/appointments/{id}/history` | 分页查询操作记录 | 本人 / 管理员 | 200 |
| GET | `/api/agent/tools` | 查询模型函数工具定义 | 登录 | 200 |
| POST | `/api/agent/tools/execute` | 执行查询或生成动作预览 | 登录，沿用业务权限 | 200 |
| GET | `/api/agent/actions/{id}` | 读取已保存的动作预览和结果 | 生成动作的账号 | 200 |
| POST | `/api/agent/actions/{id}/confirm` | 用户确认执行保存的动作 | 生成动作的账号 | 200 |
| POST | `/api/agent/conversations` | 创建空会话，不调用模型 | 登录 | 201 |
| GET | `/api/agent/conversations/{id}` | 查看会话和最近十轮 | 会话创建账号 | 200 |
| POST | `/api/agent/conversations/{id}/messages` | 发送消息，按请求 ID 重放结果 | 会话创建账号 | 200 / 502 / 504 |

注册请求包含 `username`、`displayName`、`password`；登录请求包含 `username`、`password`。用户名去首尾空格、转换小写，允许 3～50 位英文字母、数字、下划线和连字符。密码至少 8 个字符，UTF-8 编码后最多 72 字节，保留原始空格和大小写。

登录返回 `accessToken`、`tokenType=Bearer`、UTC `expiresAt` 和 `user`。受保护接口使用 `Authorization: Bearer <accessToken>`。令牌默认有效 24 小时，数据库只保存令牌散列；应用重启不会清空登录，退出只撤销当前令牌。这里不使用登录 Cookie 或 HTTP Session。详细流程与取舍见 [身份与权限说明](docs/identity-and-permissions.md)。

### 在服务器启用对话

在现有 Ubuntu 项目目录中，先确认对应提交的 CI 通过，再执行：

```bash
git pull --ff-only
python3 scripts/configure_deepseek.py
sudo docker compose up -d --build app
python3 scripts/chat.py
```

配置脚本隐藏 API Key 的输入，保留 `.env` 中的数据库和管理员配置；不要把 Key 发到聊天或提交到 Git。重建应用容器后，新环境变量才会生效。对话脚本用普通账号或管理员账号登录，支持多轮交流、查看历史和显式确认；发送新消息可能产生 DeepSeek API 费用。代码和模拟测试已验证，真实账号的模型调用需要配置后在服务器验证。

如果当前不启用模型，原有业务接口、工具接口和动作确认仍可使用。`scripts/verify_persistence.py` 不会调用模型 API。

创建预约的请求体为：

```json
{
  "customerName": "张三",
  "slotId": 12
}
```

其中 `slotId` 必须替换为服务器返回的真实时段 ID。创建、详情和取消接口的预约对象包含 `id`、`slotId`、`customerName`、`doctorName`、`startTime`、`endTime`、`status`。时间按 UTC 返回；请求时间可携带时区偏移。

`customerName` 是预约资料，可以填写代约人的姓名；它不决定权限。`owner_id` 由服务器从登录身份填写，不能通过请求里的账号字段指定。普通用户访问他人的预约统一返回 404。V4 之前没有归属账号的历史记录只向管理员开放。

时段列表的 `available` 是查询当时计算出的结果。创建预约时，应用会在事务内再次检查时段是否开始、是否被占用。

参数与业务错误响应使用 ProblemDetail，并提供 `code` 供调用方判断错误类型；具体错误说明使用中文。

| 情况 | HTTP 状态 | `code` |
| --- | --- | --- |
| 非法输入，包括请求格式校验失败 | 400 | `INVALID_ARGUMENT` |
| 未登录、令牌无效或过期、登录凭证错误 | 401 | `UNAUTHENTICATED` |
| 普通用户尝试管理员操作 | 403 | `FORBIDDEN` |
| 资源不存在 | 404 | `NOT_FOUND` |
| 时段重叠、已被占用或预约当前状态不允许改期 | 409 | `CONFLICT` |

### 预约改期与操作记录

`POST /api/appointments/{id}/reschedule` 使用当前查询到的时段 ID 与目标时段 ID：

```json
{
  "expectedSlotId": 12,
  "targetSlotId": 18
}
```

只有尚未开始的 `BOOKED` 预约可以真正改期，目标时段必须存在、尚未开始且空闲，可以属于另一位医生。改期保留预约 ID、预约人姓名与账号归属，更新医生和时段信息；原时段释放、新时段占用在同一事务中完成。目标被占用、输入过期或操作记录保存失败时，原预约不会丢失。已取消的预约以及没有时段的旧练习记录不能改期。

若当前时段已是 `targetSlotId`，返回当前预约且不追加记录；其他情况下，当前时段必须与 `expectedSlotId` 相同，否则返回 409，调用方应重新查询。它是当前时段的比较保护，不是通用的请求幂等键。

`GET /api/appointments/{id}/history?page=0&size=20` 返回与预约列表相同的分页结构。每条记录包含 `id`、`actorId`、`action`、`fromSlotId`、`toSlotId`、UTC `occurredAt`，按记录 ID 升序排列。`action` 为 `BOOKED`、`RESCHEDULED` 或 `CANCELLED`；操作人来自服务端登录身份。重复取消和已经到达目标时段的改期不会产生额外记录。

操作记录从 V5 上线后开始积累，不补造升级前的历史。历史查询同样受预约归属限制，他人的预约与不存在的预约统一返回 404。流程、锁顺序和并发测试见 [改期与操作记录说明](docs/rescheduling-and-history.md)。

### 分页与筛选预约

`GET /api/appointments` 返回分页对象，预约记录放在 `content` 数组中。原来直接读取列表数组的调用方需要改为读取 `content`。没有匹配记录时，响应例如：

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`totalElements` 是当前账号有权查看、且符合筛选条件的记录数，`totalPages` 是对应总页数，`hasNext` 表示是否还有下一页。记录固定按 `id` 升序排列。

| 查询参数 | 默认值 | 规则 |
| --- | --- | --- |
| `page` | `0` | 从 0 开始的非负整数 |
| `size` | `20` | 每页 1～100 条 |
| `status` | 不限制 | `BOOKED` 或 `CANCELLED` |
| `customerName` | 不限制 | 提供时去掉首尾空格，不能为空、不能超过 100 个字符；使用 SQL 等值匹配，遵循 MySQL 的 collation，不做模糊查询 |
| `from` | 不限制下界 | 带偏移的 ISO 时间，预约 `startTime >= from` |
| `to` | 不限制上界 | 带偏移的 ISO 时间，预约 `startTime < to` |

可组合多个筛选条件。时间范围是 **`[from, to)`**，筛选的是预约开始时间，不是创建记录的时间或整个预约区间是否重叠。输入时间先转换为 UTC `Instant` 并归一化到微秒精度；同时提供上下界时，归一化后的 `to` 必须晚于 `from`。允许查询历史时间范围。

`page * size` 不能超过 Java 的 `Integer.MAX_VALUE`（2,147,483,647）。非法参数返回 400，错误码为 `INVALID_ARGUMENT`。

下面的服务器查询示例需要把登录响应中的 `accessToken` 保存在当前终端的 `ACCESS_TOKEN` 变量里，再对姓名、时区偏移等参数进行 URL 编码。可以按已有记录修改姓名和范围；日期范围可用于查询历史记录，不要求在未来。

```bash
curl --get 'http://127.0.0.1:8080/api/appointments' \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'page=0' \
  --data-urlencode 'size=10' \
  --data-urlencode 'status=BOOKED' \
  --data-urlencode 'customerName=示例用户' \
  --data-urlencode 'from=2026-10-01T00:00:00+08:00' \
  --data-urlencode 'to=2026-11-01T00:00:00+08:00'
```

使用 `--data-urlencode` 可以正确保留时区中的 `+`，避免它被解析为空格。

### 在服务器尝试完整流程

配置管理员并启动应用后，在服务器终端运行下面的示例。它先用管理员发布时段，再注册普通账号完成预约。密码在终端隐藏输入，令牌不打印；示例会保留真实测试记录。

```bash
python3 - <<'PY'
import getpass
import json
from datetime import datetime, timedelta, timezone
from urllib.request import Request, urlopen
from uuid import uuid4

base_url = "http://127.0.0.1:8080"
access_token = None

def post(path, body):
    headers = {"Content-Type": "application/json"}
    if access_token:
        headers["Authorization"] = "Bearer " + access_token
    request = Request(
        base_url + path,
        data=json.dumps(body).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urlopen(request, timeout=10) as response:
        return json.load(response)

admin_username = input("管理员用户名 [admin]: ").strip() or "admin"
admin_password = getpass.getpass("管理员密码: ")
access_token = post("/api/auth/login", {"username": admin_username, "password": admin_password})["accessToken"]
doctor = post("/api/doctors", {"name": "示例医生"})
start = (datetime.now(timezone.utc) + timedelta(days=1)).replace(microsecond=0)
slot = post(f"/api/doctors/{doctor['id']}/slots", {
    "startTime": start.isoformat(),
    "endTime": (start + timedelta(minutes=30)).isoformat(),
})
username = "demo_" + uuid4().hex
password = getpass.getpass("为示例用户设置密码（至少 8 个字符）: ")
post("/api/auth/register", {"username": username, "displayName": "示例用户", "password": password})
access_token = post("/api/auth/login", {"username": username, "password": password})["accessToken"]
appointment = post("/api/appointments", {
    "customerName": "示例用户",
    "slotId": slot["id"],
})
print(json.dumps(appointment, ensure_ascii=False, indent=2))
print(f"示例账号：{username}")
print(f"预约详情：{base_url}/api/appointments/{appointment['id']}")
PY
```

完整的取消、重新预约、改期、操作记录和重启检查使用下面的部署验证脚本。

## 在 Ubuntu 服务器部署

当前服务器环境为 Ubuntu 24.04，已安装 Docker 与 Docker Compose。进入 **workspace 下已有的 appointment-manager 项目目录** 执行以下操作。路径以服务器上的实际目录为准。

### 首次配置数据库密码

数据库连接需要项目根目录 `.env` 中的以下两个键。管理员初始化配置在下一步补齐：

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

### 首次配置管理员

升级到本版本或首次部署时，在服务器运行：

```bash
python3 scripts/configure_admin.py
```

[配置脚本](scripts/configure_admin.py) 隐藏读取两次密码，在现有 `.env` 中加入 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD`，保留数据库配置。已有完整管理员配置时直接退出。用户名不要与普通用户重名。

Compose 把配置传为 `APP_ADMIN_USERNAME`、`APP_ADMIN_PASSWORD`，应用首次启动时创建管理员；以后重启保留数据库里的账号和密码。修改 `.env` 中的初始化密码不会重置已有管理员密码。公开注册接口只能创建普通用户，不能提交角色成为管理员。

### 更新与启动

对应提交的 CI 检查通过后执行：

```bash
git pull --ff-only
python3 scripts/configure_admin.py
sudo docker compose up -d --build
sudo docker compose ps
```

应用启动时由 Flyway 按顺序执行尚未执行的迁移，再由 Hibernate 校验表结构。已执行的迁移保持原样，后续表结构修改添加新的版本文件。

Compose 将应用绑定到服务器的 `127.0.0.1:8080`，可以在服务器终端访问。数据库数据保存在 `mysql-data` 命名卷中。

V4 添加账号、令牌与预约归属，V5 添加操作记录表；升级保留旧预约与已有账号，旧记录不会被补造历史。若以后开放公网接口，登录密码与令牌应通过 HTTPS 传输。

查看应用日志：

```bash
sudo docker compose logs --tail=100 app
```

### 验证部署和数据持久化

```bash
sudo python3 scripts/verify_persistence.py
```

[验证脚本](scripts/verify_persistence.py) 从 Compose 的实际配置中取得管理员凭证并登录，不打印密码和令牌。它创建医生和时段、预约、取消、重新预约、改期并检查操作记录，再重启 `app` 服务，检查登录令牌、取消历史、改期后的预约和操作记录是否保留。脚本会保留这些测试记录，只重启应用服务。

## 验证代码

[GitHub Actions 工作流](.github/workflows/build.yml) 在推送到 `main` 时执行：

```bash
mvn --batch-mode --no-transfer-progress package
```

工作流提供 MySQL 8.4 测试数据库，以及 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 环境变量。集成测试通过真实 HTTP 与数据库检查预约流程、并发占用和数据库约束；单元测试检查业务输入和模型规则。[ArchUnit 架构测试](src/test/java/com/op1ed/appointmentmanager/architecture/ArchitectureTest.java) 分析编译后的类，检查跨模块依赖与业务层对 HTTP 的依赖边界。ArchUnit 仅作为测试依赖，不参与应用运行。

身份测试覆盖密码散列、重复与并发注册、过期和撤销令牌、管理员操作、同名用户的预约隔离、伪造账号字段及历史记录权限。测试配置单独初始化测试管理员，部署凭证不写入源代码。

改期测试覆盖并发争抢目标时段、不同改期请求、相同请求重试、交叉改期、改期与预约或取消的竞争，以及记录保存失败时的真实事务回滚。权限、域模型和分页规则也有对应验证。

如果自行在其他环境运行 Maven，同样需要 Java 21、Maven、可访问的 MySQL，以及上述三个数据库环境变量。具体测试数量和是否通过，以该次 Maven 输出为准。
