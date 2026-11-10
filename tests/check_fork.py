"""Validate release metadata and independently addressable launcher recovery."""
from pathlib import Path
import xml.etree.ElementTree as ET
r = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(r / 'manager/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
activities = {x.get(a+'name'): x for x in app.findall('activity')}
launchers = []
for entry in list(app.findall('activity')) + list(app.findall('activity-alias')):
    for category in entry.findall('intent-filter/category'):
        if category.get(a+'name') == 'android.intent.category.LAUNCHER':
            launchers.append(entry)
assert len(launchers) == 1, 'Exactly one independently disableable launcher is required'
launcher = launchers[0]
assert launcher.tag == 'activity-alias', 'Hiding the icon must not disable the recovery activity'
assert launcher.get(a+'targetActivity') in activities
assert activities[launcher.get(a+'targetActivity')].get(a+'exported') == 'true'
assert launcher.get(a+'exported') == 'true'
base = {x.get('name'): x.text for x in ET.parse(r / 'manager/src/main/res/values/privacy.xml').getroot()}
for p in (r / 'manager/src/main/res').glob('*/privacy.xml'):
    strings = {x.get('name'): x.text for x in ET.parse(p).getroot()}
    assert strings.keys() == base.keys(), f'Missing translations in {p}'
    for key, value in strings.items():
        assert value and ('%1$s' in value) == ('%1$s' in base[key]), f'Format argument mismatch: {p}:{key}'
print('Launcher recovery and localized format contracts passed')
