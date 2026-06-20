from xml.etree import ElementTree as ET

from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

from app.doc_parser import _omml_to_text, _paragraph_to_text
from docx import Document
from io import BytesIO


def test_omml_overline_and_text():
    xml = (
        f"<m:oMath {nsdecls('m')}>"
        "<m:r><m:t>F = </m:t></m:r>"
        "<m:sSub><m:e><m:r><m:t>A</m:t></m:r></m:e>"
        "<m:sub><m:r><m:t>B</m:t></m:r></m:sub></m:sSub>"
        "</m:oMath>"
    )
    elem = parse_xml(xml)
    text = _omml_to_text(elem)
    assert "F" in text
    assert "A" in text
    assert "B" in text


def test_read_minimal_docx_with_math():
    doc = Document()
    para = doc.add_paragraph()
    para.add_run("2-1 已知 ")
    doc.save("test_math.docx")
    text = __import__("app.doc_parser", fromlist=["read_docx_text"]).read_docx_text("test_math.docx")
    assert "2-1" in text
    import os
    os.remove("test_math.docx")
