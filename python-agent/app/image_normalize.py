"""将 Word 提取的各类图片统一转为视觉 API 支持的 PNG。"""

from __future__ import annotations

import logging
import platform
import shutil
import subprocess
import uuid
from pathlib import Path

logger = logging.getLogger(__name__)

VISION_SUPPORTED = {".png", ".jpg", ".jpeg", ".gif", ".webp"}
VECTOR_SUFFIXES = {".emf", ".wmf"}


def _detect_suffix(path: Path) -> str:
    data = path.read_bytes()
    header = data[:12]
    if header.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    if header.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    if header.startswith(b"GIF87a") or header.startswith(b"GIF89a"):
        return ".gif"
    if header.startswith(b"BM"):
        return ".bmp"
    if len(data) > 12 and header[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return ".webp"
    if len(header) >= 4 and header[0:4] == b"\x01\x00\x00\x00":
        return ".emf"
    if len(header) >= 2 and header[0:2] == b"\xd7\xcd":
        return ".wmf"
    return path.suffix.lower()


def _output_path(src: Path) -> Path:
    return src.parent / f"{src.stem}_{uuid.uuid4().hex[:8]}_vision.png"


def _pillow_to_png(src: Path, dst: Path) -> Path:
    from PIL import Image

    with Image.open(src) as image:
        if image.mode not in ("RGB", "RGBA"):
            image = image.convert("RGB")
        elif image.mode == "RGBA":
            background = Image.new("RGB", image.size, (255, 255, 255))
            background.paste(image, mask=image.split()[3])
            image = background
        image.save(dst, format="PNG")
    return dst


def _convert_with_imagemagick(src: Path, dst: Path) -> Path:
    magick = shutil.which("magick") or shutil.which("convert")
    if not magick:
        raise RuntimeError("未找到 ImageMagick")

    cmd = [magick, str(src), str(dst)]
    if Path(magick).name.lower() == "magick":
        cmd = [magick, "convert", str(src), str(dst)]

    subprocess.run(cmd, check=True, capture_output=True, timeout=60)
    if not dst.exists() or dst.stat().st_size == 0:
        raise RuntimeError("ImageMagick 转换结果为空")
    return dst


def _convert_metafile_windows(src: Path, dst: Path) -> Path:
    src_str = str(src.resolve()).replace("'", "''")
    dst_str = str(dst.resolve()).replace("'", "''")
    script = f"""
Add-Type -AssemblyName System.Drawing
$src = '{src_str}'
$dst = '{dst_str}'
$img = $null
try {{
    $img = [System.Drawing.Imaging.Metafile]::FromFile($src)
}} catch {{
    $img = [System.Drawing.Image]::FromFile($src)
}}
$unit = [System.Drawing.GraphicsUnit]::Pixel
$bounds = $img.GetBounds([ref]$unit)
$width = [Math]::Max([int]$bounds.Width, 1)
$height = [Math]::Max([int]$bounds.Height, 1)
if ($width -lt 50) {{ $width = 2400 }}
if ($height -lt 50) {{ $height = 1800 }}
$bitmap = New-Object System.Drawing.Bitmap $width, $height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.Clear([System.Drawing.Color]::White)
$graphics.DrawImage($img, 0, 0, $width, $height)
$bitmap.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
$img.Dispose()
"""
    completed = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )
    if completed.returncode != 0:
        stderr = (completed.stderr or completed.stdout or "").strip()
        raise RuntimeError(stderr or "PowerShell 转换失败")
    if not dst.exists() or dst.stat().st_size == 0:
        raise RuntimeError("PowerShell 转换结果为空")
    return dst


def ensure_display_png(image_path: str) -> Path:
    """生成可在浏览器/Word 中展示的 PNG（不调用视觉 API）。"""
    return normalize_image_for_vision(image_path)


def normalize_image_for_vision(image_path: str) -> Path:
    src = Path(image_path)
    if not src.exists():
        raise FileNotFoundError(f"图片不存在: {image_path}")

    suffix = _detect_suffix(src)
    dst = _output_path(src)

    if suffix in VISION_SUPPORTED:
        if suffix in {".png"}:
            dst.write_bytes(src.read_bytes())
            return dst
        try:
            return _pillow_to_png(src, dst)
        except Exception:
            if suffix in {".jpg", ".jpeg"}:
                dst.write_bytes(src.read_bytes())
                return dst

    if suffix in {".bmp", ".tif", ".tiff"}:
        return _pillow_to_png(src, dst)

    errors: list[str] = []

    if platform.system() == "Windows" and suffix in VECTOR_SUFFIXES.union({".emf", ".wmf"}):
        try:
            logger.info("使用 Windows GDI+ 转换矢量图: %s", src.name)
            return _convert_metafile_windows(src, dst)
        except Exception as exc:
            errors.append(f"Windows转换失败: {exc}")

    try:
        logger.info("使用 ImageMagick 转换图片: %s", src.name)
        return _convert_with_imagemagick(src, dst)
    except Exception as exc:
        errors.append(f"ImageMagick失败: {exc}")

    if platform.system() == "Windows":
        try:
            return _convert_metafile_windows(src, dst)
        except Exception as exc:
            errors.append(f"Windows兜底失败: {exc}")

    detail = "；".join(errors) if errors else "未知原因"
    raise ValueError(f"图片格式 {suffix or src.suffix} 无法转为 PNG，豆包视觉 API 不支持。{detail}")
