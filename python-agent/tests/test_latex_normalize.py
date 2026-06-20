from app.latex_normalize import normalize_latex_delimiters, normalize_latex_fields


def test_wrap_bare_overline():
    raw = r"Y=\overline{A_2}\overline{A_1}"
    out = normalize_latex_delimiters(raw)
    assert out.startswith("$")
    assert r"\overline{A_2}" in out
    assert out.count("$") >= 2


def test_keep_existing_dollar_delimiters():
    raw = r"已知 $F=\overline{A}$ 成立"
    out = normalize_latex_delimiters(raw)
    assert out == raw


def test_convert_paren_delimiters():
    raw = r"函数 \(Y=\overline{A_1}\) 成立"
    out = normalize_latex_delimiters(raw)
    assert r"$Y=\overline{A_1}$" in out
    assert r"\(" not in out


def test_option_line_with_chinese_prefix():
    raw = r"用与非门，Y=\overline{\overline{Y_0} \ \overline{Y_1}}"
    out = normalize_latex_delimiters(raw)
    assert out.startswith("用与非门，")
    assert "$" in out
    assert r"\overline{\overline{Y_0}" in out


def test_normalize_fields_on_parse_row():
    row = {
        "stemRaw": r"Y=\overline{A_2}",
        "answerRaw": r"\overline{Y_2}+\overline{Y_3}",
        "options": [r"A. Y=\overline{Y_0}", "B. 纯文字"],
    }
    normalized = normalize_latex_fields(row)
    assert normalized["stemRaw"].startswith("$")
    assert normalized["answerRaw"].startswith("$")
    assert normalized["options"][0].endswith("$") or "$" in normalized["options"][0]
    assert normalized["options"][1] == "B. 纯文字"
