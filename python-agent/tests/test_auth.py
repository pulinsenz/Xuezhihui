"""内部鉴权测试"""
import pytest
from fastapi import HTTPException

from config import settings
from utils.auth import _constant_time_equals, verify_agent_token


def test_constant_time_equals():
    assert _constant_time_equals("abc", "abc")
    assert not _constant_time_equals("abc", "abd")
    assert not _constant_time_equals("", "abc")


def test_verify_correct_token(monkeypatch):
    monkeypatch.setattr(settings, "agent_token", "secret-token")
    # 正确 token 不抛异常
    verify_agent_token("secret-token")


def test_verify_wrong_token(monkeypatch):
    monkeypatch.setattr(settings, "agent_token", "secret-token")
    with pytest.raises(HTTPException) as e:
        verify_agent_token("wrong-token")
    assert e.value.status_code == 401


def test_verify_no_token_configured(monkeypatch):
    monkeypatch.setattr(settings, "agent_token", "")
    with pytest.raises(HTTPException):
        verify_agent_token("anything")
