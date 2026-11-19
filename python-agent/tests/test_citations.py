"""引用过滤：只保留回答正文实际引用的来源分片。"""
from agent.nodes.answer_agent import filter_cited_sources


def make_sources(n):
    return [{"text": f"分片{i}", "doc_id": f"doc{i}"} for i in range(1, n + 1)]


def test_keeps_only_cited_chunks_in_original_order():
    sources = filter_cited_sources("前半句依据 [3]；后半句依据 [1]。", make_sources(5))
    assert [s["cite_no"] for s in sources] == [1, 3]
    assert [s["text"] for s in sources] == ["分片1", "分片3"]


def test_parses_comma_separated_citations():
    sources = filter_cited_sources("要点一 [2, 4]、要点二 [1]。", make_sources(4))
    assert [s["cite_no"] for s in sources] == [1, 2, 4]


def test_out_of_range_numbers_are_ignored():
    sources = filter_cited_sources("结论 [2] 和不存在的 [9]。", make_sources(3))
    assert [s["cite_no"] for s in sources] == [2]


def test_all_invalid_numbers_fall_back_to_all_sources():
    original = make_sources(3)
    sources = filter_cited_sources("结论 [9]。", original)
    assert sources == original
    assert all("cite_no" not in s for s in sources)


def test_no_citations_falls_back_to_all_sources():
    original = make_sources(2)
    assert filter_cited_sources("没有标注编号的回答。", original) == original
    assert filter_cited_sources(None, original) == original


def test_empty_sources_returned_as_is():
    sources = []
    assert filter_cited_sources("回答 [1]。", sources) is sources
