#pragma once

#include <string_view>

// Release package format: app.p<24 lowercase hex>.m<12 lowercase hex>.
// Match only its main server role; never match client user-service processes.
inline bool is_random_fork_server(std::string_view name) {
    constexpr std::string_view prefix = "app.p", middle = ".m", suffix = ":service";
    constexpr size_t first = 24, second = 12;
    if (name.size() != prefix.size() + first + middle.size() + second + suffix.size()) return false;
    if (name.substr(0, prefix.size()) != prefix) return false;
    if (name.substr(prefix.size() + first, middle.size()) != middle) return false;
    if (name.substr(name.size() - suffix.size()) != suffix) return false;
    auto hex = [](char ch) { return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'); };
    for (size_t i = prefix.size(); i < prefix.size() + first; ++i) if (!hex(name[i])) return false;
    size_t start = prefix.size() + first + middle.size();
    for (size_t i = start; i < start + second; ++i) if (!hex(name[i])) return false;
    return true;
}

inline bool is_replaced_shizuku_server(std::string_view name, std::string_view current) {
    return name == current || name == "shizuku_server" || is_random_fork_server(name);
}
