"""Configure administrator bootstrap credentials in the server's existing .env.

Run interactively from the Ubuntu checkout: python3 scripts/configure_admin.py
Passwords are read without echo, never printed, and never passed as arguments.
"""

import getpass
import os
import re
import sys
from pathlib import Path


ENV_PATH = Path(__file__).resolve().parents[1] / ".env"


def quote_dotenv(value):
    # Compose single-quoted values preserve '$' literally and allow escaped quotes.
    return "'" + value.replace("'", "\\'") + "'"


def main():
    if not ENV_PATH.is_file():
        raise ValueError("请先按 README 创建包含数据库配置的 .env。")
    original = ENV_PATH.read_text(encoding="utf-8")
    lines = original.splitlines()
    configured = {}
    for line in lines:
        match = re.match(r"^\s*(ADMIN_USERNAME|ADMIN_PASSWORD)\s*=(.*)$", line)
        if match:
            configured[match.group(1)] = match.group(2).strip()
    if all(configured.get(key) not in (None, "", "''", '""')
           for key in ("ADMIN_USERNAME", "ADMIN_PASSWORD")):
        print("管理员配置已存在，未修改。更改初始化配置不会重置数据库中已有管理员的密码。")
        return
    username = input("管理员用户名 [admin]: ").strip().lower() or "admin"
    if not re.fullmatch(r"[a-z0-9_-]{3,50}", username):
        raise ValueError("用户名须为 3 到 50 位字母、数字、下划线或连字符。")
    password = getpass.getpass("管理员密码（至少 8 个字符）: ")
    if password.isspace() or len(password) < 8 or len(password.encode("utf-8")) > 72:
        raise ValueError("密码至少 8 个字符，UTF-8 编码后不能超过 72 字节。")
    if any(character in password for character in "\r\n") or password.endswith("\\"):
        raise ValueError("密码不能包含换行或以反斜杠结尾。")
    if password != getpass.getpass("再次输入密码: "):
        raise ValueError("两次密码不一致，未修改配置。")
    retained = [line for line in lines if not re.match(r"^\s*ADMIN_(USERNAME|PASSWORD)\s*=", line)]
    updated = "\n".join(retained).rstrip() + "\n"
    updated += "ADMIN_USERNAME=" + quote_dotenv(username) + "\n"
    updated += "ADMIN_PASSWORD=" + quote_dotenv(password) + "\n"
    ENV_PATH.write_text(updated, encoding="utf-8")
    os.chmod(ENV_PATH, 0o600)
    print("已写入管理员初始化配置，保留原有数据库配置。请重新构建并启动应用。")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
