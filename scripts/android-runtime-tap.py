import re
import subprocess
import sys
import xml.etree.ElementTree as ET

label = sys.argv[1]
root = ET.parse('runtime-diagnostics/window.xml').getroot()
nodes = [n for n in root.iter('node') if n.get('text') == label or n.get('content-desc') == label]
if not nodes:
    raise SystemExit('No visible control: ' + label)
node = nodes[-1]
x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
subprocess.run(['adb', 'shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
print('Tapped', label)
