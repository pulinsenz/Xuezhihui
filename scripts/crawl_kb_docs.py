"""Download selected Chinese MDN HTTP guides as knowledge-base fixtures."""

import argparse
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

RAW_ROOT = "https://raw.githubusercontent.com/mdn/translated-content/main/files/zh-cn/web/http/guides"
MDN_ROOT = "https://developer.mozilla.org/zh-CN/docs"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/2.5/"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "data" / "kb-test-docs" / "mdn-http"
MAX_BYTES = 1024 * 1024
TOPICS = {
    "overview": "HTTP 概述",
    "messages": "HTTP 报文",
    "evolution_of_http": "HTTP 的演变",
    "caching": "HTTP 缓存",
    "conditional_requests": "HTTP 条件请求",
    "compression": "HTTP 压缩",
    "content_negotiation": "HTTP 内容协商",
    "mime_types": "HTTP MIME 类型",
}


def download(topic: str, title: str) -> str:
    url = f"{RAW_ROOT}/{topic}/index.md"
    request = Request(url, headers={"User-Agent": "Xuezhihui-KB-TestDocs/1.0"})
    with urlopen(request, timeout=20) as response:
        data = response.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("document exceeds 1 MiB")

    raw = data.decode("utf-8-sig").replace("\r\n", "\n")
    front, separator, body = raw[4:].partition("\n---\n")
    slug = re.search(r"^slug: (Web/HTTP/[A-Za-z0-9_/.-]+)$", front, re.MULTILINE)
    if not raw.startswith("---\n") or not separator or not slug or not body.strip():
        raise ValueError("expected a non-empty MDN Markdown document")

    return (
        f"# {title}\n\n{body.strip()}\n\n"
        f"---\n\n"
        f"来源：[{title}]({MDN_ROOT}/{slug.group(1)})，Mozilla Contributors。\n\n"
        f"文档许可：[CC BY-SA 2.5 或更高版本]({LICENSE_URL})，另有标注的内容除外。\n\n"
        f"采集改动：移除页面元数据，添加文档标题和来源说明。\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="destination directory")
    parser.add_argument("topics", nargs="*", metavar="TOPIC",
                        help="download all by default; choices: " + ", ".join(TOPICS))
    args = parser.parse_args()
    unknown = [topic for topic in args.topics if topic not in TOPICS]
    if unknown:
        parser.error("unknown topic: " + ", ".join(unknown))
    args.output.mkdir(parents=True, exist_ok=True)

    downloaded = skipped = failed = 0
    for topic in dict.fromkeys(args.topics or TOPICS):
        title = TOPICS[topic]
        path = args.output / f"{title}.md"
        if path.is_file():
            skipped += 1
            print(f"Exists: {path.name}")
            continue
        if downloaded or failed:
            time.sleep(0.5)
        try:
            content = download(topic, title)
            with path.open("x", encoding="utf-8", newline="\n") as out:
                out.write(content)
        except (OSError, ValueError) as exc:
            failed += 1
            print(f"Failed: {topic}: {exc}", file=sys.stderr)
            if isinstance(exc, HTTPError) and exc.code == 429:
                break
        else:
            downloaded += 1
            print(f"Saved: {path.name}")

    print(f"Downloaded: {downloaded}; skipped: {skipped}; failed: {failed}. Output: {args.output.resolve()}")
    return int(failed > 0)


if __name__ == "__main__":
    raise SystemExit(main())
