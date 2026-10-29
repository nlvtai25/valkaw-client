# Valkaw Client v1.0.2 — Handoff

Ghi chú bàn giao: toàn bộ thay đổi đã làm cho client và launcher. Dùng khi cần tiếp tục công việc mà không có lịch sử chat.

## Trạng thái hiện tại

| Mục | Giá trị |
| --- | --- |
| Repo | https://github.com/nlvtai25/valkaw-client (public, branch `main`) |
| Commit mới nhất | `Release v1.0.2` |
| Tag / Release | `v1.0.1` — https://github.com/nlvtai25/valkaw-client/releases/tag/v1.0.1 (v1.0.2 không tạo Release, chỉ đẩy binary vào `assets/downloads/`) |
| Version client | `1.0.1` |
| Asset Release | `valkawclient-1.0.1-sodium.jar`, `valkawclient-1.0.1-vulkan.jar` |
| Launcher binary | `assets/downloads/valkaw-launcher-v1.0.2-linux-amd64` |
| Version launcher | `v1.0.2` |
| Target game | Minecraft Java 1.21.11, Fabric loader 0.20.1 |

## Cấu trúc repo

```
Minecraft Client/
├── README.md                    # trang chính repo
├── LICENSE                      # Apache 2.0
├── .gitignore
├── assets/downloads/            # binary launcher
├── valkaw-client/               # mod Fabric (Java)
└── valkaw-launcher/             # launcher (Go + Wails)
```

Không publish: `MCP-Reborn/` (chỉ để đọc code vanilla), `docs/`, `AGENTS.md`, `.directory`.

## Client — tính năng

Tiêu đề cửa sổ: `Valkaw <profile> v<version>` → `Valkaw Sodium v1.0.1` / `Valkaw Vulkan v1.0.1`.

### HUD (16 module)
ArmorHud, Combo, Coordinates, Cps, Crosshair, Fps, Keystrokes, PcInfo, Ping, PotionHud, ServerIp, SneakHud, Speed, SprintHud, Time, Tps.

- ArmorHud: **một module duy nhất**, chế độ Split là option bên trong Options.
- Coordinates: có toggle riêng `LAYOUT`, `DIMENSION`, `BIOME`, `DIRECTION` (mặc định SHOW hết).
- Mọi HUD module có `SIDE: LEFT/RIGHT` để bám mép trái/phải màn hình.
- HUD Editor mở bằng cách giữ `F6` (~0.3 s), kéo thả được, thả gần mép sẽ tự link trái/phải.

### Performance (5 module)
ClearFluids, Clouds, NoFog, Particles, RenderDistance.

### Utility (13 module)
BlockOutline, ChatTimestamps, CopyChat, CopyCoords, Freelook, Fullbright, NoBobbing, NoFire, NoHurtCam, NoPumpkin, ToggleSneak, ToggleSprint, Zoom.

- Freelook: giống Toggle Perspective nhưng camera xoay tự do, hướng nhân vật không đổi. Options: `WHEN PRESSING` = THIRD-PERSON BACK / THIRD-PERSON FRONT, `MODE` = TOGGLE / PRESSING (giữ phím).
- Ẩn panel TEXT STYLE (không có option màu/prefix) cho: Freelook, Copy Chat, Chat Timestamps, No Bobbing, No Hurt Cam, Copy Coords, No Fire, Clear Water/Lava, No Pumpkin, Zoom, Toggle Sprint, Toggle Sneak. Panel options của các module này rộng hết bề ngang.

### Tính năng khác
- **Main menu sci-fi** tự viết, không dùng mod thay thế.
- **Custom Symbols panel** trong chat (bấm `/` hoặc `T`): tab ngang 44×14 nằm ngay trên thanh chat, icon `✦`, hover hiện tooltip "Custom Symbols". Hai tab con:
  - `Symbols`: 2278 ký tự Unicode (17 block), grid 10×10, scroll 3 hàng/lần.
  - `Color`: 16 mã `&0`–`&f` kèm swatch màu, và 6 format (`&k` `&l` `&m` `&n` `&o` `&r`) kèm preview "Sample" để thấy hiệu ứng.
  - Click là chèn vào ô chat tại con trỏ qua `screen.insertText(text, false)`.
- **Crosshair Editor**: lưới pixel 15×15, ô bật có viền trắng cho dễ nhìn, nút CLEAR / CANCEL / SAVE.

## Client — sửa lỗi đáng nhớ

**Flicker main menu trên Vulkan (Beryl).**
Nguyên nhân: main menu là màn duy nhất dùng nhiều lớp nền alpha + sao nhấp nháy alpha mỗi frame; shader pass của Beryl blend mấy quad đó sai nên frame sáng/tối xen kẽ. Cách sửa **không tắt Beryl**: `ValkawMainMenu` kiểm tra `FabricLoader.isModLoaded("beryl")`, nếu có thì dùng nhánh render **opaque hoàn toàn** (gradient không alpha, sao tĩnh, panel/nút màu đục). Đã test 12 frame liên tiếp, độ sáng ổn định.

**Click lệch dòng trong Options.**
Danh sách vẽ và danh sách click lệch nhau 1 dòng (lúc vẽ thiếu `SIDE`). Sửa bằng cách dùng **chung một list** cho cả vẽ và click.

**Grid line lạ dưới footer menu chỉ có ở Vulkan.**
Do vòng lặp vẽ 4 đường ngang nằm trong nhánh Beryl. Đã bỏ hẳn, footer giờ một đường đục duy nhất, hai profile giống nhau.

## Client — tối ưu hiệu năng

**HUD hot spots (đã dọn, ~40 dòng):**
- `HudRenderer.layout()`: bỏ `new ArrayList<>()` mỗi frame → dùng pool `COLUMNS` + biến `used`; comparator thành hằng `BY_X_Y` / `BY_Y`.
- `Hud.width()`: cache width theo font, map tối đa 256 entry, reset khi đổi font.
- `Hud.component()`: bỏ `root.getString()` chỉ để test rỗng, dùng cờ `any`.
- `ArmorHudModule`: dùng lại mảng `ItemStack[4]` thay vì cấp phát mỗi frame.
- `PcInfoModule`: resolve đường dẫn sysfs GPU **một lần**, không `Files.list("/sys/class/drm")` mỗi 500 ms.

**Mod lồng trong jar (jar-in-jar).**
`valkawclient.jar` giờ chứa sẵn cả mod phụ, không cần thả file lẻ vào `mods/`.

| Profile | Mod lồng thêm |
| --- | --- |
| Sodium | entityculling, greedy-meshing, lambdynamiclights, sodiumfpscapfix |
| Vulkan | greedy-meshing, lambdynamiclights, ImmediatelyFast |

- Sodium: **22 nested jar**, runtime `Loading 129 mods`.
- Vulkan: **14 nested jar**, runtime `Loading 111 mods`.
- File nguồn nằm ở `valkaw-client/libs/` (5 file, ~4.1 MB, có commit vào repo).

Lý do phải tự đóng gói: Loom `include files(...)` báo `Attempted to nest artifact ... which is not a module component and has no capabilities`; còn `maven.modrinth:entityculling:1.11.3` trên Maven có metadata sai (`minecraft: 1.19.4`). Nên `build.gradle` có `doLast` tự viết lại zip: chép toàn bộ entry qua, sửa `fabric.mod.json` thêm đường dẫn vào mảng `jars`, rồi append từng jar vào `META-INF/jars/`.

**Greedy Meshing.**
Bản dùng đúng là project `greedy-meshing-mod` (id `yOnoFrVo`), **không phải** `greedy-meshing` (id `zxS4Jf8h` — bản này chỉ có mixin Sodium, không có Vulkan). Bản 0.6.1 có `greedy_meshing.vulkan.mixins.json` trỏ vào `BuildTask`, `ChunkTask`, `SPIRVUtils` của VulkanMod 0.6.8 — đã kiểm tra cả 3 class tồn tại.

**Kết quả đo:** cả hai profile cho FPS trung bình như nhau (200–300), nhưng Vulkan cảm giác mượt hơn.

## Launcher

**JVM flags** (`install.go`) — G1GC tuned:
```
-Xms<mem>M -Xmx<mem>M
-XX:+UnlockExperimentalVMOptions
-XX:+UseG1GC -XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=50 -XX:+AlwaysPreTouch
-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
```
Flag `-XX:+UnlockExperimentalVMOptions` là **bắt buộc**, thiếu là JVM chết ngay: `VM option 'G1NewSizePercent' is experimental`.

**Không còn nhúng jar client** (62 MB → 11 MB):
- Xoá `//go:embed assets/clients/*/*.jar` và `embed.FS`.
- Khi cài, launcher đọc tag mới nhất từ redirect của `https://github.com/nlvtai25/valkaw-client/releases/latest` (302 → `/releases/tag/v1.0.1`, bỏ tiền tố `v`), rồi tải `.../releases/latest/download/valkawclient-<client-version>-<profile>.jar`. Không còn `clientVersion` hard-code trong Go.
- Cache ở `~/.valkaw-launcher/clients/<mc-version>/<profile>.jar`; tải lỗi thì dùng cache cũ (chạy offline được).
- Cố tình **không dùng GitHub REST API** cho bước này: API không auth bị `HTTP 403` rate limit 60 req/giờ/IP. Cả redirect `releases/latest` và URL tĩnh `releases/latest/download/...` đều không bị giới hạn.
- `CheckUpdate` / `InstallUpdate` vẫn dùng API (hành động tự nguyện, tần suất thấp) — vẫn có thể 403 nếu IP chung.
- `InstallUpdate` lấy binary từ `assets/downloads/valkaw-launcher-<tag>-linux-amd64` trong repo, không tìm asset trong Release nữa (asset đó đã bị xoá).

**Cấu hình:** `~/.valkaw-launcher/config.json`. `gameDir` mặc định `~/.valkaw`, mặc định `memoryMB 4096`, `profile gl`, `afterRun hide`, `autoUpdate true`.

**Popup kiểm tra điều kiện (v1.0.2):**
- `CheckRequirements()` trong `install.go` trả `Prereq{java, javaText, files, filesText, graphics, graphicsApi, graphicsText, ready}`. `ready = java && files` — thiếu driver **không** chặn chơi.
- `InstallJava()` tải Temurin JRE 21 từ `api.adoptium.net/v3/binary/latest/21/ga/<os>/<arch>/jre/hotspot/normal/eclipse`, giải nén vào `~/.valkaw-launcher/java`, tìm `*/bin/java` (Linux/macOS) hoặc `*/bin/java.exe` (Windows), kiểm tra major ≥ 21 rồi ghi vào `javaPath`.
- `findJava` quét thêm thư mục JRE do launcher tự cài, nên lần sau không tải lại.
- `graphicsState(profile)` ở `gpu.go`: profile `gl` kiểm `libGL.so.1`, profile `vk` kiểm file `*.json` trong `/usr/share/vulkan/icd.d` hoặc `/etc/vulkan/icd.d` (Windows: `System32\vulkan-1.dll`; macOS: luôn false vì thiếu MoltenVK).
- Popup (`#prereqModal`) chỉ tự mở khi thiếu Java hoặc game files. Thiếu driver thì chỉ hiện khi bấm Play lần đầu, nút đổi thành `Continue anyway`, và không hỏi lại trong phiên đó.

## Build & deploy

Client:
```bash
cd valkaw-client
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew remapJar -Pvalkaw_variant=sodium --no-configuration-cache --no-parallel
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew remapJar -Pvalkaw_variant=vulkan --no-configuration-cache --no-parallel
cp build/libs/valkawclient-1.0.1-sodium.jar ~/.valkaw/sodium/mods/valkawclient.jar
cp build/libs/valkawclient-1.0.1-vulkan.jar ~/.valkaw/vulkan/mods/valkawclient.jar
```
`JAVA_HOME` phải trỏ JDK có `javac`; `/usr/lib/jvm/java-25-openjdk` là JRE nên build fail (`does not provide ... [JAVA_COMPILER]`).

Launcher:
```bash
cd valkaw-launcher
PATH=$HOME/go/bin:$PATH go test ./... && go vet ./...
PATH=$HOME/go/bin:$PATH ./build.sh        # wails build -tags webkit2_41
```

Git (identity chưa cấu hình, phải truyền inline):
```bash
git -c user.name=nlvtai25 -c user.email=nlvtai25@users.noreply.github.com commit -am "..."
git -c credential.helper='!/home/itzzparsher/.local/bin/gh auth git-credential' push origin main
```

## Việc còn lại / khoảng trống

1. **License còn placeholder** — `LICENSE` vẫn ghi `Copyright [yyyy] [name of copyright owner]`. Cần điền `Copyright 2026 nlvtai25`.
2. **Contributors chỉ hiện `nlvtai25`.** Các commit có `Co-authored-by: OpenCode <noreply@opencode.ai>` (5 commit) nhưng GitHub không có user nào tên OpenCode, và organization không hiện ở sidebar. Đây là hạn chế của GitHub, không sửa được bằng code.
3. **Release notes ghi launcher nằm trong repo** — nếu sau này muốn đẩy launcher lên Release thì phải sửa lại README + notes và `InstallUpdate`.
4. **Chưa test luồng bấm Install trên GUI** (môi trường này là Wayland native, không tự động click được). Đã test `ensureClientJar` + `installMods` bằng Go test, cả hai pass.

## Bài học khi làm tiếp

- Sau mỗi lần build, **game đang chạy vẫn giữ jar cũ**. Phải thoát hẳn rồi mở lại mới thấy thay đổi UI. Kiểm tra: `stat -c '%y' ~/.valkaw/sodium/mods/valkawclient.jar` so với `ps -o lstart= -p <java pid>`.
- Kiểm tra hằng số Java thật sự đã vào jar: `unzip -p <jar> com/valkaw/client/gui/X.class > /tmp/x.class && javap -p -c /tmp/x.class | grep bipush`.
- **Không dùng input tự động** (XTEST/ydotool). Đã một lần click lạc vào cửa sổ browser của người dùng. Chỉ dùng `spectacle` để chụp và `wmctrl -lG` để xem.
- Không chạy `pkill -f '<pattern>'` khi chuỗi đó cũng xuất hiện trong chính command line — nó tự giết shell đang chạy.
- `go test` với `HOME=<thư mục khác>` sẽ tạo module cache mới trong HOME đó; phải truyền kèm `GOMODCACHE=$HOME/go/pkg/mod GOPATH=$HOME/go`.
- GitHub API không auth bị rate limit theo IP → dùng `gh api` thay `curl`.
- File >50 MB trong git sẽ bị GitHub cảnh báo (chặn cứng ở 100 MB). Hiện không còn file nào vượt ngưỡng.
