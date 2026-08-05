import os
import math
from PIL import Image, ImageDraw, ImageOps

ORIGINAL_IMAGE = "/Users/jinliang/.gemini/antigravity/brain/6425843d-a95c-4c73-9570-dda25e6f1e4d/elevator_app_icon_1785844319665.jpg"
APP_RES_DIR = "/Users/jinliang/Documents/elevator_qr_android/app/src/main/res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

# 1. 打开原始高分辨率图标
base_img = Image.open(ORIGINAL_IMAGE).convert("RGBA")

# 辅助函数：裁剪为圆形
def make_circular(img):
    size = img.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    result = img.copy()
    result.putalpha(mask)
    return result

# 生成各分辨率的 App Launcher 图标
for folder, px in sizes.items():
    folder_path = os.path.join(APP_RES_DIR, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    # 调整大小
    resized = base_img.resize((px, px), Image.Resampling.LANCZOS)
    
    # 保存标准图标
    square_path = os.path.join(folder_path, "ic_launcher.png")
    resized.save(square_path, "PNG")
    
    # 保存圆形图标
    round_img = make_circular(resized)
    round_path = os.path.join(folder_path, "ic_launcher_round.png")
    round_img.save(round_path, "PNG")

print("✅ App 桌面主图标与圆形图标生成完毕！")
