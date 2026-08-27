"""生产配置校验测试"""
import pytest

from config import settings, validate_settings


def test_validate_missing_key_and_token(monkeypatch):
    monkeypatch.setattr(settings, "deepseek_api_key", "")
    monkeypatch.setattr(settings, "user_api_key", "sk-user")
    monkeypatch.setattr(settings, "allow_mock_llm", False)
    monkeypatch.setattr(settings, "agent_token", "")
    with pytest.raises(RuntimeError, match="DEEPSEEK_API_KEY"):
        validate_settings()


def test_validate_missing_user_api_key(monkeypatch):
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")
    monkeypatch.setattr(settings, "user_api_key", "")
    monkeypatch.setattr(settings, "allow_mock_llm", False)
    monkeypatch.setattr(settings, "agent_token", "token")
    with pytest.raises(RuntimeError, match="USER_API_KEY"):
        validate_settings()


def test_validate_missing_token_only(monkeypatch):
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")
    monkeypatch.setattr(settings, "user_api_key", "sk-user")
    monkeypatch.setattr(settings, "allow_mock_llm", False)
    monkeypatch.setattr(settings, "agent_token", "")
    with pytest.raises(RuntimeError, match="AGENT_TOKEN"):
        validate_settings()


def test_validate_mock_llm_allowed_without_key(monkeypatch):
    """仅开发：ALLOW_MOCK_LLM=true 且配置了 token 时可通过"""
    monkeypatch.setattr(settings, "deepseek_api_key", "")
    monkeypatch.setattr(settings, "user_api_key", "")
    monkeypatch.setattr(settings, "allow_mock_llm", True)
    monkeypatch.setattr(settings, "agent_token", "token")
    validate_settings()  # 不抛


def test_validate_full_config_passes(monkeypatch):
    monkeypatch.setattr(settings, "deepseek_api_key", "sk-test")
    monkeypatch.setattr(settings, "user_api_key", "sk-user")
    monkeypatch.setattr(settings, "agent_token", "token")
    validate_settings()  # 不抛
