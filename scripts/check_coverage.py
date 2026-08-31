import sys
import xml.etree.ElementTree as ET

def check_coverage(xml_path='target/site/jacoco/jacoco.xml', target_filter=None, top_n=None, show_details=False):
    try:
        tree = ET.parse(xml_path)
    except Exception as e:
        print(f"Error parsing {xml_path}: {e}")
        sys.exit(1)
    
    root = tree.getroot()
    total_covered = 0
    total_missed = 0

    for counter in root.findall('counter'):
        if counter.get('type') == 'BRANCH':
            total_missed = int(counter.get('missed'))
            total_covered = int(counter.get('covered'))
            total = total_missed + total_covered
            pct = (total_covered / total) * 100 if total > 0 else 0
            print(f"Total branches: {total_covered}/{total} ({pct:.2f}%) missed: {total_missed}")

    missed_classes = []
    for package in root.findall('package'):
        for cls in package.findall('class'):
            name = cls.get('name')
            for counter in cls.findall('counter'):
                if counter.get('type') == 'BRANCH':
                    missed = int(counter.get('missed'))
                    covered = int(counter.get('covered'))
                    total = missed + covered
                    if missed > 0:
                        pct = (covered / total) * 100
                        missed_classes.append((missed, covered, total, pct, name))

    # Sort descending by number of missed branches
    missed_classes.sort(key=lambda x: x[0], reverse=True)

    print("\n--- Missed Branches by Class (Sorted by Missed Count) ---")
    display_classes = missed_classes[:top_n] if top_n else missed_classes
    for missed, covered, total, pct, name in display_classes:
        if target_filter and not any(t.lower() in name.lower() for t in target_filter):
            continue
        print(f"{name}: {covered}/{total} ({pct:.1f}%) missed: {missed}")

    if show_details or target_filter:
        print("\n--- Detailed Missed Branches by Source File ---")
        for package in root.findall('package'):
            for sf in package.findall('sourcefile'):
                name = sf.get('name')
                if target_filter and not any(t.lower() in name.lower() for t in target_filter):
                    continue
                lines_with_misses = []
                for line in sf.findall('line'):
                    mb = int(line.get('mb'))
                    cb = int(line.get('cb'))
                    mi = int(line.get('mi'))
                    ci = int(line.get('ci'))
                    if mb > 0 or mi > 0:
                        lines_with_misses.append(f"Line {line.get('nr')}: missed branches={mb} (covered={cb}), missed instructions={mi}")
                if lines_with_misses:
                    print(f"=== {name} ===")
                    for l in lines_with_misses:
                        print(f"  {l}")

if __name__ == '__main__':
    args = sys.argv[1:]
    top_n = None
    show_details = False
    targets = []
    for arg in args:
        if arg.startswith('--top='):
            top_n = int(arg.split('=')[1])
        elif arg == '--top':
            top_n = 20
        elif arg == '--detail' or arg == '--details':
            show_details = True
        else:
            targets.append(arg)
    target_filter = targets if targets else None
    check_coverage(target_filter=target_filter, top_n=top_n, show_details=show_details)

