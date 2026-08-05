#!/bin/zsh
set -e

ORIGINAL_IMAGE="/Users/jinliang/.gemini/antigravity/brain/6425843d-a95c-4c73-9570-dda25e6f1e4d/elevator_app_icon_1785844319665.jpg"
APP_RES_DIR="/Users/jinliang/Documents/elevator_qr_android/app/src/main/res"

make_icon() {
    FOLDER=$1
    SIZE=$2
    TARGET_DIR="$APP_RES_DIR/$FOLDER"
    mkdir -p "$TARGET_DIR"
    sips -z $SIZE $SIZE -s format png "$ORIGINAL_IMAGE" --out "$TARGET_DIR/ic_launcher.png" > /dev/null
    sips -z $SIZE $SIZE -s format png "$ORIGINAL_IMAGE" --out "$TARGET_DIR/ic_launcher_round.png" > /dev/null
}

make_icon "mipmap-mdpi" 48
make_icon "mipmap-hdpi" 72
make_icon "mipmap-xhdpi" 96
make_icon "mipmap-xxhdpi" 144
make_icon "mipmap-xxxhdpi" 192

echo "✅ App Launcher 桌面图标全套多分辨率 PNG 生成完毕！"
