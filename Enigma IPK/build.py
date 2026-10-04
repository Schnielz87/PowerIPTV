#!/usr/bin/env python3
"""
Baut das Enigma2-Paket (.ipk) ohne weitere Werkzeuge:
  python3 build.py [Versionsnummer]  ->  out/enigma2-plugin-extensions-poweriptv_<Version>_all.ipk
Eine .ipk ist ein ar-Archiv mit debian-binary, control.tar.gz und data.tar.gz.
"""
import io
import os
import sys
import tarfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src", "PowerIPTV")
TARGET = "usr/lib/enigma2/python/Plugins/Extensions/PowerIPTV"
VERSION = sys.argv[1] if len(sys.argv) > 1 else "1.1." + os.environ.get("BUILD_NUMBER", "0")
PKG = "enigma2-plugin-extensions-poweriptv"

CONTROL = """Package: %s
Version: %s
Architecture: all
Section: extra
Priority: optional
Maintainer: Portiva <noreply@github.com>
Homepage: https://github.com/Schnielz87/PowerIPTV
Description: Portiva - PowerIPTV: Live TV, Filme und Serien ueber Xtream Codes oder M3U-Link
""" % (PKG, VERSION)

POSTINST = """#!/bin/sh
echo ""
echo "Portiva - PowerIPTV %s installiert."
echo "Bitte die Benutzeroberflaeche (GUI) neu starten. Danach: Menue > Plugins > PowerIPTV"
exit 0
""" % VERSION

POSTRM = """#!/bin/sh
# Nur beim Deinstallieren aufraeumen (nicht beim Update)
if [ "$1" = "remove" ] || [ "$1" = "purge" ]; then
  rm -rf /usr/lib/enigma2/python/Plugins/Extensions/PowerIPTV
fi
exit 0
"""


def add_bytes(tar, name, data, mode=0o644):
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mode = mode
    info.mtime = int(time.time())
    info.uname = info.gname = "root"
    tar.addfile(info, io.BytesIO(data))


def add_dir(tar, name):
    info = tarfile.TarInfo(name)
    info.type = tarfile.DIRTYPE
    info.mode = 0o755
    info.mtime = int(time.time())
    info.uname = info.gname = "root"
    tar.addfile(info)


def targz(builder):
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode="w:gz", format=tarfile.GNU_FORMAT) as tar:
        builder(tar)
    return buf.getvalue()


def control_tar(tar):
    add_dir(tar, "./")
    add_bytes(tar, "./control", CONTROL.encode())
    add_bytes(tar, "./postinst", POSTINST.encode(), 0o755)
    add_bytes(tar, "./postrm", POSTRM.encode(), 0o755)


def data_tar(tar):
    parts = TARGET.split("/")
    for i in range(1, len(parts) + 1):
        add_dir(tar, "./" + "/".join(parts[:i]) + "/")
    for name in sorted(os.listdir(SRC)):
        path = os.path.join(SRC, name)
        if os.path.isfile(path) and not name.endswith(".pyc"):
            with open(path, "rb") as f:
                add_bytes(tar, "./%s/%s" % (TARGET, name), f.read())
    add_bytes(tar, "./%s/version.txt" % TARGET, VERSION.encode())


def ar(members):
    out = io.BytesIO()
    out.write(b"!<arch>\n")
    for name, data in members:
        header = "%-16s%-12d%-6d%-6d%-8s%-10d`\n" % (name, int(time.time()), 0, 0, "100644", len(data))
        out.write(header.encode())
        out.write(data)
        if len(data) % 2:
            out.write(b"\n")
    return out.getvalue()


def main():
    os.makedirs(os.path.join(HERE, "out"), exist_ok=True)
    ipk = ar([("debian-binary", b"2.0\n"), ("control.tar.gz", targz(control_tar)), ("data.tar.gz", targz(data_tar))])
    path = os.path.join(HERE, "out", "%s_%s_all.ipk" % (PKG, VERSION))
    with open(path, "wb") as f:
        f.write(ipk)
    print(path, len(ipk) // 1024, "KB")


if __name__ == "__main__":
    main()
