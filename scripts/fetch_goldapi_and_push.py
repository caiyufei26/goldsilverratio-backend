# -*- coding: utf-8 -*-
"""
在能访问外网的电脑上运行本脚本，用 GoldAPI.io 拉取金/银价并推送到你的服务器。
服务器无需访问外网。需 Python 3.6+，无第三方依赖（仅用 urllib）。

用法：
  1. 修改下面 GOLDAPI_KEY、SERVER_URL（及可选 PUSH_SECRET）
  2. 在能上网的电脑执行：python fetch_goldapi_and_push.py
  3. 可配合计划任务 / cron 定时执行
"""
import json
import os
import ssl
import urllib.request

# ---------- 配置（可改为环境变量）----------
GOLDAPI_KEY = os.environ.get("GOLDAPI_KEY", "请填你的 GoldAPI Key")
SERVER_URL = os.environ.get("SERVER_URL", "http://你的服务器:8080").rstrip("/")
PUSH_SECRET = os.environ.get("PUSH_SECRET", "")

GOLD_URL = "https://www.goldapi.io/api/XAU/USD"
SILVER_URL = "https://www.goldapi.io/api/XAG/USD"


def fetch_price(url, key):
    req = urllib.request.Request(url)
    req.add_header("x-access-token", key)
    ctx = ssl.create_default_context()
    with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
        data = json.loads(resp.read().decode())
    return data.get("price") or data.get("close") or data.get("ask")


def push_to_server(gold, silver, server_url, push_secret):
    body = json.dumps({"gold": float(gold), "silver": float(silver)}).encode()
    req = urllib.request.Request(
        server_url + "/api/ratio/feed",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    if push_secret:
        req.add_header("X-Push-Secret", push_secret)
    with urllib.request.urlopen(req, timeout=10) as resp:
        out = json.loads(resp.read().decode())
    return out


def main():
    print("Fetching gold (XAU)...")
    gold = fetch_price(GOLD_URL, GOLDAPI_KEY)
    print("Fetching silver (XAG)...")
    silver = fetch_price(SILVER_URL, GOLDAPI_KEY)
    if gold is None or silver is None:
        print("ERROR: Could not get price from GoldAPI (check key or network)")
        return 1
    print("Gold:", gold, " Silver:", silver, " Ratio:", round(float(gold) / float(silver), 4))
    print("Pushing to server", SERVER_URL, "...")
    result = push_to_server(gold, silver, SERVER_URL, PUSH_SECRET)
    if result.get("code") == 200:
        print("OK: Pushed successfully.")
        return 0
    print("ERROR:", result.get("message", result))
    return 1


if __name__ == "__main__":
    exit(main())
