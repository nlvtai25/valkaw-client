# Valkaw Launcher — Linux

Launcher riêng cho bản Linux của Valkaw client (Minecraft 1.21.11, Fabric).
Tải pack mod từ GitHub Releases, tự lo runtime Minecraft + Java, rồi chạy game.

## Yêu cầu

- Linux **x86_64** (bản 1.21.11 của Mojang chỉ có `natives-linux` x64, không có arm64),
  có Python 3.8+ (`python3`), không cần curl.
- Java 21: **không cần cài sẵn** — launcher tự tải Temurin 21 vào `~/.valkaw/jdk`
  nếu máy chưa có Java 21.
- Minecraft 1.21.11: dùng `~/.minecraft` nếu đã có (kể cả chỉ có bản vanilla),
  thiếu thì launcher tự tải client jar + libraries + assets từ Mojang vào
  `~/.valkaw/minecraft` (~250 MB lần đầu).
- Card đồ hoạ: Sodium cần OpenGL 3.3+ (Mesa/AMD/NVIDIA đều được).
  VulkanMod cần driver Vulkan (`vulkaninfo` chạy được).
- GUI dùng `tkinter`; thiếu thì launcher tự chạy chế độ dòng lệnh
  (`sudo apt install python3-tk` để có GUI).

## Cài

Chỉ **một file** `valkaw.py` (icon/logo đã nhúng sẵn). Tải về rồi:

```bash
python3 valkaw.py            # chạy ngay, không cần cài gì
python3 valkaw.py --install  # thêm Valkaw Launcher vào menu ứng dụng
```

`--install` copy launcher vào `~/.local/share/valkaw/`, tạo icon và file
`~/.local/share/applications/valkaw.desktop`. Sau đó mở bằng menu, hoặc:

```bash
python3 ~/.local/share/valkaw/valkaw.py
```

Gỡ: `rm -r ~/.local/share/valkaw ~/.local/share/applications/valkaw.desktop`
(`~/.valkaw` chứa save/config/pack vẫn giữ).

## Cập nhật

Launcher tự kiểm tra bản mới mỗi lần mở (1 request tới `releases/latest`, dùng chung call tải pack
nên không phát sinh request riêng):

- Có bản mới → hộp thoại **Yes / No / Cancel**. **Yes**: tải `Valkaw.Launcher-linux-<ver>.py`, kiểm cú
  pháp rồi ghi đè chính nó (hiệu lực từ lần mở sau). **No**: không hỏi lại đúng bản đó
  (`update.skip=<ver>` trong `settings.txt`). **Cancel**: im lặng, lần mở sau hỏi lại.
- Tắt hẳn kiểm tra: thêm dòng `update=0` vào `~/.valkaw/settings.txt`.
- Bản đang chạy không bao giờ bị thay bằng file hỏng: file tải về phải `compile()` được mới ghi đè.
- Kiểm tra logic update (không mạng, không GUI): `python3 valkaw.py --self-test`.

## Dùng

GUI có 5 tab ở cột trái (giống launcher Windows): **Changelog** (mặc định, bấm tiêu đề để thu gọn),
**RAM**, **JVM Args**, **Options** (Priority / After run), **Game Dir**. Đổi theme sáng/tối bằng nút góc
phải trên; danh sách user bằng nút `+` / `▼` cạnh ô Username.

| Việc | Cách |
| --- | --- |
| Chọn bản | ô `Client`: **Sodium** (OpenGL) hoặc **VulkanMod** (Vulkan) |
| Username | ô Username, hoặc `+` để thêm / `▼` để chọn user đã lưu |
| RAM | tab RAM: kéo thanh hoặc gõ số GB (1–64) |
| Thư mục game | tab Game Dir: để trống dùng `~/.valkaw/game_sodium` / `game_vulkan`; chọn thư mục thì Vulkan thêm hậu tố `_vulkan` |
| JVM args | tab JVM Args; nút "Ve mac dinh (G1)" khôi phục bộ G1 giống `launch.ps1` |
| Log | tab Game Dir → "Mo log", hoặc `~/.valkaw/log_launcher/<thư mục game>/latest.log` |

Chế độ dòng lệnh: `python3 valkaw.py --cli`; tải trước pack + runtime mà không chơi:
`python3 valkaw.py --prefetch`.

Cùng một máy chạy chung `~/.valkaw/settings.txt` với launcher Windows.

## Ghi chú kỹ thuật

- Classpath dùng dấu `:`, natives giải nén từ các jar `*natives-linux*.jar`
  vào `<gameDir>/natives`, truyền qua `-Djava.library.path`.
- Fabric loader lấy profile từ `meta.fabricmc.net` (mainClass +
  libraries), vanilla từ `piston-meta.mojang.com`; cả hai được cache trong
  `~/.valkaw/cache` để lần chạy sau / offline vẫn dùng được.
- Pack zip là bản dùng chung với Windows (`Sodium_v*.zip`, `Vulkan_v*.zip`);
  `mods/` luôn ghi đè, config/save chỉ ghi khi chưa có.
