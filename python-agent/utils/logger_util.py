"""
Agent 链路结构化埋点日志
"""
import json
import logging

from config import settings

_loggers = {}


def get_logger(name: str = "agent") -> logging.Logger:
    """获取（缓存）logger，避免重复添加 handler；级别随 LOG_LEVEL 配置"""
    if name not in _loggers:
        logger = logging.getLogger(f"xuezhihui.{name}")
        level = getattr(logging, settings.log_level.upper(), logging.INFO)
        logger.setLevel(level)
        if not logger.handlers:
            handler = logging.StreamHandler()
            handler.setFormatter(logging.Formatter(
                "%(asctime)s [%(levelname)s] %(name)s | %(message)s"))
            logger.addHandler(handler)
        _loggers[name] = logger
    return _loggers[name]


def agent_event(logger: logging.Logger, event: str, **kwargs):
    """Agent 链路埋点，输出结构化 JSON，便于排查幻觉/检索问题"""
    logger.info(json.dumps({"event": event, **kwargs}, ensure_ascii=False))
