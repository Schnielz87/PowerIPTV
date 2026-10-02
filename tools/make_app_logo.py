"""Erzeugt res/drawable/app_logo.xml aus den App-Icon-Ebenen (Hintergrund + Vordergrund)."""
import os, re
res = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")

def inner(name):
    s = open(os.path.join(res, name)).read()
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    return s[s.index(">", s.index("<vector")) + 1 : s.rindex("</vector>")].strip("\n")

xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- In-App-Logo = App-Icon (generiert von tools/make_app_logo.py) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="72dp" android:height="72dp"
    android:viewportWidth="72" android:viewportHeight="72">
    <group android:translateX="-18" android:translateY="-18">
        <clip-path android:pathData="M34,18h40a16,16 0,0 1,16,16v40a16,16 0,0 1,-16,16h-40a16,16 0,0 1,-16,-16v-40a16,16 0,0 1,16,-16z" />
{inner("ic_launcher_background.xml")}
{inner("ic_launcher_foreground.xml")}
    </group>
</vector>
'''
open(os.path.join(res, "app_logo.xml"), "w").write(xml)
print("app_logo.xml aktualisiert")
