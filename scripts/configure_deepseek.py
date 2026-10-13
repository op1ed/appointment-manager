"""Enable DeepSeek chat in the server's .env without echoing the API key.

Run on Ubuntu: python3 scripts/configure_deepseek.py
The existing database and administrator settings are preserved.
"""

import getpass
import os
import re
import sys
from pathlib import Path

from configure_admin import quote_dotenv


ENV_PATH = Path(__file__).resolve().parents[1] / ".env"


def main():
    if not ENV_PATH.is_file():
        raise ValueError("请先按 README 创建包含数据库配置的 .env。")
    original = ENV_PATH.read_text(encoding="utf-8")
    key = getpass.getpass("DeepSeek API Key（输入不会显示）: ").strip()
    if not re.fullmatch(r"[A-Za-z0-9._~+/-]{1,512}=*", key):
        raise ValueError("API Key 不能为空，且不能包含空格或换行。")
    model = input("模型名 [deepseek-flash]: ").strip() or "deepseek-flash"
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", model):
        raise ValueError("模型名格式不合法。")
    retained = [line for line in original.splitlines()
                if not re.match(r"^\s*(AGENT_ENABLED|DEEPSEEK_API_KEY|DEEPSEEK_MODEL)\s*=", line)]
    updated = "\n".join(retained).rstrip() + "\n"
    updated += "AGENT_ENABLED=true\n"
    updated += "DEEPSEEK_API_KEY=" + quote_dotenv(key) + "\n"
    updated += "DEEPSEEK_MODEL=" + quote_dotenv(model) + "\n"
    ENV_PATH.write_text(updated, encoding="utf-8")
    os.chmod(ENV_PATH, 0o600)
    print("DeepSeek 配置已保存，数据库和管理员配置保持原样。请重新创建应用容器使环境变量生效。")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
