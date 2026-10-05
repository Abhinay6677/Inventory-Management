from __future__ import annotations

from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree as ET

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

ROOT = Path(__file__).resolve().parent
XML_SPEC = ROOT / "phase4_spec_25_report.xml"
XML_ADDITIONAL = ROOT / "phase4_additional_25_report.xml"
PDF_OUT = ROOT / "Phase4_Unit_Testing_Detailed_Report.pdf"


def parse_suite(xml_path: Path) -> dict:
    tree = ET.parse(xml_path)
    root = tree.getroot()
    suite = root if root.tag == "testsuite" else root.find("testsuite")
    if suite is None:
        raise ValueError(f"No testsuite found in {xml_path}")

    cases = []
    for case in suite.findall("testcase"):
        name = case.attrib.get("name", "unknown")
        classname = case.attrib.get("classname", "")
        duration = float(case.attrib.get("time", "0") or 0)

        status = "PASSED"
        detail = ""
        skipped = case.find("skipped")
        failure = case.find("failure")
        error = case.find("error")

        if skipped is not None:
            status = "SKIPPED"
            detail = skipped.attrib.get("message", "") or (skipped.text or "").strip()
        elif failure is not None:
            status = "FAILED"
            detail = failure.attrib.get("message", "") or (failure.text or "").strip()
        elif error is not None:
            status = "ERROR"
            detail = error.attrib.get("message", "") or (error.text or "").strip()

        cases.append(
            {
                "name": name,
                "classname": classname,
                "duration": duration,
                "status": status,
                "detail": detail,
            }
        )

    return {
        "name": suite.attrib.get("name", xml_path.stem),
        "tests": int(suite.attrib.get("tests", "0")),
        "failures": int(suite.attrib.get("failures", "0")),
        "errors": int(suite.attrib.get("errors", "0")),
        "skipped": int(suite.attrib.get("skipped", "0")),
        "time": float(suite.attrib.get("time", "0") or 0),
        "cases": cases,
    }


def suite_pass_count(summary: dict) -> int:
    return summary["tests"] - summary["failures"] - summary["errors"] - summary["skipped"]


def build_pdf(spec_suite: dict, additional_suite: dict, output: Path) -> None:
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        name="TitleCustom", parent=styles["Title"], fontSize=18, leading=22, textColor=colors.HexColor("#12325A")
    )
    h2 = ParagraphStyle(name="H2", parent=styles["Heading2"], fontSize=13, leading=16, textColor=colors.HexColor("#1F4B87"))
    body = ParagraphStyle(name="Body", parent=styles["BodyText"], fontSize=9.5, leading=12)
    small = ParagraphStyle(name="Small", parent=styles["BodyText"], fontSize=8.5, leading=11, textColor=colors.HexColor("#425466"))

    doc = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        leftMargin=1.4 * cm,
        rightMargin=1.4 * cm,
        topMargin=1.2 * cm,
        bottomMargin=1.2 * cm,
    )

    story = []
    story.append(Paragraph("Phase 4 Unit Testing Detailed Report", title))
    story.append(Spacer(1, 0.2 * cm))
    story.append(
        Paragraph(
            f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | Project: Inventory Management | Phase 4",
            small,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    overall_table = Table(
        [
            ["Class / Suite", "Total", "Passed", "Skipped", "Failed", "Errors", "Duration (s)"],
            [
                "TestPhase4Spec25",
                str(spec_suite["tests"]),
                str(suite_pass_count(spec_suite)),
                str(spec_suite["skipped"]),
                str(spec_suite["failures"]),
                str(spec_suite["errors"]),
                f"{spec_suite['time']:.2f}",
            ],
            [
                "TestPhase4Additional25",
                str(additional_suite["tests"]),
                str(suite_pass_count(additional_suite)),
                str(additional_suite["skipped"]),
                str(additional_suite["failures"]),
                str(additional_suite["errors"]),
                f"{additional_suite['time']:.2f}",
            ],
        ],
        colWidths=[5.2 * cm, 1.5 * cm, 1.7 * cm, 1.6 * cm, 1.5 * cm, 1.4 * cm, 2.1 * cm],
    )
    overall_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#C9D7EC")),
                ("ALIGN", (1, 1), (-1, -1), "CENTER"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(overall_table)
    story.append(Spacer(1, 0.35 * cm))
    story.append(
        Paragraph(
            f"Combined pass count: {suite_pass_count(spec_suite) + suite_pass_count(additional_suite)} / {spec_suite['tests'] + additional_suite['tests']}",
            body,
        )
    )
    story.append(Spacer(1, 0.5 * cm))

    def append_suite_details(label: str, summary: dict) -> None:
        story.append(Paragraph(label, h2))
        story.append(
            Paragraph(
                (
                    f"Suite Name: {summary['name']} | Total: {summary['tests']} | Passed: {suite_pass_count(summary)} | "
                    f"Skipped: {summary['skipped']} | Failed: {summary['failures']} | Errors: {summary['errors']} | "
                    f"Duration: {summary['time']:.2f}s"
                ),
                small,
            )
        )
        story.append(Spacer(1, 0.2 * cm))

        rows = [["#", "Test Case", "Status", "Time (s)", "Details"]]
        for idx, case in enumerate(summary["cases"], start=1):
            detail = case["detail"] if case["detail"] else "-"
            if len(detail) > 110:
                detail = detail[:107] + "..."
            rows.append([str(idx), case["name"], case["status"], f"{case['duration']:.3f}", detail])

        table = Table(rows, colWidths=[0.8 * cm, 7.9 * cm, 1.8 * cm, 1.7 * cm, 5.8 * cm], repeatRows=1)
        table_style_cmds = [
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("FONTSIZE", (0, 0), (-1, -1), 8),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#FBFDFF")]),
        ]

        for row_index, case in enumerate(summary["cases"], start=1):
            if case["status"] == "PASSED":
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#166534")))
            elif case["status"] == "SKIPPED":
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#92400E")))
            else:
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#B91C1C")))

        table.setStyle(TableStyle(table_style_cmds))
        story.append(table)
        story.append(Spacer(1, 0.45 * cm))

    append_suite_details("Section A: Test Class - TestPhase4Spec25", spec_suite)
    append_suite_details("Section B: Test Class - TestPhase4Additional25", additional_suite)

    doc.build(story)


def main() -> None:
    if not XML_SPEC.exists() or not XML_ADDITIONAL.exists():
        raise FileNotFoundError("Expected XML reports not found. Run pytest with --junitxml first.")

    spec_suite = parse_suite(XML_SPEC)
    additional_suite = parse_suite(XML_ADDITIONAL)
    build_pdf(spec_suite, additional_suite, PDF_OUT)
    print(f"PDF generated: {PDF_OUT}")


if __name__ == "__main__":
    main()
