#!/usr/bin/env python3
"""
JaCoCo Coverage Inspector & Validator
Parses target/site/jacoco/jacoco.xml to check line & branch coverage thresholds
and pinpoint exact missed branches and lines.
"""

import sys
import os
import argparse
import xml.etree.ElementTree as ET

def check_coverage(jacoco_path="target/site/jacoco/jacoco.xml", file_filter=None, threshold=0.90):
    if not os.path.exists(jacoco_path):
        print(f"Error: JaCoCo report not found at {jacoco_path}", file=sys.stderr)
        return 1

    tree = ET.parse(jacoco_path)
    root = tree.getroot()

    total_branch_missed = 0
    total_branch_covered = 0
    total_inst_missed = 0
    total_inst_covered = 0

    class_stats = []

    for pkg in root.findall("package"):
        for cls in pkg.findall("class"):
            cls_name = cls.get("name")
            b_missed = 0
            b_covered = 0
            i_missed = 0
            i_covered = 0
            for counter in cls.findall("counter"):
                ctype = counter.get("type")
                if ctype == "BRANCH":
                    b_missed = int(counter.get("missed"))
                    b_covered = int(counter.get("covered"))
                    total_branch_missed += b_missed
                    total_branch_covered += b_covered
                elif ctype == "INSTRUCTION":
                    i_missed = int(counter.get("missed"))
                    i_covered = int(counter.get("covered"))
                    total_inst_missed += i_missed
                    total_inst_covered += i_covered

            if b_missed > 0 or i_missed > 0:
                class_stats.append({
                    "class": cls_name,
                    "branch_missed": b_missed,
                    "branch_total": b_missed + b_covered,
                    "inst_missed": i_missed,
                    "inst_total": i_missed + i_covered
                })

    total_branches = total_branch_missed + total_branch_covered
    branch_ratio = total_branch_covered / total_branches if total_branches > 0 else 1.0
    total_inst = total_inst_missed + total_inst_covered
    inst_ratio = total_inst_covered / total_inst if total_inst > 0 else 1.0

    print("=" * 65)
    print("                 JACOCO COVERAGE REPORT SUMMARY")
    print("=" * 65)
    branch_status = "PASS" if branch_ratio >= threshold else "FAIL"
    inst_status = "PASS" if inst_ratio >= threshold else "FAIL"
    print(f"Branch Coverage:      {branch_ratio * 100:6.2f}% ({total_branch_covered}/{total_branches}) [{branch_status}] (Min: {threshold*100:.0f}%)")
    print(f"Instruction Coverage: {inst_ratio * 100:6.2f}% ({total_inst_covered}/{total_inst}) [{inst_status}] (Min: {threshold*100:.0f}%)")
    print("-" * 65)

    # Sort classes by branch missed desc
    class_stats.sort(key=lambda x: x["branch_missed"], reverse=True)

    print("Classes with missed branches:")
    for stat in class_stats:
        if stat["branch_missed"] > 0:
            b_pct = (stat["branch_total"] - stat["branch_missed"]) / stat["branch_total"] * 100 if stat["branch_total"] > 0 else 100
            print(f"  - {stat['class']:<60} missed {stat['branch_missed']:2d}/{stat['branch_total']:2d} ({b_pct:5.1f}%)")

    if file_filter:
        print("\n" + "=" * 65)
        print(f"Detailed line-by-line misses for: {file_filter}")
        print("=" * 65)
        found = False
        for src in root.iter("sourcefile"):
            if file_filter.lower() in src.get("name", "").lower():
                found = True
                print(f"Source: {src.get('name')}")
                for line in src.findall("line"):
                    mb = int(line.get("mb", 0))
                    mi = int(line.get("mi", 0))
                    nr = line.get("nr")
                    if mb > 0 or mi > 0:
                        print(f"  Line {nr:>4}: missed {mb} branch(es), {mi} instruction(s)")
        if not found:
            print(f"No matching sourcefile found for '{file_filter}'")

    print("=" * 65)
    return 0 if (branch_ratio >= threshold and inst_ratio >= threshold) else 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="JaCoCo Coverage Inspector")
    parser.add_argument("--file", "-f", help="Filter line details for a specific source file name")
    parser.add_argument("--path", "-p", default="target/site/jacoco/jacoco.xml", help="Path to jacoco.xml")
    parser.add_argument("--threshold", "-t", type=float, default=0.90, help="Minimum coverage ratio threshold (default: 0.90)")
    args = parser.parse_args()

    sys.exit(check_coverage(args.path, args.file, args.threshold))
