from __future__ import annotations

import base64
import json
import os
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


def sonar_get(base_url: str, token: str, path: str, query: dict[str, str] | None = None) -> dict:
    if query:
        path = f"{path}?{urlencode(query)}"
    auth = base64.b64encode(f"{token}:".encode("ascii")).decode("ascii")
    req = Request(f"{base_url}{path}", headers={"Authorization": f"Basic {auth}"})
    with urlopen(req, timeout=30) as response:  # nosec B310 - local SonarQube endpoint
        return json.loads(response.read().decode("utf-8"))


def parse_junit(path: Path) -> dict:
    if not path.exists():
        return {"total": 0, "passed": 0, "skipped": 0, "failed": 0, "cases": []}
    root = ET.parse(path).getroot()
    suite = root if root.tag == "testsuite" else root.find("testsuite")
    if suite is None:
        return {"total": 0, "passed": 0, "skipped": 0, "failed": 0, "cases": []}

    total = int(suite.attrib.get("tests", "0"))
    failures = int(suite.attrib.get("failures", "0"))
    errors = int(suite.attrib.get("errors", "0"))
    skipped = int(suite.attrib.get("skipped", "0"))
    passed = total - failures - errors - skipped

    cases: list[dict[str, str]] = []
    for case in suite.findall("testcase"):
        status = "PASSED"
        detail = ""
        skipped_node = case.find("skipped")
        failure_node = case.find("failure")
        error_node = case.find("error")
        if skipped_node is not None:
            status = "SKIPPED"
            detail = skipped_node.attrib.get("message", "") or (skipped_node.text or "").strip()
        elif failure_node is not None:
            status = "FAILED"
            detail = failure_node.attrib.get("message", "") or (failure_node.text or "").strip()
        elif error_node is not None:
            status = "ERROR"
            detail = error_node.attrib.get("message", "") or (error_node.text or "").strip()

        cases.append(
            {
                "name": case.attrib.get("name", "unknown"),
                "classname": case.attrib.get("classname", ""),
                "time": f"{float(case.attrib.get('time', '0') or 0):.3f}",
                "status": status,
                "detail": detail,
            }
        )

    return {"total": total, "passed": passed, "skipped": skipped, "failed": failures + errors, "cases": cases}


def format_facet(values: list[dict]) -> str:
    if not values:
        return "none"
    return ", ".join(f"{v.get('val')}={v.get('count')}" for v in values)


def add_table(
    story: list,
    rows: list[list[str]],
    col_widths: list[float],
    repeat_rows: int = 1,
    font_size: int = 8,
    zebra_alt: str = "#F8FBFF",
) -> None:
    table = Table(rows, colWidths=col_widths, repeatRows=repeat_rows)
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
                ("FONTSIZE", (0, 0), (-1, -1), font_size),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor(zebra_alt)]),
            ]
        )
    )
    story.append(table)


def append_test_case_details(story: list, heading_style: ParagraphStyle, title_text: str, suite: dict) -> None:
    story.append(Paragraph(title_text, heading_style))
    rows = [["#", "Test Case", "Status", "Time (s)", "Details"]]
    for i, case in enumerate(suite["cases"], start=1):
        detail = case["detail"] or "-"
        if len(detail) > 120:
            detail = detail[:117] + "..."
        rows.append([str(i), case["name"], case["status"], case["time"], detail])

    table = Table(rows, colWidths=[0.8 * cm, 6.4 * cm, 1.8 * cm, 1.7 * cm, 5.8 * cm], repeatRows=1)
    style_commands = [
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
        ("FONTSIZE", (0, 0), (-1, -1), 8),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#FBFDFF")]),
    ]

    for row_index, case in enumerate(suite["cases"], start=1):
        if case["status"] == "PASSED":
            style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#166534")))
        elif case["status"] == "SKIPPED":
            style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#92400E")))
        else:
            style_commands.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#B91C1C")))

    table.setStyle(TableStyle(style_commands))
    story.append(table)
    story.append(Spacer(1, 0.45 * cm))


def build_pdf(base_url: str, project_key: str, token: str, output_path: Path) -> None:
    project = sonar_get(base_url, token, "/api/components/show", {"component": project_key})
    quality = sonar_get(base_url, token, "/api/qualitygates/project_status", {"projectKey": project_key})
    measures = sonar_get(
        base_url,
        token,
        "/api/measures/component",
        {
            "component": project_key,
            "metricKeys": (
                "alert_status,coverage,line_coverage,branch_coverage,lines_to_cover,"
                "uncovered_lines,duplicated_lines_density,ncloc,code_smells,bugs,"
                "vulnerabilities,security_hotspots,reliability_rating,security_rating,"
                "sqale_rating,sqale_index,new_coverage,new_duplicated_lines_density,new_violations"
            ),
        },
    )
    issues = sonar_get(base_url, token, "/api/issues/search", {"components": project_key, "resolved": "false", "ps": "100"})
    analyses = sonar_get(base_url, token, "/api/project_analyses/search", {"project": project_key, "ps": "5"})
    issue_facets = sonar_get(
        base_url,
        token,
        "/api/issues/search",
        {
            "components": project_key,
            "resolved": "false",
            "ps": "1",
            "facets": "severities,types,statuses,impactSoftwareQualities",
        },
    )

    component = project.get("component", {})
    quality_status = quality.get("projectStatus", {}).get("status", "UNKNOWN")
    conditions = quality.get("projectStatus", {}).get("conditions", [])
    analysis_list = analyses.get("analyses") or []
    latest_analysis_date = analysis_list[0].get("date", "N/A") if analysis_list else "N/A"
    latest_version = analysis_list[0].get("projectVersion", "N/A") if analysis_list else "N/A"
    metrics = {m.get("metric"): m.get("value", "-") for m in measures.get("component", {}).get("measures", [])}
    facets_map = {facet.get("property"): facet.get("values", []) for facet in issue_facets.get("facets", [])}

    suite_spec = parse_junit(Path.cwd() / "multi_agent" / "tests" / "phase5_spec_25_report.xml")

    styles = getSampleStyleSheet()
    title = ParagraphStyle("TitleCustom", parent=styles["Title"], fontSize=18, leading=22, textColor=colors.HexColor("#12325A"))
    h2 = ParagraphStyle("H2", parent=styles["Heading2"], fontSize=13, leading=16, textColor=colors.HexColor("#1F4B87"))
    body = ParagraphStyle("Body", parent=styles["BodyText"], fontSize=9.5, leading=12)

    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=A4,
        leftMargin=1.4 * cm,
        rightMargin=1.4 * cm,
        topMargin=1.2 * cm,
        bottomMargin=1.2 * cm,
    )

    story: list = []
    story.append(Paragraph("SonarQube Phase 5 Detailed Report", title))
    story.append(Spacer(1, 0.2 * cm))
    story.append(
        Paragraph(
            f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}<br/>"
            f"Project Key: {project_key}<br/>"
            f"Project Name: {component.get('name', 'N/A')}<br/>"
            f"Dashboard: {base_url}/dashboard?id={project_key}<br/>"
            f"Latest Analysis: {latest_analysis_date}<br/>"
            f"Project Version: {latest_version}",
            body,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    coverage = float(metrics.get("coverage", "0") or 0)
    coverage_status = "PASS" if coverage >= 85 else "FAIL"
    coverage_color = "#0B7A34" if coverage >= 85 else "#B42318"
    quality_color = "#0B7A34" if quality_status == "OK" else "#B42318"

    story.append(Paragraph("Executive Summary", h2))
    story.append(
        Paragraph(
            (
                f"Quality Gate: <font color='{quality_color}'><b>{quality_status}</b></font><br/>"
                f"Coverage: <b>{coverage:.1f}%</b> (Target >= 85%: <font color='{coverage_color}'><b>{coverage_status}</b></font>)<br/>"
                f"Open Issues: <b>{issues.get('total', 0)}</b><br/>"
                f"Bugs: <b>{metrics.get('bugs', '-')}</b>, Vulnerabilities: <b>{metrics.get('vulnerabilities', '-')}</b>, "
                f"Code Smells: <b>{metrics.get('code_smells', '-')}</b>, Security Hotspots: <b>{metrics.get('security_hotspots', '-')}</b>"
            ),
            body,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    story.append(Paragraph("Quality Gate Conditions", h2))
    qg_rows = [["Metric", "Operator", "Error Threshold", "Actual", "Status"]]
    if conditions:
        for cond in conditions:
            qg_rows.append(
                [
                    str(cond.get("metricKey", "-")),
                    str(cond.get("comparator", "-")),
                    str(cond.get("errorThreshold", "-")),
                    str(cond.get("actualValue", "-")),
                    str(cond.get("status", "-")),
                ]
            )
    else:
        qg_rows.append(["-", "-", "-", "-", "No condition data"])
    add_table(story, qg_rows, [4 * cm, 2.5 * cm, 3 * cm, 2.5 * cm, 3.2 * cm])
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Dashboard Measures", h2))
    measure_rows = [["Metric", "Value"]]
    for key in [
        "alert_status",
        "coverage",
        "line_coverage",
        "branch_coverage",
        "new_coverage",
        "new_duplicated_lines_density",
        "new_violations",
        "lines_to_cover",
        "uncovered_lines",
        "duplicated_lines_density",
        "ncloc",
        "code_smells",
        "bugs",
        "vulnerabilities",
        "security_hotspots",
    ]:
        measure_rows.append([key, str(metrics.get(key, "-"))])
    add_table(story, measure_rows, [7.5 * cm, 7.5 * cm], font_size=8)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Issue Distribution", h2))
    issue_dist_rows = [
        ["Facet", "Breakdown"],
        ["Severity", format_facet(facets_map.get("severities", []))],
        ["Type", format_facet(facets_map.get("types", []))],
        ["Status", format_facet(facets_map.get("statuses", []))],
        ["Software Quality", format_facet(facets_map.get("impactSoftwareQualities", []))],
    ]
    add_table(story, issue_dist_rows, [4.5 * cm, 10.5 * cm], font_size=8)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Testing Summary (Phase 5 Dashboard Context)", h2))
    recap_rows = [
        ["Suite", "Total", "Passed", "Skipped", "Failed/Errors"],
        ["Phase 5 Spec + Additional Cases", str(suite_spec["total"]), str(suite_spec["passed"]), str(suite_spec["skipped"]), str(suite_spec["failed"])],
    ]
    add_table(story, recap_rows, [5.5 * cm, 2.2 * cm, 2.2 * cm, 2.2 * cm, 2.7 * cm], font_size=8)
    story.append(Spacer(1, 0.4 * cm))

    append_test_case_details(story, h2, "Detailed Test Cases: Phase 5 Multi-Agent Suite", suite_spec)

    story.append(Paragraph("Open Issues (Top 100 from Dashboard)", h2))
    issue_rows = [["Severity", "Type", "Component", "Message"]]
    issue_list = issues.get("issues", [])
    if not issue_list:
        issue_rows.append(["-", "-", "-", "No open issues"])
    else:
        for issue in issue_list:
            message = (issue.get("message") or "").replace("\n", " ")
            if len(message) > 120:
                message = message[:117] + "..."
            issue_rows.append(
                [
                    issue.get("severity", "-"),
                    issue.get("type", "-"),
                    issue.get("component", "-"),
                    message,
                ]
            )
    add_table(story, issue_rows, [2 * cm, 2 * cm, 4.8 * cm, 6.8 * cm], font_size=8)

    doc.build(story)


def main() -> None:
    token = os.getenv("SONAR_TOKEN", "").strip()
    base_url = os.getenv("SONAR_HOST_URL", "http://localhost:9001").strip().rstrip("/")
    project_key = "inventory-management-phase5"

    if not token:
        raise RuntimeError("SONAR_TOKEN is required in environment")

    output = Path.cwd() / f"SonarQube_Phase5_Detailed_Report_{datetime.now().strftime('%Y-%m-%d_%H%M%S')}.pdf"
    build_pdf(base_url, project_key, token, output)
    print(f"PDF generated: {output}")


if __name__ == "__main__":
    main()
