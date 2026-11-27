from contextlib import nullcontext
from types import SimpleNamespace
from unittest.mock import Mock

from fastapi.testclient import TestClient

from agent import runtime
from config import settings
from main import app
from rag.reranker import Reranker


def test_retrieval_test_returns_ranked_sources_without_calling_llm(monkeypatch):
    hits = [
        {"chunk_id": "100", "doc_id": "10", "text": "old handbook", "bm25_rank": 1, "fusion_score": 0.0164},
        {"chunk_id": "200", "doc_id": "20", "text": "new handbook", "vector_rank": 1, "fusion_score": 0.0164},
    ]
    retriever = Mock()
    retriever.retrieve.return_value = hits
    monkeypatch.setattr(runtime, "retriever", retriever)
    reranker = Reranker.__new__(Reranker)
    reranker._tokenizer = Mock(return_value={})
    logits = Mock()
    reranker._model = Mock(return_value=SimpleNamespace(logits=logits))
    reranker._torch = SimpleNamespace(
        no_grad=nullcontext, sigmoid=Mock(return_value=SimpleNamespace(tolist=lambda: [0.2, 0.9])),
    )
    monkeypatch.setattr(runtime, "reranker", reranker)
    monkeypatch.setattr(settings, "agent_token", "test-agent-token")
    client = TestClient(app)
    url = "/api/knowledge/retrieval-test?knowledge_id=7"

    assert client.post(url, json={"query": "handbook"}).status_code == 401
    headers = {"X-Agent-Token": "test-agent-token"}
    assert client.post(url, json={"query": "  "}, headers=headers).status_code == 422
    response = client.post(url, json={"query": " handbook "}, headers=headers)

    assert response.status_code == 200
    retriever.retrieve.assert_called_once_with("handbook", "7", top_k=10)
    result = response.json()["data"]
    assert result["elapsed_ms"] >= 0
    assert result["hits"] == [
        {**hits[1], "score": 0.9, "rerank_score": 0.9},
        {**hits[0], "score": 0.2, "rerank_score": 0.2},
    ]
