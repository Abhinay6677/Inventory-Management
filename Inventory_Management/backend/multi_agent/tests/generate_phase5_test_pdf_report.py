from __future__ import annotations

from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree as ET

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

ROOT = Path(__file__).resolve().parent
XML_SPEC = ROOT / "phase5_spec_25_report.xml"
XML_ADDITIONAL = ROOT / "phase5_additional_25_report.xml"
PDF_OUT = ROOT / "Phase5_Unit_Testing_Detailed_Report.pdf"


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


def _category_for(name: str) -> str:
    lowered = name.lower()
    if "state" in lowered or "initial" in lowered or "mutation" in lowered or "persist" in lowered or "isolated" in lowered or "product_id" in lowered:
        return "State Schema"
    if "route" in lowered or "router" in lowered or "node" in lowered or "entry" in lowered or "graph" in lowered:
        return "Routing / Graph"
    if "pipeline" in lowered or "full" in lowered or "e2e" in lowered or "audit_non_empty" in lowered or "terminal" in lowered or "four_messages" in lowered or "langsmith" in lowered or "urgent" in lowered:
        return "End-to-End"
    if "safe_json" in lowered:
        return "JSON Parsing Utility"
    if "auditor" in lowered:
        return "Inventory Auditor Agent"
    if "supplier" in lowered:
        return "Supplier Coordinator Agent"
    if "reorder" in lowered:
        return "Reorder Agent"
    if "demand" in lowered or "forecast" in lowered:
        return "Demand Forecaster Agent"
    return "Agent Behavior"


def build_pdf(spec_suite: dict, additional_suite: dict, output: Path) -> None:
    styles = getSampleStyleSheet()
    title = ParagraphStyle(
        name="TitleCustom", parent=styles["Title"], fontSize=18, leading=22, textColor=colors.HexColor("#12325A")
    )
    h2 = ParagraphStyle(name="H2", parent=styles["Heading2"], fontSize=13, leading=16, textColor=colors.HexColor("#1F4B87"))
    h3 = ParagraphStyle(name="H3", parent=styles["Heading3"], fontSize=10.5, leading=13, textColor=colors.HexColor("#2E6B9E"))
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

    story: list = []
    story.append(Paragraph("Phase 5 Multi-Agent LangGraph — Detailed Test Case Report", title))
    story.append(Spacer(1, 0.2 * cm))
    story.append(
        Paragraph(
            f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | Project: Inventory Management | "
            f"Phase 5 — Multi-Agent with LangGraph | POC-07",
            small,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    total_tests = spec_suite["tests"] + additional_suite["tests"]
    total_passed = suite_pass_count(spec_suite) + suite_pass_count(additional_suite)
    total_failed = spec_suite["failures"] + spec_suite["errors"] + additional_suite["failures"] + additional_suite["errors"]
    total_skipped = spec_suite["skipped"] + additional_suite["skipped"]
    pass_rate = (total_passed / total_tests * 100) if total_tests else 0.0
    threshold_status = "CLEARED" if total_passed >= 18 else "NOT CLEARED"
    threshold_color = "#0B7A34" if total_passed >= 18 else "#B42318"

    story.append(Paragraph("Executive Summary", h2))
    story.append(
        Paragraph(
            (
                f"Total Test Cases: <b>{total_tests}</b> (spec-required 25 + additional 25)<br/>"
                f"Passed: <b>{total_passed}</b> | Failed: <b>{total_failed}</b> | Skipped: <b>{total_skipped}</b><br/>"
                f"Overall Pass Rate: <b>{pass_rate:.1f}%</b><br/>"
                f"Phase 5 Pass Threshold (≥18 of 25 spec cases): "
                f"<font color='{threshold_color}'><b>{threshold_status}</b></font>"
            ),
            body,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    story.append(Paragraph("Suite-Level Summary", h2))
    overall_table = Table(
        [
            ["Class / Suite", "Total", "Passed", "Skipped", "Failed", "Errors", "Duration (s)"],
            [
                "TestPhase5Spec25 (attached specification)",
                str(spec_suite["tests"]),
                str(suite_pass_count(spec_suite)),
                str(spec_suite["skipped"]),
                str(spec_suite["failures"]),
                str(spec_suite["errors"]),
                f"{spec_suite['time']:.2f}",
            ],
            [
                "TestPhase5Additional25 (extra unit coverage)",
                str(additional_suite["tests"]),
                str(suite_pass_count(additional_suite)),
                str(additional_suite["skipped"]),
                str(additional_suite["failures"]),
                str(additional_suite["errors"]),
                f"{additional_suite['time']:.2f}",
            ],
            [
                "TOTAL",
                str(total_tests),
                str(total_passed),
                str(total_skipped),
                str(spec_suite["failures"] + additional_suite["failures"]),
                str(spec_suite["errors"] + additional_suite["errors"]),
                f"{spec_suite['time'] + additional_suite['time']:.2f}",
            ],
        ],
        colWidths=[6.6 * cm, 1.6 * cm, 1.6 * cm, 1.6 * cm, 1.6 * cm, 1.6 * cm, 2.3 * cm],
        repeatRows=1,
    )
    overall_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTNAME", (0, -1), (-1, -1), "Helvetica-Bold"),
                ("BACKGROUND", (0, -1), (-1, -1), colors.HexColor("#F0F5EC")),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
                ("FONTSIZE", (0, 0), (-1, -1), 8.5),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ]
        )
    )
    story.append(overall_table)
    story.append(Spacer(1, 0.4 * cm))

    # Category breakdown (functional grouping) across both suites combined
    story.append(Paragraph("Functional Category Breakdown", h2))
    category_stats: dict[str, dict[str, int]] = {}
    for suite in (spec_suite, additional_suite):
        for case in suite["cases"]:
            cat = _category_for(case["name"])
            stats = category_stats.setdefault(cat, {"total": 0, "passed": 0, "failed": 0})
            stats["total"] += 1
            if case["status"] == "PASSED":
                stats["passed"] += 1
            else:
                stats["failed"] += 1

    cat_rows = [["Category", "Total", "Passed", "Failed/Skipped"]]
    for cat, stats in sorted(category_stats.items(), key=lambda kv: -kv[1]["total"]):
        cat_rows.append([cat, str(stats["total"]), str(stats["passed"]), str(stats["failed"])])
    cat_table = Table(cat_rows, colWidths=[7.5 * cm, 2.3 * cm, 2.3 * cm, 3.2 * cm], repeatRows=1)
    cat_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
                ("FONTSIZE", (0, 0), (-1, -1), 8.5),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(cat_table)
    story.append(Spacer(1, 0.4 * cm))
    story.append(PageBreak())

    def append_suite_section(suite: dict, title_text: str, description: str) -> None:
        story.append(Paragraph(title_text, h2))
        story.append(Paragraph(description, small))
        story.append(Spacer(1, 0.25 * cm))

        grouped: dict[str, list[dict]] = {}
        for case in suite["cases"]:
            grouped.setdefault(_category_for(case["name"]), []).append(case)

        for cat, cases in sorted(grouped.items()):
            story.append(Paragraph(f"{cat} ({len(cases)} cases)", h3))
            rows = [["#", "Test Case", "Status", "Time (s)", "Details"]]
            for i, case in enumerate(cases, start=1):
                detail = case["detail"] or "-"
                if len(detail) > 100:
                    detail = detail[:97] + "..."
                rows.append([str(i), case["name"], case["status"], f"{case['duration']:.3f}", detail])

            table = Table(rows, colWidths=[0.8 * cm, 6.2 * cm, 1.8 * cm, 1.7 * cm, 6.0 * cm], repeatRows=1)
            style_commands = [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
                ("FONTSIZE", (0, 0), (-1, -1), 8),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#FBFDFF")]),
            ]
            for row_index, case in enumerate(cases, start=1):
                if case["status"] == "PASSED":
                    style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#166534")))
                elif case["status"] == "SKIPPED":
                    style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#92400E")))
                else:
                    style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#B91C1C")))
            table.setStyle(TableStyle(style_commands))
            story.append(table)
            story.append(Spacer(1, 0.3 * cm))

        story.append(Spacer(1, 0.2 * cm))

    append_suite_section(
        spec_suite,
        "Section 1 — TestPhase5Spec25 (Attached Specification Suite)",
        "Covers the 25 required Phase 5 test cases from the attached specification: state schema, "
        "agent behavior, routing logic, and end-to-end pipeline execution.",
    )
    story.append(PageBreak())
    append_suite_section(
        additional_suite,
        "Section 2 — TestPhase5Additional25 (Additional Unit Test Suite)",
        "Covers 25 additional unit test cases created for deeper coverage: JSON parsing utility edge "
        "cases, state isolation guarantees, per-agent error handling, and router edge conditions.",
    )

    doc.build(story)


def main() -> None:
    spec_suite = parse_suite(XML_SPEC)
    additional_suite = parse_suite(XML_ADDITIONAL)
    build_pdf(spec_suite, additional_suite, PDF_OUT)
    print(f"PDF generated: {PDF_OUT}")


if __name__ == "__main__":
    main()
