# -*- coding: utf-8 -*-
"""Regeln wie in Android/Samsung (CategoryRules, ContentFilter, ParentalControl): Sprachen, Jugendschutz, Sortierung."""
import re

_LANG = re.compile(r"^\W*([A-Za-z]{2,4})\s*[|:\-–]")
_ADULT = re.compile(r"(xxx|adult|erotic|erotik|porn|\b18\s*\+|\+\s*18\b|for adults|nur für erwachsene)", re.I)
_YEAR = re.compile(r"\b(19[3-9]\d|20[0-4]\d)\b")


def category_language(name):
    m = _LANG.match(name or "")
    return m.group(1).upper() if m else None


def strip_language(name):
    m = _LANG.match(name or "")
    if not m:
        return name
    return re.sub(r"^[|:\-–\s]+", "", name[m.end():]).strip()


def detect_languages(categories):
    """Alle Praefixe, die mindestens zweimal vorkommen (sonst ist es kein Sprachschema)."""
    counts = {}
    for c in categories:
        l = category_language(c.get("name"))
        if l:
            counts[l] = counts.get(l, 0) + 1
    return sorted([k for k, v in counts.items() if v >= 2], key=lambda k: -counts[k])


def is_adult(name):
    return bool(_ADULT.search(name or ""))


def year_of(field, name):
    m = _YEAR.search(str(field or ""))
    if m:
        return int(m.group(1))
    m = re.search(r"\((19[3-9]\d|20[0-4]\d)\)", name or "")
    return int(m.group(1)) if m else 0


def matches(name, query):
    words = (query or "").lower().split()
    n = (name or "").lower()
    return all(w in n for w in words)


SORTS = [("DEFAULT", "Standard"), ("NAME_ASC", "A – Z"), ("NAME_DESC", "Z – A"),
         ("NEWEST", "Neu hinzugefügt"), ("RATING", "Beste Bewertung"), ("YEAR_DESC", "Neueste Jahre")]


def apply_sort(items, sort):
    l = list(items)
    key = {"NAME_ASC": (lambda i: (i.get("name") or "").lower(), False), "NAME_DESC": (lambda i: (i.get("name") or "").lower(), True),
           "NEWEST": (lambda i: i.get("added") or 0, True), "RATING": (lambda i: i.get("rating_f") or 0, True),
           "YEAR_DESC": (lambda i: i.get("year") or 0, True)}.get(sort)
    if key:
        l.sort(key=key[0], reverse=key[1])
    return l
