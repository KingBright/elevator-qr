import shutil
import os

SRC_ICON = "/Users/jinliang/.gemini/antigravity/brain/6425843d-a95c-4c73-9570-dda25e6f1e4d/elevator_app_icon_1785844319665.jpg"
DIST_DIR = "/Users/jinliang/Documents/elevator_qr_android/dist"

os.makedirs(DIST_DIR, exist_ok=True)
shutil.copy(SRC_ICON, os.path.join(DIST_DIR, "app-icon.png"))

print("✅ 新版应用图标已同步保存至 Web dist 目录！")
