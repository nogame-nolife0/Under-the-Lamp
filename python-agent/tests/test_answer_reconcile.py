from app.graph_workflow import (
    _answer_looks_merged,
    _build_answer_items_from_segments,
    _reconcile_answer_from_raw,
    _segment_to_answer,
    _split_raw_into_questions,
)


def test_segment_to_answer_keeps_sub_questions():
    segment = (
        "4-4 解：(1) $F_1(A,B,C)=\\overline{A}B\\overline{C}+BC$ "
        "(2) $F_2(A,B,C,D)=\\sum m(2,6,9,13,15)$ [嵌入图片1]"
    )
    answer = _segment_to_answer(segment, "4-4")
    assert "(1)" in answer
    assert "(2)" in answer
    assert "F_2" in answer
    assert "[嵌入图片1]" in answer


def test_reconcile_answer_does_not_merge_4_4_and_4_5():
    raw = (
        "4-4 解：(1) $F_1=\\sum m(2,3,7)$ (2) $F_2=\\sum m(2,6,9,13,15)$ [嵌入图片1]\n"
        "4-5 解：(1) $F_3=ABC$ (2) $F_4=\\overline{A}$ (3) $F_5=B$ (4) $F_6=C$\n"
        "4-6 解：黄灯 $Y=$ 红灯 $R=$ [嵌入图片2]"
    )
    segments = _split_raw_into_questions(raw)
    merged_llm = (
        "4-4 解：(1) $F_1=\\sum m(2,3,7)$ (2) $F_2=\\sum m(2,6,9,13,15)$ "
        "4-5 解：(1) $F_3=ABC$ (2) $F_4=\\overline{A}$ (3) $F_5=B$ (4) $F_6=C$"
        "4-6 解：黄灯 $Y=$ 红灯 $R=$"
    )
    answer = _reconcile_answer_from_raw(merged_llm, "4-4", 4, raw, segments)
    assert "F_1" in answer
    assert "F_2" in answer
    assert "F_3" not in answer
    assert "4-5" not in answer
    assert "4-6" not in answer


def test_reconcile_answer_for_4_5_excludes_neighbors():
    raw = (
        "4-4 解：(1) $F_1=\\sum m(2,3,7)$ (2) $F_2=\\sum m(2,6,9,13,15)$\n"
        "4-5 解：(1) $F_3=ABC$ (2) $F_4=\\overline{A}$ (3) $F_5=B$ (4) $F_6=C$\n"
        "4-6 解：黄灯 $Y=$ 红灯 $R=$"
    )
    segments = _split_raw_into_questions(raw)
    answer = _reconcile_answer_from_raw(None, "4-5", 5, raw, segments)
    assert "F_3" in answer
    assert "(4)" in answer
    assert "F_1" not in answer
    assert "黄灯" not in answer


def test_answer_looks_merged_detects_cross_labels():
    merged = "4-4 解：(1) F1 4-5 解：(1) F3"
    assert _answer_looks_merged(merged, "4-4") is True
    assert _answer_looks_merged("4-4 解：(1) F1 (2) F2", "4-4") is False


def test_reconcile_answer_for_7_1_choice_question():
    raw = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。\n"
        "A. $10 \\times 8$  B. $10^2 \\times 8$  C. $10 \\times 8^2$  D. $2^{10} \\times 8$\n"
        "7-2 为了构成$4096 \\times 8$的RAM，需要( B )片$1024 \\times 2$的RAM。\n"
        "A. 8  B. 16  C. 2  D. 4\n"
        "7-3 下列ROM中，目前应用最广泛的是( D )。"
    )
    segments = _split_raw_into_questions(raw)
    answer = _reconcile_answer_from_raw(None, "7-1", 1, raw, segments)
    assert answer == "D"
    assert "7-2" not in answer
    assert "为了构成" not in answer


def test_reconcile_answer_merged_llm_for_7_1():
    raw = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。\n"
        "A. $10 \\times 8$  B. $10^2 \\times 8$  C. $10 \\times 8^2$  D. $2^{10} \\times 8$\n"
        "7-2 为了构成$4096 \\times 8$的RAM，需要( B )片$1024 \\times 2$的RAM。\n"
        "7-3 下列ROM中，目前应用最广泛的是( D )。"
    )
    segments = _split_raw_into_questions(raw)
    merged_llm = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。"
        "A. 10x8B. 102x8C. 10x82D. 210x8 为了构成4096x8的RAM，需要( B )片"
        "7-3 下列ROM中，目前应用最广泛的是( D )。"
    )
    answer = _reconcile_answer_from_raw(merged_llm, "7-1", 1, raw, segments)
    assert answer == "D"
    assert "为了构成" not in answer
    assert "7-3" not in answer


def test_reconcile_answer_for_7_2_keeps_neighbor_out():
    raw = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。\n"
        "A. $10 \\times 8$  B. $10^2 \\times 8$  C. $10 \\times 8^2$  D. $2^{10} \\times 8$\n"
        "7-2 为了构成$4096 \\times 8$的RAM，需要( B )片$1024 \\times 2$的RAM。\n"
        "A. 8  B. 16  C. 2  D. 4"
    )
    segments = _split_raw_into_questions(raw)
    answer = _reconcile_answer_from_raw(None, "7-2", 2, raw, segments)
    assert answer == "B"
    assert "ROM" not in answer
    assert "7-1" not in answer


def test_build_answer_items_from_segments_splits_merged_llm_output():
    raw = (
        "4-4 解：(1) $F_1=\\sum m(2,3,7)$ (2) $F_2=\\sum m(2,6,9,13,15)$\n"
        "4-5 解：(1) $F_3=ABC$ (2) $F_4=\\overline{A}$\n"
        "4-6 解：黄灯 $Y=$ 红灯 $R=$"
    )
    segments = _split_raw_into_questions(raw)
    merged_llm = [
        {
            "seqNo": 1,
            "questionLabel": "4-4",
            "answerRaw": (
                "4-4 解：(1) $F_1=\\sum m(2,3,7)$ (2) $F_2=\\sum m(2,6,9,13,15)$ "
                "4-5 解：(1) $F_3=ABC$ (2) $F_4=\\overline{A}$ "
                "4-6 解：黄灯 $Y=$ 红灯 $R=$"
            ),
            "questionType": "CALCULATION",
            "confidenceScore": 0.85,
        }
    ]
    items = _build_answer_items_from_segments(segments, merged_llm, raw, {})
    assert len(items) == 3
    assert "F_1" in (items[0].answer_raw or "")
    assert "F_2" in (items[0].answer_raw or "")
    assert "F_3" not in (items[0].answer_raw or "")
    assert "F_3" in (items[1].answer_raw or "")
    assert "黄灯" in (items[2].answer_raw or "")
