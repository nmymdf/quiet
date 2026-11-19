# 一鍵安靜 — 給 Claude 的專案說明

**先讀 https://github.com/nmymdf/READFIRST（README.md 與其他檔案）：家族 App 的分工與規則。**
這份只寫這個 App 自己的事。

## 這是什麼
爸爸的 Android App：看電影、音樂會時點一下全部安靜（鈴聲靜音、勿擾完全靜音、螢幕最暗、來電全部拒接），再點一下還原。手機是 Galaxy S23 Ultra，手錶 Galaxy Watch7。

## 分工
- Android：爸爸與他的 Claude，本 repo `main` 分支。
- **iPhone 版：不需要**。

## 文件地圖
- `docs/SPEC.md`：規格與版本紀錄

## 常用指令
- 推到 `main` → GitHub Actions 編譯 → `apk` 分支的 `quiet-v<版本>.apk`
- 交付：下載 APK，包成 `quiet-v<版本>.zip` 傳給爸爸（他的手機不能直接下載 .apk）
- 金鑰：`app/archie.keystore`（與鬧鐘 App 同一把 archie 金鑰，SHA-256 8C:68:71:09:…:63:B6:DE）。雲端 Claude 無法設定 Actions 機密，所以跟鬧鐘 repo 一樣放在 repo 裡；workflow 也支援機密 `QUIET_KEYSTORE_B64`

## 目前狀態（每次改版更新）
- Android v0.2；iPhone 不需要
- 還沒決定的事：無
