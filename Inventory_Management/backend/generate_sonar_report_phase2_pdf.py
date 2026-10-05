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
    with urlopen(req, timeout=30) as response:  # nosec B310 - localhost SonarQube API access
        return json.loads(response.read().decode("utf-8"))


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
                "sqale_rating,sqale_index"
            ),
        },
    )
    issues = sonar_get(base_url, token, "/api/issues/search", {"components": project_key, "resolved": "false", "ps": "100"})
    analyses = sonar_get(base_url, token, "/api/project_analyses/search", {"project": project_key, "ps": "1"})
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
    latest_analysis_date = "N/A"
    analysis_list = analyses.get("analyses") or []
    if analysis_list:
        latest_analysis_date = analysis_list[0].get("date", "N/A")

    metrics = {m.get("metric"): m.get("value", "-") for m in measures.get("component", {}).get("measures", [])}

    def parse_junit(path: Path) -> dict:
        if not path.exists():
            return {"total": 0, "passed": 0, "skipped": 0, "failed": 0, "cases": []}
        suite = ET.parse(path).getroot().find("testsuite")
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

    suite20 = parse_junit(Path.cwd() / "rag" / "tests" / "phase2_20_report.xml")
    suite25 = parse_junit(Path.cwd() / "rag" / "tests" / "phase2_25_report.xml")

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

    story = []
    story.append(Paragraph("SonarQube Phase 2 Report", title))
    story.append(Spacer(1, 0.2 * cm))
    story.append(
        Paragraph(
            f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}<br/>"
            f"Project Key: {project_key}<br/>"
            f"Project Name: {component.get('name', 'N/A')}<br/>"
            f"Dashboard: {base_url}/dashboard?id={project_key}<br/>"
            f"Latest Analysis: {latest_analysis_date}",
            body,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    coverage = float(metrics.get("coverage", "0") or 0)
    coverage_status = "PASS" if coverage >= 85 else "FAIL"
    coverage_color = "#0B7A34" if coverage >= 85 else "#B42318"
    conditions = quality.get("projectStatus", {}).get("conditions", [])

    facets_map = {facet.get("property"): facet.get("values", []) for facet in issue_facets.get("facets", [])}

    def format_facet(values: list[dict]) -> str:
        if not values:
            return "none"
        return ", ".join(f"{v.get('val')}={v.get('count')}" for v in values)

    story.append(Paragraph("Executive Summary", h2))
    story.append(
        Paragraph(
            (
                f"Quality Gate: <b>{quality_status}</b><br/>"
                f"Coverage: <b>{coverage:.1f}%</b> (Target >= 85%: <font color='{coverage_color}'><b>{coverage_status}</b></font>)<br/>"
                f"Open Issues: <b>{issues.get('total', 0)}</b><br/>"
                f"Bugs: <b>{metrics.get('bugs', '-')}</b>, Vulnerabilities: <b>{metrics.get('vulnerabilities', '-')}</b>, "
                f"Code Smells: <b>{metrics.get('code_smells', '-')}</b>"
            ),
            body,
        )
    )
    story.append(Spacer(1, 0.35 * cm))

    quality_color = "#0B7A34" if quality_status == "OK" else "#B42318"
    story.append(Paragraph("Quality Gate", h2))
    story.append(Paragraph(f"Status: <font color='{quality_color}'><b>{quality_status}</b></font>", body))
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

    qg_table = Table(qg_rows, colWidths=[4 * cm, 2.5 * cm, 3 * cm, 2.5 * cm, 3.2 * cm], repeatRows=1)
    qg_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
                ("FONTSIZE", (0, 0), (-1, -1), 8),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(qg_table)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Quality Ratings", h2))
    ratings_rows = [
        ["Dimension", "Rating"],
        ["Reliability", metrics.get("reliability_rating", "-")],
        ["Security", metrics.get("security_rating", "-")],
        ["Maintainability", metrics.get("sqale_rating", "-")],
    ]
    ratings_table = Table(ratings_rows, colWidths=[7.5 * cm, 7.5 * cm])
    ratings_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(ratings_table)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Key Measures", h2))
    metric_rows = [["Metric", "Value"]]
    for key in [
        "alert_status",
        "coverage",
        "line_coverage",
        "branch_coverage",
        "lines_to_cover",
        "uncovered_lines",
        "ncloc",
        "code_smells",
        "bugs",
        "vulnerabilities",
        "security_hotspots",
        "duplicated_lines_density",
        "sqale_index",
    ]:
        metric_rows.append([key, str(metrics.get(key, "-"))])

    metric_table = Table(metric_rows, colWidths=[7.5 * cm, 7.5 * cm])
    metric_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(metric_table)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Issue Distribution", h2))
    issue_dist_rows = [
        ["Facet", "Breakdown"],
        ["Severity", format_facet(facets_map.get("severities", []))],
        ["Type", format_facet(facets_map.get("types", []))],
        ["Status", format_facet(facets_map.get("statuses", []))],
        ["Software Quality", format_facet(facets_map.get("impactSoftwareQualities", []))],
    ]
    issue_dist_table = Table(issue_dist_rows, colWidths=[4.5 * cm, 10.5 * cm])
    issue_dist_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(issue_dist_table)
    story.append(Spacer(1, 0.4 * cm))

    story.append(Paragraph("Test Execution Recap", h2))
    recap_rows = [
        ["Suite", "Total", "Passed", "Skipped", "Failed/Errors"],
        ["Phase 2 Spec (20)", str(suite20["total"]), str(suite20["passed"]), str(suite20["skipped"]), str(suite20["failed"])],
        ["Phase 2 Extended (25)", str(suite25["total"]), str(suite25["passed"]), str(suite25["skipped"]), str(suite25["failed"])],
    ]
    recap_table = Table(recap_rows, colWidths=[5.5 * cm, 2.2 * cm, 2.2 * cm, 2.2 * cm, 2.7 * cm])
    recap_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D0DBEC")),
                ("ALIGN", (1, 1), (-1, -1), "CENTER"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FBFF")]),
            ]
        )
    )
    story.append(recap_table)
    story.append(Spacer(1, 0.4 * cm))

    def append_test_case_details(title_text: str, suite: dict) -> None:
        story.append(Paragraph(title_text, h2))
        detail_rows = [["#", "Test Case", "Status", "Time (s)", "Details"]]
        for i, case in enumerate(suite["cases"], start=1):
            detail = case["detail"] or "-"
            if len(detail) > 120:
                detail = detail[:117] + "..."
            detail_rows.append([str(i), case["name"], case["status"], case["time"], detail])

        detail_table = Table(detail_rows, colWidths=[0.8 * cm, 6.4 * cm, 1.8 * cm, 1.7 * cm, 5.8 * cm], repeatRows=1)
        table_style_cmds = [
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
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#166534")))
            elif case["status"] == "SKIPPED":
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#92400E")))
            else:
                table_style_cmds.append(("TEXTCOLOR", (2, row_index), (2, row_index), colors.HexColor("#B91C1C")))

        detail_table.setStyle(TableStyle(table_style_cmds))
        story.append(detail_table)
        story.append(Spacer(1, 0.45 * cm))

    append_test_case_details("Detailed Test Cases: Phase 2 Spec (20)", suite20)
    append_test_case_details("Detailed Test Cases: Phase 2 Extended (25)", suite25)

    story.append(Paragraph("Open Issues (Top 100)", h2))
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

    issue_table = Table(issue_rows, colWidths=[2 * cm, 2 * cm, 4.8 * cm, 6.8 * cm], repeatRows=1)
    issue_table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#EAF1FB")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#12325A")),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#D0DBEC")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("FONTSIZE", (0, 0), (-1, -1), 8),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#FBFDFF")]),
            ]
        )
    )
    story.append(issue_table)

    doc.build(story)


def main() -> None:
    token = os.getenv("SONAR_TOKEN", "").strip()
    base_url = os.getenv("SONAR_HOST_URL", "http://localhost:9001").strip().rstrip("/")
    project_key = "inventory-management-phase2"

    if not token:
        raise RuntimeError("SONAR_TOKEN is required in environment")

    output = Path.cwd() / f"SonarQube_Phase2_Report_{datetime.now().strftime('%Y-%m-%d_%H%M%S')}.pdf"
    build_pdf(base_url, project_key, token, output)
    print(f"PDF generated: {output}")


if __name__ == "__main__":
    main()
