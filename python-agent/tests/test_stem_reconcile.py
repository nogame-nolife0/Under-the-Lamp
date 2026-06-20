from app.graph_workflow import (
    _build_stem_items_from_segments,
    _extend_stem_in_raw,
    _extract_options_from_segment,
    _find_raw_segment,
    _pick_best_stem,
    _reconcile_stem_from_raw,
    _split_raw_into_questions,
    _stem_looks_merged,
)


def test_extend_stem_from_truncated_llm_output():
    raw = (
        "设计一个4线—2线二进制优先编码器，用与非门电路实现。输入为 [嵌入图片1]，"
        "[嵌入图片2] 优先级最高，[嵌入图片3] 最低。输出为 [嵌入图片4]，"
        "并加一 G 输出端，以指示最低优先级信号 [嵌入图片3] 输入有效。"
    )
    llm_stem = "设计一个4线—2线二进制优先编码器，用与非门电路实现。输入为 [嵌入图片1]"
    extended = _extend_stem_in_raw(llm_stem, raw)
    assert extended is not None
    assert "并加一 G 输出端" in extended
    assert "优先级最高" in extended


def test_find_raw_segment_does_not_match_digit_inside_text():
    raw = "设计一个4线—2线二进制优先编码器。输入为 A0 最低。"
    segment = _find_raw_segment("2", 1, _split_raw_into_questions(raw), raw)
    assert segment is None or segment.startswith("设计")


def test_pick_best_stem_prefers_longer_complete_text():
    short = "优先编码器74LS148正常工作，若输入端 [嵌入图片1]"
    long = (
        "优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，"
        "则输出 [嵌入图片2] 为多少？"
    )
    best = _pick_best_stem(short, long)
    assert "全为 1" in best
    assert "为多少" in best


def test_reconcile_stem_merges_segment_and_extension():
    raw = (
        "1. 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，"
        "则输出 [嵌入图片2] 为多少？\n"
        "2. 设计一个4线—2线二进制优先编码器。"
    )
    segments = _split_raw_into_questions(raw)
    stem, _ = _reconcile_stem_from_raw(
        "1. 优先编码器74LS148正常工作，若输入端 [嵌入图片1]",
        "1",
        1,
        raw,
        segments,
    )
    assert "全为 1" in stem
    assert "为多少" in stem


def test_pick_best_stem_prefers_complete_over_latex_spam():
    llm = (
        "优先编码器74LS148正常工作，若输入端 "
        "$\\overline{I_0}$ $\\overline{I_7}$ $\\overline{Y_2 Y_1 Y_0}$ "
        "$A_2 + \\overline{A_2} A_1$"
    )
    complete = (
        "优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，"
        "则输出 [嵌入图片2] 为多少？"
    )
    best = _pick_best_stem(llm, complete)
    assert "为多少" in best
    assert "[嵌入图片1]" in best


def test_extend_stem_with_latex_hint():
    raw = (
        "1. 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，"
        "则输出 [嵌入图片2] 为多少？\n"
        "2. 设计一个4线—2线二进制优先编码器。"
    )
    hint = "优先编码器74LS148正常工作，若输入端 $\\overline{I_0}$"
    extended = _extend_stem_in_raw(hint, raw)
    assert extended is not None
    assert "全为 1" in extended
    assert "为多少" in extended


def test_extract_inline_options_from_segment():
    segment = (
        "优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，则输出 [嵌入图片2] 为多少？"
        " A. 用与非门，Y=[嵌入图片5] B. 用与门，Y=[嵌入图片6] "
        "C. 用或门，Y=[嵌入图片7] D. 用或非门，Y=[嵌入图片8]"
    )
    options = _extract_options_from_segment(segment)
    assert options is not None
    assert len(options) == 4
    assert options[0].startswith("A.")
    assert options[3].startswith("D.")


def test_reconcile_74ls148_with_latex_llm_output():
    raw = (
        "1. 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 全为 1，"
        "则输出 [嵌入图片2] 为多少？"
        " A. 用与非门，Y=[嵌入图片5] B. 用与门，Y=[嵌入图片6] "
        "C. 用或门，Y=[嵌入图片7] D. 用或非门，Y=[嵌入图片8]\n"
        "2. 设计一个4线—2线二进制优先编码器。"
    )
    segments = _split_raw_into_questions(raw)
    llm_stem = (
        "1. 优先编码器74LS148正常工作，若输入端 "
        "$\\overline{I_0}$ $\\overline{I_7}$ $\\overline{Y_2 Y_1 Y_0}$"
    )
    stem, options = _reconcile_stem_from_raw(llm_stem, "1", 1, raw, segments)
    assert "全为 1" in stem
    assert "为多少" in stem
    assert options is not None
    assert len(options) == 4


def test_reconcile_prefers_segment_when_llm_incomplete():
    raw = (
        "3. 设计一个4线—2线二进制优先编码器，用与非门电路实现。输入为 [嵌入图片1]，"
        "[嵌入图片2] 优先级最高，[嵌入图片3] 最低。输出为 [嵌入图片4]，"
        "并加一 G 输出端，以指示最低优先级信号 [嵌入图片3] 输入有效。"
    )
    llm = (
        "3. 设计一个4线—2线二进制优先编码器，用与非门电路实现。输入为 "
        "$A_3A_2A_1A_0$ $A_3$ $A_0$ $Y_1Y_0$"
    )
    stem, _ = _reconcile_stem_from_raw(llm, "3", 1, raw, [{"label": "3", "content": raw}])
    assert "并加一 G 输出端" in stem
    assert "[嵌入图片1]" in stem


def test_reconcile_does_not_merge_4_1_and_4_2():
    raw = (
        "4-1 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 按顺序10101011输入时，"
        "输出 [嵌入图片2] 为多少？\n"
        "4-2 用三线-八线译码器74LS138和辅助门电路实现逻辑函数 Y=[嵌入图片3]，应（ ）。"
        " A. 用与非门，Y=[嵌入图片4] B. 用与门，Y=[嵌入图片5]"
        " C. 用或门，Y=[嵌入图片6] D. 用或门，Y=[嵌入图片7]\n"
        "4-3 设计一个4线—2线二进制优先编码器，用与非门电路实现。"
    )
    segments = _split_raw_into_questions(raw)
    merged_llm = (
        "4-1 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 按顺序10101011输入时，"
        "输出 [嵌入图片2] 为多少？"
        "4-2 用三线-八线译码器74LS138和辅助门电路实现逻辑函数 Y=[嵌入图片3]，应（ ）。"
    )
    stem, options = _reconcile_stem_from_raw(merged_llm, "4-2", 2, raw, segments)
    assert "74LS138" in stem
    assert "74LS148" not in stem
    assert "为多少" not in stem
    assert options is not None
    assert len(options) == 4
    assert options[3].startswith("D.")
    assert "4-3" not in options[3]
    assert "优先编码器" not in options[3]


def test_stem_looks_merged_detects_two_question_types():
    merged = (
        "优先编码器74LS148正常工作，若输入端 [嵌入图片1] 为多少？"
        "用三线-八线译码器74LS138和辅助门电路实现逻辑函数，应（ ）。"
    )
    assert _stem_looks_merged(merged, "4-2") is True
    assert _stem_looks_merged("4-2 用三线-八线译码器74LS138，应（ ）。", "4-2") is False


def test_split_homework_labels_into_separate_segments():
    raw = (
        "4-1 优先编码器74LS148正常工作，若输入端 [嵌入图片1] 为多少？\n"
        "4-2 用三线-八线译码器74LS138和辅助门电路实现逻辑函数，应（ ）。"
        " A. 用与非门 B. 用与门 C. 用或门 D. 用或门\n"
        "4-3 设计一个4线—2线二进制优先编码器，用与非门电路实现。\n"
        "4-4 试用74LS138实现下列逻辑函数，画出连线图。\n"
        "4-5 写出下图所示电路输出F1的最简逻辑表达式。[嵌入图片14]\n"
        "4-6 某车间有黄、红两个故障指示灯，用来监测三台设备的工作情况。"
    )
    segments = _split_raw_into_questions(raw)
    labels = [segment["label"] for segment in segments]
    assert labels == ["4-1", "4-2", "4-3", "4-4", "4-5", "4-6"]
    assert "74LS148" in segments[0]["content"]
    assert "74LS138" in segments[1]["content"]
    assert "设计一个" in segments[2]["content"]
    assert "写出下图" in segments[4]["content"]


def test_segment_to_stem_strips_inline_duplicate_question_label():
    from app.graph_workflow import _segment_to_stem

    segment = (
        "3. 试用 74LS138 实现下列逻辑函数，画出连线图。"
        "2. 写出下图所示电路输出F1的最简逻辑表达式。[嵌入图片1]"
        "某车间有黄、红两个故障指示灯，用来监测三台设备的工作情况。"
    )
    stem = _segment_to_stem(segment, "3")
    assert "74LS138" in stem
    assert "写出下图" not in stem
    assert "某车间" not in stem


def test_extract_glued_options_without_spaces():
    segment = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。"
        " A. 10x8B. 102x8C. 10x82D. 210x8"
    )
    options = _extract_options_from_segment(segment)
    assert options is not None
    assert len(options) == 4
    assert options[0].startswith("A.")
    assert options[1].startswith("B.")
    assert options[2].startswith("C.")
    assert options[3].startswith("D.")


def test_normalize_options_list_splits_merged_llm_output():
    from app.graph_workflow import _normalize_options_list

    merged = [
        "A. 10x8B. 102x8C. 10x82D. 210x8",
        "A. 8B. 16C. 2D. 4",
        "A. 掩膜ROMB. UVEPROMC. E2PROMD. Flash Memories",
    ]
    options = _normalize_options_list(merged)
    assert options is not None
    assert len(options) == 4
    assert options[0].startswith("A.")
    assert options[1].startswith("B.")
    assert "210" in options[3]


def test_reconcile_7_1_options_from_word_segment():
    raw = (
        "7-1 一个ROM共有10根地址线，8根位线（数据输出线），则其存储容量为( D )。\n"
        "A. $10 \\times 8$  B. $10^2 \\times 8$  C. $10 \\times 8^2$  D. $2^{10} \\times 8$\n"
        "7-2 为了构成$4096 \\times 8$的RAM，需要( B )片$1024 \\times 2$的RAM。\n"
        "A. 8  B. 16  C. 2  D. 4"
    )
    segments = _split_raw_into_questions(raw)
    stem, options = _reconcile_stem_from_raw(None, "7-1", 1, raw, segments)
    assert options is not None
    assert len(options) == 4
    assert "7-2" not in " ".join(options)
    assert "为了构成" not in " ".join(options)


def test_build_stem_items_from_segments_splits_merged_llm_output():
    raw = (
        "4-3 设计一个4线—2线二进制优先编码器，用与非门电路实现。\n"
        "4-4 试用74LS138实现下列逻辑函数，画出连线图。\n"
        "4-5 写出下图所示电路输出F1的最简逻辑表达式。[嵌入图片14]"
    )
    segments = _split_raw_into_questions(raw)
    merged_llm = [
        {
            "seqNo": 1,
            "questionLabel": "4-3",
            "stemRaw": (
                "4-3 设计一个4线—2线二进制优先编码器，用与非门电路实现。"
                "4-4 试用74LS138实现下列逻辑函数，画出连线图。"
                "4-5 写出下图所示电路输出F1的最简逻辑表达式。[嵌入图片14]"
            ),
            "questionType": "CALCULATION",
            "confidenceScore": 0.85,
        }
    ]
    items = _build_stem_items_from_segments(segments, merged_llm, raw, {})
    assert len(items) == 3
    assert "设计一个" in items[0].stem_raw
    assert "试用74LS138" in items[1].stem_raw
    assert "写出下图" in items[2].stem_raw
    assert "4-4" not in items[0].stem_raw
    assert "4-5" not in items[1].stem_raw
