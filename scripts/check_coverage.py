import sys
import xml.etree.ElementTree as ET

def check_coverage(xml_path='target/site/jacoco/jacoco.xml'):
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

    print("\n--- Missed Branches by Class ---")
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
                        print(f"{name}: {covered}/{total} ({pct:.1f}%) missed: {missed}")

    print("\n--- Detailed Line Coverage for Target Source Files ---")
    for package in root.findall('package'):
        for sf in package.findall('sourcefile'):
            name = sf.get('name')
            if any(t in name for t in ['TwelveData', 'InstrumentService', 'MarketData', 'FxRate']):
                lines_with_misses = []
                for line in sf.findall('line'):
                    mb = int(line.get('mb'))
                    cb = int(line.get('cb'))
                    if mb > 0:
                        lines_with_misses.append(f"Line {line.get('nr')}: missed {mb}, covered {cb}")
                if lines_with_misses:
                    print(f"=== {name} ===")
                    for l in lines_with_misses:
                        print(f"  {l}")

if __name__ == '__main__':
    check_coverage()
