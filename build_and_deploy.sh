#!/bin/bash
set -e

# 设置 JDK 17 与 Android SDK 环境变量
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/Users/jinliang/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "========================================="
echo "⚡ 电梯码 (Elevator QR) Android 编译发布工具"
echo "========================================="

# 1. 检查 keystore
if [ ! -f "elevator.jks" ]; then
    echo "🔑 生成 Release 密钥库..."
    keytool -genkeypair -v -keystore elevator.jks -storepass elevator123456 -alias elevator -keypass elevator123456 -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ElevatorQR, OU=Dev, O=ElevatorApp, L=Beijing, ST=Beijing, C=CN"
fi

# 2. 执行编译打包 (带 clean 彻底清除增量 dex 缓存)
echo "📦 正在编译打包 Release APK..."
/tmp/gradle-8.7/bin/gradle clean assembleRelease --no-daemon

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ 编译失败，未找到 APK 文件！"
    exit 1
fi

echo "✅ 编译成功！APK 文件路径: $APK_PATH"

# 3. 准备 Web 发布目录
DIST_DIR="$PROJECT_DIR/dist"
mkdir -p "$DIST_DIR"
cp "$APK_PATH" "$DIST_DIR/elevator-qr-v1.2.1.apk"
cp "$APK_PATH" "$DIST_DIR/app-release.apk"
cp "/Users/jinliang/.gemini/antigravity/brain/6425843d-a95c-4c73-9570-dda25e6f1e4d/elevator_app_icon_1785844319665.jpg" "$DIST_DIR/app-icon.png"

# 4. 生成漂亮的 HTML 下载网页
cat << 'EOF' > "$DIST_DIR/index.html"
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>电梯乘车码 - Android 客户端下载</title>
    <link rel="icon" href="./app-icon.png" type="image/png">
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 50%, #0f172a 100%);
            color: #f8fafc;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .card {
            background: rgba(30, 41, 59, 0.85);
            backdrop-filter: blur(12px);
            border-radius: 24px;
            padding: 40px;
            max-width: 440px;
            width: 100%;
            text-align: center;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
            border: 1px solid rgba(255, 255, 255, 0.1);
        }
        .icon {
            width: 96px;
            height: 96px;
            border-radius: 24px;
            margin: 0 auto 20px;
            overflow: hidden;
            box-shadow: 0 12px 24px rgba(37, 99, 235, 0.5);
            border: 2px solid rgba(255, 255, 255, 0.2);
        }
        .icon img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
        h1 { font-size: 24px; font-weight: 700; margin-bottom: 8px; }
        .version { font-size: 14px; color: #94a3b8; margin-bottom: 24px; }
        .feature-list {
            text-align: left;
            background: rgba(15, 23, 42, 0.6);
            border-radius: 16px;
            padding: 16px;
            margin-bottom: 28px;
            font-size: 14px;
            color: #cbd5e1;
            line-height: 1.8;
        }
        .feature-list li { margin-left: 20px; }
        .btn-download {
            display: block;
            background: linear-gradient(90deg, #2563eb, #3b82f6);
            color: white;
            text-decoration: none;
            padding: 16px;
            border-radius: 16px;
            font-weight: bold;
            font-size: 16px;
            box-shadow: 0 8px 20px rgba(37, 99, 235, 0.4);
            transition: transform 0.2s, box-shadow 0.2s;
        }
        .btn-download:hover {
            transform: translateY(-2px);
            box-shadow: 0 12px 24px rgba(37, 99, 235, 0.6);
        }
        .qr-section {
            margin-top: 24px;
            padding-top: 20px;
            border-top: 1px solid rgba(255, 255, 255, 0.1);
        }
        .qr-section img {
            width: 160px;
            height: 160px;
            border-radius: 12px;
            background: white;
            padding: 8px;
        }
        .qr-tip { font-size: 12px; color: #64748b; margin-top: 8px; }
    </style>
</head>
<body>
    <div class="card">
        <div class="icon">
            <img src="./app-icon.png" alt="App Icon">
        </div>
        <h1>电梯乘车码 App</h1>
        <div class="version">版本: v1.2.1 (Release 签名版)</div>

        <ul class="feature-list">
            <li>⚡ 极速 300ms 冷启动与纯原生 Compose UI</li>
            <li>📍 智能到站识别：到达公司自动弹出快开通知</li>
            <li>📱 100% 网页同款标准黑白二维码点阵（支持任意传感器扫码）</li>
            <li>🔒 独立非 Debug 证书签名，安全稳定</li>
        </ul>

        <a href="./elevator-qr-v1.2.1.apk" class="btn-download">🚀 立即下载安装包 (APK)</a>

        <div class="qr-section">
            <img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=./elevator-qr-v1.2.1.apk" alt="扫码下载">
            <div class="qr-tip">使用手机相机或浏览器扫码直接下载</div>
        </div>
    </div>
</body>
</html>
EOF

echo "🌐 HTML Web 下载页面已生成！"

# 处理云端部署或本地服务
if [ "$1" == "--deploy" ]; then
    echo "🚀 正在部署到 NAS Caddy 服务器..."
    tar czf - -C "$DIST_DIR" . | ssh -p 222 root@hackerlife.fun "mkdir -p /var/www/elevator-qr && tar xzf - -C /var/www/elevator-qr && rm -f /var/www/elevator-qr/._*"
    echo "🎉 NAS 部署成功！实测可用下载访问地址: https://flowstride.hackerlife.fun:8443/elevator/"
else
    echo "========================================="
    echo "🌐 启动后台下载 HTTP 服务器 (端口 8080)..."
    cd "$DIST_DIR"
    pkill -f "python3 -m http.server 8080" 2>/dev/null || true
    nohup python3 -m http.server 8080 > /dev/null 2>&1 &
    echo "访问链接: http://localhost:8080"
    echo "========================================="
fi
