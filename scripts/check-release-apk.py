#!/usr/bin/env python3
"""Check optimized release entry names and size without extra Python packages.

This checks DEX contents, not device runtime behavior. Pairing and retained
instrumentation still require a physical-device regression check after R8 changes.
"""

import argparse
import json
import struct
import zipfile
from pathlib import Path


def read_uleb(data, offset):
    value = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, offset
        shift += 7
        if shift >= 35:
            raise ValueError("Invalid DEX ULEB128")


def dex_classes(data):
    """Return descriptor -> declared method names for a standard Android DEX."""
    if not data.startswith(b"dex\n"):
        raise ValueError("Expected a standard DEX")
    string_count, strings_offset, type_count, types_offset = struct.unpack_from("<4I", data, 56)
    method_count, methods_offset, class_count, classes_offset = struct.unpack_from("<4I", data, 88)
    strings = []
    for index in range(string_count):
        start = struct.unpack_from("<I", data, strings_offset + index * 4)[0]
        _, start = read_uleb(data, start)
        end = data.index(b"\0", start)
        strings.append(data[start:end].decode("utf-8", errors="replace"))
    types = [strings[struct.unpack_from("<I", data, types_offset + index * 4)[0]]
             for index in range(type_count)]
    methods = [strings[struct.unpack_from("<I", data, methods_offset + index * 8 + 4)[0]]
               for index in range(method_count)]
    classes = {}
    for index in range(class_count):
        entry = classes_offset + index * 32
        descriptor = types[struct.unpack_from("<I", data, entry)[0]]
        offset = struct.unpack_from("<I", data, entry + 24)[0]
        names = set()
        if offset:
            counts = []
            for _ in range(4):
                count, offset = read_uleb(data, offset)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, offset = read_uleb(data, offset)  # field index delta
                _, offset = read_uleb(data, offset)  # access flags
            for count in counts[2:]:
                method_index = 0
                for _ in range(count):
                    delta, offset = read_uleb(data, offset)
                    method_index += delta
                    _, offset = read_uleb(data, offset)  # access flags
                    _, offset = read_uleb(data, offset)  # code offset
                    names.add(methods[method_index])
        classes[descriptor] = names
    return classes, strings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--max-bytes", type=int)
    args = parser.parse_args()
    classes = {}
    strings = set()
    with zipfile.ZipFile(args.apk) as apk:
        entries = apk.infolist()
        dex_entries = [entry for entry in entries
                       if entry.filename.startswith("classes") and entry.filename.endswith(".dex")]
        if not dex_entries:
            raise SystemExit("FAIL: no DEX files")
        for entry in dex_entries:
            found, pool = dex_classes(apk.read(entry))
            classes.update(found)
            strings.update(pool)

    required = {
        "com.svenuks.imsforpixel.MainActivity": {"<init>", "onCreate"},
        "com.svenuks.imsforpixel.BrokerInstrumentation": {"<init>", "onCreate"},
        "com.svenuks.imsforpixel.GuardianInstrumentation": {"<init>", "onCreate"},
        "com.svenuks.imsforpixel.GuardianService": {"<init>", "onStartCommand"},
        "com.svenuks.imsforpixel.GuardianStopReceiver": {"<init>", "onReceive"},
        "org.lsposed.hiddenapibypass.HiddenApiBypass": set(),
        "org.bouncycastle.jce.provider.BouncyCastleProvider": set(),
        "org.bouncycastle.jcajce.provider.asymmetric.RSA$Mappings": set(),
        "org.bouncycastle.jcajce.provider.digest.SHA256$Mappings": set(),
        "org.bouncycastle.jcajce.provider.asymmetric.X509$Mappings": set(),
        "org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory": set(),
    }
    failures = []
    for name, expected_methods in required.items():
        descriptor = "L" + name.replace(".", "/") + ";"
        if descriptor not in classes:
            failures.append("missing named entry: " + name)
        elif not expected_methods <= classes[descriptor]:
            failures.append("missing entry methods: " + name)
    if "ims.sa_disable_policy_int" in strings:
        failures.append("removed Keep SA configuration key has returned")
    if "waiting_debug_off" in strings:
        failures.append("removed developer-options recovery gate has returned")
    forbidden_debug = {"development_settings_enabled", "adb_enabled", "adb_wifi_enabled",
                       "retained_ui_automation_shell", "debug_flags_ui", "debug_flags_keeper",
                       "debug_flags", "debug_off"}
    for value in sorted(forbidden_debug & strings):
        failures.append("removed developer-options detection remains: " + value)
    for descriptor in classes:
        if descriptor.startswith("Lcom/svenuks/imsforpixel/DebugSettings"):
            failures.append("removed debug-state reader remains: " + descriptor)
        if descriptor.startswith("Lcom/tananaev/adblib/") or descriptor.endswith("/ImsQueryTool;"):
            failures.append("obsolete code remains: " + descriptor)
    size = args.apk.stat().st_size
    if args.max_bytes is not None and size > args.max_bytes:
        failures.append(f"APK is {size} bytes, budget is {args.max_bytes}")
    report = {
        "apk_bytes": size,
        "dex_uncompressed_bytes": sum(entry.file_size for entry in dex_entries),
        "resource_uncompressed_bytes": sum(entry.file_size for entry in entries
                                           if entry.filename.startswith("res/")
                                           or entry.filename == "resources.arsc"),
        "native_uncompressed_bytes": sum(entry.file_size for entry in entries
                                         if entry.filename.startswith("lib/")),
        "class_count": len(classes),
        "named_entry_checks": len(required),
        "failures": failures,
    }
    print(json.dumps(report, indent=2))
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
