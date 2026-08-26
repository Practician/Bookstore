#!/usr/bin/env python3
import os

VERSION = os.environ.get("VERSION_NAME", "unknown")

content = f"""### 📖 Книжная лавка (Bookstore) — Комбайн для Книгохранилища 4PDA

**Основные возможности:**
- 📚 Универсальный парсер книг (FB2, FB2.ZIP, EPUB, PDF)
- 🌐 Интеллектуальный переводчик (Gemini AI, Google PA, Edge Free, Custom AI)
- 🤖 Автоматизация публикации на форум 4PDA
- 🔍 Поиск по четырём источникам: Флибуста, Anna's Archive, Z-Library и Librain
- 📥 Доступные действия зависят от источника: скачивание FB2/EPUB, ссылка на файл или переход на страницу книги
- 🎨 UI: Современная тёмная тема Glassmorphism

**Требования:**
- Android 8.0+ (API 26)
- Размер: ~4.3 MB
"""

with open("release_notes.md", "w", encoding="utf-8") as f:
    f.write(content)

print(f"Release notes written (v{VERSION})")
