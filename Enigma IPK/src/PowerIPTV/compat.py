# -*- coding: utf-8 -*-
"""Python 2 (OpenPLi 7/8, aeltere Images) und Python 3 gemeinsam.

Unter Python 2 liefern json und .decode() Unicode-Texte; die Enigma2-Anzeige (eLabel, Listen) nimmt dort aber nur
UTF-8-Bytes ("str") und stuerzt sonst mit "TypeError: in method 'eLabel_setText'" ab (gruener Bildschirm).
native() wandelt deshalb alle Texte in die Form um, die das jeweilige Python erwartet.
"""
import sys

PY2 = sys.version_info[0] == 2


def native(o):
    """Unicode -> UTF-8-str (nur Python 2), auch in Listen/Woerterbuechern. Unter Python 3 unveraendert."""
    if not PY2:
        return o
    if isinstance(o, unicode):  # noqa: F821 (nur Python 2)
        return o.encode("utf-8")
    if isinstance(o, list):
        return [native(x) for x in o]
    if isinstance(o, tuple):
        return tuple(native(x) for x in o)
    if isinstance(o, dict):
        return dict((native(k), native(v)) for k, v in o.items())
    return o


_patched = [False]


def patch_gui():
    """Sicherheitsnetz unter Python 2: Texte fuer Anzeige-Elemente immer als UTF-8-str uebergeben."""
    if not PY2 or _patched[0]:
        return
    _patched[0] = True
    try:
        from Components.VariableText import VariableText
        orig = VariableText.setText

        def setText(self, text):
            return orig(self, native(text))
        VariableText.setText = setText
    except Exception:
        pass
    try:
        from Screens.Screen import Screen
        orig_title = Screen.setTitle

        def setTitle(self, title, *a, **k):
            return orig_title(self, native(title), *a, **k)
        Screen.setTitle = setTitle
    except Exception:
        pass
    try:
        from Components.MenuList import MenuList
        orig_list = MenuList.setList

        def setList(self, lst):
            return orig_list(self, native(lst))
        MenuList.setList = setList
    except Exception:
        pass
