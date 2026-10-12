#!/usr/bin/env python3
"""Build metadata for XGram CI artifacts (Python 3.6+, standard library only).

  build_info.py make  --apk-dir DIR --flavor F --build-type T --out-dir OUT
      Reads DIR/output-metadata.json (written by the Android Gradle Plugin), copies the APKs into OUT and
      writes OUT/build-info.json: versionCode, versionName, size and SHA-256 of each APK, plus commit and run.

  build_info.py merge --in-dir ARTIFACTS --out-dir OUT [--github-output FILE]
      Merges ARTIFACTS/*/build-info.json (one per downloaded artifact) into OUT/build-info.json, copies the
      APKs into OUT after checking their SHA-256, and prints/sets `version_name` (the versionName without the
      ABI/debug suffix, e.g. 0.29.1.1817.9) and `apk_count`.
"""
import argparse
import glob
import hashlib
import json
import os
import shutil
import sys


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def fail(message):
    sys.exit("build_info.py: " + message)


def base_version(version_name):
    # 0.29.1.1817.9-arm64-v8a-debug -> 0.29.1.1817.9 ; 0.29.1.1817.9 (universal release) -> 0.29.1.1817.9
    return version_name.split("-", 1)[0]


def run_info():
    env = os.environ
    return {
        "commit": env.get("GITHUB_SHA", ""),
        "repository": env.get("GITHUB_REPOSITORY", ""),
        "run_id": env.get("GITHUB_RUN_ID", ""),
        "run_number": env.get("GITHUB_RUN_NUMBER", ""),
        "workflow": env.get("GITHUB_WORKFLOW", ""),
        "app_version": env.get("XGRAM_APP_VERSION", ""),
    }


def cmd_make(args):
    meta_path = os.path.join(args.apk_dir, "output-metadata.json")
    if not os.path.isfile(meta_path):
        fail("not found: %s (did the Gradle build run?)" % meta_path)
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)
    os.makedirs(args.out_dir, exist_ok=True)
    apks = []
    for element in meta.get("elements", []):
        name = element.get("outputFile", "")
        src = os.path.join(args.apk_dir, name)
        if not name.endswith(".apk") or not os.path.isfile(src):
            fail("APK listed in output-metadata.json is missing: %s" % src)
        dst = os.path.join(args.out_dir, name)
        shutil.copy2(src, dst)
        apks.append({
            "file": name,
            "flavor": args.flavor,
            "build_type": args.build_type,
            "versionCode": element.get("versionCode"),
            "versionName": element.get("versionName", ""),
            "size": os.path.getsize(dst),
            "sha256": sha256(dst),
        })
    if not apks:
        fail("no APKs in %s" % meta_path)
    info = dict(run_info())
    info.update({"applicationId": meta.get("applicationId", ""), "apks": apks})
    with open(os.path.join(args.out_dir, "build-info.json"), "w", encoding="utf-8") as f:
        json.dump(info, f, indent=2)
        f.write("\n")
    for apk in apks:
        print("%s  versionCode %s  versionName %s  %.1f MB" % (
            apk["file"], apk["versionCode"], apk["versionName"], apk["size"] / 1e6), file=sys.stderr)


def cmd_merge(args):
    infos = sorted(glob.glob(os.path.join(args.in_dir, "*", "build-info.json")))
    if not infos:
        fail("no */build-info.json under %s" % args.in_dir)
    os.makedirs(args.out_dir, exist_ok=True)
    merged = None
    apks = []
    for path in infos:
        with open(path, encoding="utf-8") as f:
            info = json.load(f)
        if merged is None:
            merged = {k: v for k, v in info.items() if k != "apks"}
        elif info.get("commit") != merged.get("commit"):
            fail("artifacts come from different commits: %s" % path)
        folder = os.path.dirname(path)
        for apk in info.get("apks", []):
            src = os.path.join(folder, apk["file"])
            if not os.path.isfile(src):
                fail("missing %s" % src)
            if sha256(src) != apk["sha256"]:
                fail("SHA-256 mismatch for %s" % src)
            dst = os.path.join(args.out_dir, apk["file"])
            if os.path.exists(dst):
                fail("two artifacts contain %s" % apk["file"])
            shutil.copy2(src, dst)
            apks.append(apk)
    apks.sort(key=lambda a: (a.get("flavor") != "arm64", a["file"]))
    versions = sorted({base_version(a.get("versionName", "")) for a in apks})
    if len(versions) != 1:
        fail("APKs have different versions: %s" % ", ".join(versions))
    merged["apks"] = apks
    merged["version_name"] = versions[0]
    with open(os.path.join(args.out_dir, "build-info.json"), "w", encoding="utf-8") as f:
        json.dump(merged, f, indent=2)
        f.write("\n")
    outputs = {"version_name": versions[0], "apk_count": str(len(apks))}
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as f:
            for key, value in outputs.items():
                f.write("%s=%s\n" % (key, value))
    for key, value in outputs.items():
        print("%s=%s" % (key, value))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command")
    make = sub.add_parser("make")
    make.add_argument("--apk-dir", required=True)
    make.add_argument("--flavor", required=True, choices=["arm64", "universal"])
    make.add_argument("--build-type", required=True, choices=["debug", "release"])
    make.add_argument("--out-dir", required=True)
    merge = sub.add_parser("merge")
    merge.add_argument("--in-dir", required=True)
    merge.add_argument("--out-dir", required=True)
    merge.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT", ""))
    args = parser.parse_args()
    if args.command == "make":
        cmd_make(args)
    elif args.command == "merge":
        cmd_merge(args)
    else:
        parser.print_help()
        sys.exit(2)


if __name__ == "__main__":
    main()
