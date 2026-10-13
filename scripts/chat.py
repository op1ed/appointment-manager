"""Interactive authenticated chat and explicit action confirmation.

Run on the Ubuntu server: python3 scripts/chat.py
Use --conversation UUID to resume a saved conversation.
Each new message may call the configured paid DeepSeek API.
"""

import argparse
import getpass
import json
import sys
import uuid
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BASE_URL = "http://127.0.0.1:8080"


def canonical_uuid(value):
    try:
        if str(uuid.UUID(value)) != value:
            raise ValueError()
        return value
    except (ValueError, AttributeError):
        raise ValueError("请输入完整的小写 UUID。") from None


class ApiClient:
    def __init__(self):
        self.token = None

    def request(self, path, method="GET", body=None):
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = Request(BASE_URL + path, method=method, headers=headers,
                          data=None if body is None else json.dumps(body).encode("utf-8"))
        try:
            with urlopen(request, timeout=120) as response:
                return json.load(response)
        except HTTPError as error:
            try:
                response = json.load(error)
            except (ValueError, OSError):
                raise RuntimeError(f"服务器返回 HTTP {error.code}") from None
            # 失败的模型轮次也有保存的对话响应，显示它并允许同一 requestId 重试读取。
            if "reply" in response and "requestId" in response:
                return response
            raise RuntimeError(response.get("detail", f"服务器返回 HTTP {error.code}")) from None


def local_time(value):
    if value is None:
        return "无"
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(
        timezone(timedelta(hours=8))).isoformat(timespec="seconds")


def show_appointment(appointment):
    print(f"  预约人：{appointment['customerName']}；医生：{appointment['doctorName']}")
    print(f"  时段：{appointment.get('slotId')}；时间：{local_time(appointment['startTime'])} 至 {local_time(appointment.get('endTime'))}")
    if "id" in appointment:
        print(f"  预约 ID：{appointment['id']}；状态：{appointment.get('status')}")


def show_action(action):
    print(f"\n动作 {action['id']}：{action['kind']} / {action['status']}；有效至 {local_time(action['expiresAt'])}")
    preview = action["preview"]
    print(preview["summary"])
    if preview.get("before"):
        print("原预约：")
        show_appointment(preview["before"])
    if preview.get("after"):
        print("目标预约：")
        show_appointment(preview["after"])
    if action["status"] == "PENDING":
        print("核对后输入 :confirm " + action["id"] + " 执行该动作。")


def show_reply(response):
    print("\n助手：" + response["reply"])
    for action in response.get("actions", []):
        show_action(action)
    if response.get("failureCode"):
        print("错误：" + response["failureCode"] + "。:retry 返回已保存结果；输入新消息才会重新请求模型。")
    elif response.get("status") == "LIMIT_REACHED":
        print("本轮已停止，请补充更具体的信息后继续。")


def main():
    parser = argparse.ArgumentParser(description="在服务器终端使用预约助手")
    parser.add_argument("--conversation", help="已有会话的小写 UUID")
    options = parser.parse_args()
    client = ApiClient()
    username = input("登录用户名: ").strip()
    password = getpass.getpass("登录密码（输入不会显示）: ")
    client.token = client.request("/api/auth/login", "POST", {"username": username, "password": password})["accessToken"]
    if options.conversation:
        conversation_id = canonical_uuid(options.conversation)
        client.request("/api/agent/conversations/" + conversation_id)
    else:
        conversation_id = client.request("/api/agent/conversations", "POST")["id"]
    path = "/api/agent/conversations/" + conversation_id
    print("会话 ID：" + conversation_id)
    print("新消息可能调用付费模型。:history 查看最近十轮；:retry 重试上一请求；:quit 退出。")
    print("文字回复“确认”不会执行预约。执行已展示的预览时，请输入 :confirm 动作UUID。")
    last_command = None
    while True:
        message = input("\n你：").strip()
        if message == ":quit":
            return
        if not message:
            continue
        try:
            if message == ":history":
                for turn in client.request(path)["turns"]:
                    print("\n你：" + turn["message"])
                    show_reply(turn["response"])
            elif message.startswith(":confirm "):
                action_id = canonical_uuid(message.removeprefix(":confirm ").strip())
                action_path = "/api/agent/actions/" + action_id
                current = client.request(action_path)
                if current["status"] == "EXPIRED":
                    raise RuntimeError("动作已过期，请重新生成预览。")
                # 此命令就是用户的显式确认；参数从服务器保存的动作中读取。
                confirmed = client.request(action_path + "/confirm", "POST")
                print("\n服务器返回的执行结果：")
                show_appointment(confirmed["result"])
            elif message == ":retry":
                if last_command is None:
                    raise RuntimeError("还没有可重试的请求。")
                show_reply(client.request(path + "/messages", "POST", last_command))
            elif message.startswith(":"):
                raise RuntimeError("未知命令，请使用 :history、:retry、:confirm 或 :quit。")
            else:
                last_command = {"requestId": str(uuid.uuid4()), "message": message}
                show_reply(client.request(path + "/messages", "POST", last_command))
        except (RuntimeError, URLError, OSError, ValueError) as error:
            print("请求未完成：" + str(error))
            print("若上次请求已在服务器完成，可用 :retry 读取同一请求的保存结果。")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, URLError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print("\n已退出。")
