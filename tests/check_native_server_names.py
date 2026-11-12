"""Compile the production matcher and check cross-build replacement boundaries."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = r'''
#include "server_identity.h"
#include <cassert>
#include <iostream>
#include <string>
int main() {
    const std::string old = "app.p0123456789abcdef01234567.m012345abcdef:service";
    const std::string current = "app.pffffffffffffffffffffffff.mffffffffffff:service";
    assert(is_replaced_shizuku_server(old, current));
    assert(is_replaced_shizuku_server(current, current));
    assert(is_replaced_shizuku_server("shizuku_server", current));
    assert(is_replaced_shizuku_server("custom.manager:service", "custom.manager:service"));
    assert(!is_replaced_shizuku_server("custom.other:service", current));
    for (const auto& name : {"li.songe.gkd:shizuku-user-service", "dev.studyfloat:capture",
            "com.android.shell", "app.p0123456789abcdef01234567.m012345abcdef:capture",
            "app.p0123456789abcdef01234567.m012345abcdef", "app.p123.m123:service",
            "app.p0123456789abcdef01234567.m012345abcdeg:service",
            "app.p0123456789abcdef01234567.m012345abcdef:service-extra"}) {
        assert(!is_replaced_shizuku_server(name, current));
    }
    for (size_t i = 0; i < old.size(); ++i) {
        auto truncated = old.substr(0, i);
        assert(!is_random_fork_server(truncated));
    }
    std::cout << "PASS cross-build main-server replacement boundaries\n";
}
'''
with tempfile.TemporaryDirectory() as tmp:
    cpp, binary = Path(tmp) / "test.cpp", Path(tmp) / "test"
    cpp.write_text(source)
    subprocess.run(["c++", "-std=c++17", "-Wall", "-Wextra", "-Werror", "-I",
                    str(root / "manager/src/main/jni"), str(cpp), "-o", str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
