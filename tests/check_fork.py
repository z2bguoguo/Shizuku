"""Checks the fork's cross-language identity and security boundaries before building."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r = Path(__file__).resolve().parents[1]
def read(p): return (r / p).read_text()
for p in (r / 'manager/src/main/res').glob('*/privacy.xml'):
    ET.parse(p)
a = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(r / 'manager/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
launcher = next(x for x in app.findall('activity-alias') if x.get(a+'name') == '.Launcher')
assert launcher.get(a+'targetActivity') == '.MainActivity'
assert any(x.get(a+'name') == '.MainActivity' for x in app.findall('activity'))
assert 'applicationId rootProject.ext.managerApplicationId' in read('manager/build.gradle')
assert 'MANAGER_APPLICATION_ID' in read('common/build.gradle')
assert 'MANAGER_APPLICATION_ID' in read('starter/src/main/java/moe/shizuku/starter/ServiceStarter.java')
assert 'MANAGER_APPLICATION_ID' in read('shell/src/main/java/rikka/shizuku/shell/ShizukuShellLoader.java')
assert '#define PACKAGE_NAME MANAGER_APPLICATION_ID' in read('manager/src/main/jni/starter.cpp')
s = read('server/src/main/java/rikka/shizuku/server/ShizukuService.java')
assert 'if (isSdkBlocked(Binder.getCallingUid())) return false;' in s
assert 'enforceManagerPermission("sdkBlacklist")' in s
assert 'enforceManagerPermission("getApplications")' in s
assert '((ShizukuService) binder).isSdkBlocked(pi.applicationInfo.uid)' in s
assert 'if (isSdkBlocked(Binder.getCallingUid())) return;' in s
assert 'sShizukuService.isSdkBlocked(uid)' in read('server/src/main/java/rikka/shizuku/server/BinderSender.java')
# Compatibility key must not follow randomized identity.
assert 'extra.putParcelable("moe.shizuku.privileged.api.intent.extra.BINDER"' in s
assert not re.search(r'create.pull.request|pull_request_target', read('.github/workflows/app.yml'), re.I)
print('Fork cross-module and manifest checks passed')
