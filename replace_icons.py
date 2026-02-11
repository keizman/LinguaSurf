"""
Replace all app icons in LinguaSurf project with the new linguasurf.png icon.
Source: D:\Download\linguasurf.png (1024x1024 PNG RGB)
"""
import os
import shutil
from PIL import Image

SOURCE_ICON = r"D:\Download\linguasurf.png"
PROJECT_ROOT = r"E:\git\goog_trans\LinguaSurf"
APP_SRC = os.path.join(PROJECT_ROOT, "Android-app", "app", "src")

# Load source image once
src_img = Image.open(SOURCE_ICON).convert("RGBA")
print(f"Source icon: {src_img.size}, mode={src_img.mode}")


def save_resized(img, size, output_path):
    """Resize source image to (size x size) and save as PNG with RGBA."""
    resized = img.resize((size, size), Image.LANCZOS)
    resized.save(output_path, "PNG")
    print(f"  [OK] {output_path} ({size}x{size})")


def save_resized_wh(img, w, h, output_path):
    """Resize source image to (w x h) and save as PNG with RGBA."""
    resized = img.resize((w, h), Image.LANCZOS)
    resized.save(output_path, "PNG")
    print(f"  [OK] {output_path} ({w}x{h})")


replaced_count = 0

# ============================================================
# 1. Extension icons (bundled in Android assets)
# ============================================================
print("\n=== 1. Extension icons (assets/extensions/linguasurf/icon/) ===")
ext_icon_dir = os.path.join(APP_SRC, "main", "assets", "extensions", "linguasurf", "icon")
for size in [16, 32, 48, 64, 128]:
    out = os.path.join(ext_icon_dir, f"{size}.png")
    save_resized(src_img, size, out)
    replaced_count += 1

# ============================================================
# 2. Extension logo (used inside extension pages)
# ============================================================
print("\n=== 2. Extension logo (assets/extensions/linguasurf/assets/logo-BdLZH4Fn.png) ===")
logo_path = os.path.join(APP_SRC, "main", "assets", "extensions", "linguasurf", "assets", "logo-BdLZH4Fn.png")
save_resized(src_img, 128, logo_path)
replaced_count += 1

# ============================================================
# 3. tmp_manifest/out icons (extension build output used for bundling)
# ============================================================
print("\n=== 3. tmp_manifest/out icons ===")
tmp_icon_dir = os.path.join(PROJECT_ROOT, "tmp_manifest", "out", "icon")
for size in [16, 32, 48, 64, 128]:
    out = os.path.join(tmp_icon_dir, f"{size}.png")
    save_resized(src_img, size, out)
    replaced_count += 1

tmp_logo = os.path.join(PROJECT_ROOT, "tmp_manifest", "out", "assets", "logo-BdLZH4Fn.png")
save_resized(src_img, 128, tmp_logo)
replaced_count += 1

# ============================================================
# 4. Android launcher icons - forkDebug
# ============================================================
print("\n=== 4. Android launcher icons - forkDebug ===")
fork_debug = os.path.join(APP_SRC, "forkDebug")

# ic_launcher-web.png (512x512)
save_resized(src_img, 512, os.path.join(fork_debug, "ic_launcher-web.png"))
replaced_count += 1

# mipmap variants
mipmap_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for mipmap, size in mipmap_sizes.items():
    mipmap_dir = os.path.join(fork_debug, "res", mipmap)
    for name in ["ic_launcher.png", "ic_launcher_round.png"]:
        out = os.path.join(mipmap_dir, name)
        if os.path.exists(out):
            save_resized(src_img, size, out)
            replaced_count += 1

# ============================================================
# 5. Android launcher icons - forkRelease
# ============================================================
print("\n=== 5. Android launcher icons - forkRelease ===")
fork_release = os.path.join(APP_SRC, "forkRelease")

for mipmap, size in mipmap_sizes.items():
    mipmap_dir = os.path.join(fork_release, "res", mipmap)
    for name in ["ic_launcher.png", "ic_launcher_round.png",
                 "ic_launcher_private.png", "ic_launcher_private_round.png"]:
        out = os.path.join(mipmap_dir, name)
        if os.path.exists(out):
            save_resized(src_img, size, out)
            replaced_count += 1

# ============================================================
# 6. Play Store / web icons (512x512)
# ============================================================
print("\n=== 6. Play Store / web icons (512x512) ===")
playstore_icons = [
    os.path.join(APP_SRC, "main", "ic_launcher-playstore.png"),
    os.path.join(APP_SRC, "debug", "ic_launcher-playstore.png"),
]
for p in playstore_icons:
    if os.path.exists(p):
        save_resized(src_img, 512, p)
        replaced_count += 1

# ============================================================
# 7. Wordmark logo (used in app UI - square icon part)
# ============================================================
print("\n=== 7. Wordmark logo icon ===")
wordmark_logo = os.path.join(fork_release, "res", "drawable", "ic_wordmark_logo.png")
if os.path.exists(wordmark_logo):
    # Original is 347x320, replace with square icon scaled to fit
    save_resized_wh(src_img, 347, 320, wordmark_logo)
    replaced_count += 1

# ============================================================
# 8. Logo wordmark images (logo + text composites, various DPI)
#    These contain the icon + text. Replace with just the icon scaled to match.
# ============================================================
print("\n=== 8. Logo wordmark composites (ic_logo_wordmark_normal/private) ===")
wordmark_sizes = {
    "drawable-mdpi": (434, 80),
    "drawable-hdpi": (651, 120),
    "drawable-xhdpi": (868, 160),
    "drawable-xxhdpi": (1302, 240),
    "drawable-xxxhdpi": (1736, 320),
}

for dpi_dir, (w, h) in wordmark_sizes.items():
    for name in ["ic_logo_wordmark_normal.png", "ic_logo_wordmark_private.png"]:
        out = os.path.join(fork_release, "res", dpi_dir, name)
        if os.path.exists(out):
            save_resized_wh(src_img, w, h, out)
            replaced_count += 1

# ============================================================
# 9. Search widget image
# ============================================================
print("\n=== 9. Search widget (fenix_search_widget.png) ===")
search_widget = os.path.join(fork_release, "res", "drawable-hdpi", "fenix_search_widget.png")
if os.path.exists(search_widget):
    # Original is 1312x232, replace with icon scaled to fit
    save_resized_wh(src_img, 1312, 232, search_widget)
    replaced_count += 1

# ============================================================
# 10. Wordmark text images (normal/private)
# ============================================================
print("\n=== 10. Wordmark text images ===")
for name in ["ic_wordmark_text_normal.png", "ic_wordmark_text_private.png"]:
    out = os.path.join(fork_release, "res", "drawable", name)
    if os.path.exists(out):
        orig = Image.open(out)
        w, h = orig.size
        save_resized_wh(src_img, w, h, out)
        replaced_count += 1

print(f"\n{'='*60}")
print(f"Done! Replaced {replaced_count} icon files total.")
print("Build outputs in gradle/build/ will be regenerated on next build.")
