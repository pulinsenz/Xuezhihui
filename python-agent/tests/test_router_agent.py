"""路由输出解析测试：模型输出带标点 / 中文标签 / 空值时的健壮性"""
import pytest

from agent.nodes.router_agent import _parse_route

ROUTE_CASES = [
    # (模型输出, 期望路由)
    ("kb", "kb"),
    ("KB", "kb"),
    ("kb。", "kb"),
    ("kb 知识库问答", "kb"),
    ("知识库问答", "kb"),
    ("chitchat", "chitchat"),
    ("chitchat。", "chitchat"),
    ("chitchat（闲聊）", "chitchat"),
    ("闲聊", "chitchat"),
    ("business", "business"),
    ("business：我的统计", "business"),
    ("业务", "business"),
    ("other", "other"),
    ("其他", "other"),
    ("", "chitchat"),
    ("   ", "chitchat"),
    ("乱七八糟的回复", "chitchat"),
]


@pytest.mark.parametrize("reply,expected", ROUTE_CASES)
def test_parse_route(reply, expected):
    assert _parse_route(reply) == expected
