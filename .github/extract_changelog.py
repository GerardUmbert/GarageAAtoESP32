import sys
import re

version, oldest = sys.argv[1], sys.argv[2]
text = open("CHANGELOG.md").read()
sections = re.split(r"(?=^## \[)", text, flags=re.MULTILINE)
collecting, parts = False, []
for s in sections:
    m = re.match(r"^## \[([^\]]+)\]", s)
    if not m:
        continue
    v = m.group(1)
    if v == version:
        collecting = True
    if collecting:
        if oldest and v == oldest:
            break
        parts.append(s.strip())
print("\n\n---\n\n".join(parts))
