# Port Valkaw Client 1.21.11 → 26.2 — Bản đồ chuyển đổi & Trạng thái hiện tại

> Trạng thái: **ĐÃ PORT XONG & ĐÃ BUILD XONG CLIENT + LAUNCHER CHO 26.2 — SẴN SÀNG TEST**.
> Toàn bộ file `.jar` (`sodium` + `vulkan`) cho 26.2 đã được build, chép vào `~/.valkaw/launcher/clients/26.2/` cũng như `~/.valkaw/{sodium,vulkan}/mods/valkawclient.jar`, và binary `valkaw-launcher/build/bin/valkaw-launcher` đã được biên dịch mới.

## 0. Hướng dẫn mở test (ĐÃ BUILD XONG)

Các lệnh build ở bước cuối đã chạy thành công (`go test ./...`, `go vet ./...`, `./build.sh` và `./gradlew jar` cho cả 2 profile):

```bash
# 1. Build lại 2 bản client 26.2 (để cập nhật fabric.mod.json "minecraft": ">=1.21.11" và text 26.2 ở Main Menu)
cd valkaw-client
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=sodium --no-configuration-cache --no-parallel
JAVA_HOME=$HOME/.local/share/jdk-25 ./gradlew jar -Pvalkaw_variant=vulkan --no-configuration-cache --no-parallel

# 2. Copy sẵn 2 jar 26.2 vào cache của launcher (~/.valkaw/launcher/clients/26.2/)
mkdir -p ~/.valkaw/launcher/clients/26.2
cp build/libs/valkawclient-1.0.6-sodium.jar ~/.valkaw/launcher/clients/26.2/sodium.jar
cp build/libs/valkawclient-1.0.6-vulkan.jar ~/.valkaw/launcher/clients/26.2/vulkan.jar

# 3. Test và build lại launcher Linux
cd ../valkaw-launcher
go test ./... && go vet ./... && ./build.sh
```

Sau khi chạy xong:
- Mở `./valkaw-launcher/build/bin/valkaw-launcher` (hoặc copy đè vào nơi bạn đang chạy launcher).
- Chọn phiên bản **26.2** trên launcher → bấm **Install** (lần đầu để tải vanilla 26.2 + Fabric 0.20.1) → bấm **Play** để test cả 2 profile **Sodium** và **VulkanMod**.

---

## 1. Những gì vừa sửa ở Launcher & Client để trỏ vào 26.2 (ĐÃ XONG TRONG CODE)

### A. Sửa lỗi Launcher không nhận / tự reset phiên bản 26.2 (`valkaw-launcher/core/`)
1. **`localBuildJar(version, profile)` (`core/install.go`)**:
   - Trước đó hàm tìm `valkawclient-26.2-sodium.jar` (dùng nhầm số version MC thay vì version mod `1.0.6` trong `gradle.properties`), dẫn tới không bao giờ tìm thấy file `valkaw-client/build/libs/valkawclient-1.0.6-{sodium,vulkan}.jar`.
   - Đã sửa: tự dò ngược thư mục cha từ cả `os.Executable()` và `os.Getwd()`, đọc `valkaw-client/gradle.properties` (`minecraft_version=26.2`, `version=1.0.6`), khi `minecraft_version == version` sẽ trỏ đúng vào `valkaw-client/build/libs/valkawclient-1.0.6-{sodium,vulkan}.jar`.
2. **Không tự reset `26.2` về `1.21.11` khi lưu cấu hình (`core/app.go` + `core/install.go`)**:
   - Trước đó `Startup()` và `SaveConfig()` kiểm tra `!fileExists(clientJarPath(c.Version, c.Profile))` → khi mới chọn `26.2` trên UI (chưa có file trong `~/.valkaw/launcher/clients/26.2/`), `SaveConfig` lập tức ghi đè `c.Version` về lại `1.21.11`.
   - Đã sửa: thay bằng `!isKnownVersion(version, profile)` (chấp nhận mọi version trong `baseVersions = ["1.21.11", "26.2"]`, version đã cache, hoặc có local build).
3. **Không ghi đè cache 26.2 bằng bản 1.21.11 trên GitHub Releases (`core/install.go`)**:
   - Trong `ensureClientJar`: nếu `version != defaultMCVersion` (`26.2`) và đã có file cache ở `~/.valkaw/launcher/clients/26.2/`, giữ nguyên bản cache thay vì tải jar `1.21.11` từ `/releases/latest`.
4. **Tự đồng bộ `mods/valkawclient.jar` khi bấm Play (`core/install.go`)**:
   - Vì cả `1.21.11` và `26.2` dùng chung `~/.valkaw/minecraft/{sodium,vulkan}/mods/valkawclient.jar`, hàm `Launch()` giờ tự gọi `ensureClientJar(versionID, profile)` + `installMods(profile)` trước khi quét mod trùng và khởi chạy game. Nhờ đó khi chuyển qua lại giữa `1.21.11` ↔ `26.2`, chuyển profile `Sodium` ↔ `VulkanMod`, hoặc vừa `./gradlew jar` xong là bấm Play sẽ nhận ngay jar mới nhất.
5. **Ưu tiên Java mới nhất (`findJava` trong `core/install.go`)**:
   - Đổi `r.major < bestMajor` thành `r.major > bestMajor` để ưu tiên JDK 25 (`~/.local/share/jdk-25`) vì Minecraft 26.2 và các mod 26.2 yêu cầu `java >= 25`.
6. **Thêm unit test `TestLocalBuildJarFor262` (`core/install_test.go`)**.

### B. Sửa metadata chặn khởi động trên 26.2 (`valkaw-client/`)
1. **`valkaw-client/src/main/resources/fabric.mod.json`**:
   - Trước đó `depends.minecraft` vẫn để `"~1.21.11"`, khiến Fabric Loader báo lỗi incompatible mod khi chạy với Minecraft `26.2`.
   - Đã đổi thành `">=1.21.11"` (chạy được cả 1.21.11 và 26.2).
2. **`valkaw-client/src/client/java/com/valkaw/client/gui/ValkawMainMenu.java`**:
   - Đổi dòng footer từ `"Minecraft 1.21.11"` sang `"Minecraft 26.2"`.

---

## 2. Build system 26.2 (ĐÃ GIẢI QUYẾT)

| Thành phần | 1.21.11 | 26.2 |
|---|---|---|
| minecraft_version | 1.21.11 | 26.2 |
| loader_version | 0.20.1 | 0.20.1 |
| Loom plugin | `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` |
| Loom version | 1.18-SNAPSHOT | **1.16.1** (bản Sodium 26.2 dùng) |
| Gradle | 9.8.2 | **9.4.1** |
| fabric_api | 0.141.6+1.21.11 | **0.161.0+26.2** |
| mappings | `loom.officialMojangMappings()` | **BỎ** — 26.x unobfuscated, không có `client_mappings` |

- **Không có task `remapJar`** ở 26.x (không remap) → logic nhét nested jars gắn vào task `jar`. Build bằng `./gradlew jar -Pvalkaw_variant=sodium` và `./gradlew jar -Pvalkaw_variant=vulkan`.

## 3. Fabric API / mod versions cho 26.2 (ĐÃ TÍCH HỢP)

| Mod | 26.2 version |
|---|---|
| fabric-api | 0.161.0+26.2 |
| sodium | mc26.2-0.9.2-fabric |
| sodium-extra | mc26.2-0.9.5+fabric |
| reeses-sodium-options | mc26.2-2.2.4+fabric |
| immediatelyfast | 1.16.5+26.2-fabric |
| moreculling | 1.8.2 |
| cloth-config | 26.2.155+fabric |
| particle-core | 0.3.3+fix1+26.2 |
| fzzy-config | 0.7.7+26.2 |
| fabric-language-kotlin | 1.14.2+kotlin.2.4.21 |
| dynamic-fps | 3.11.9 |
| lithium | mc26.2-0.25.3-fabric |
| ferrite-core | 9.0.0-fabric |
| krypton | 0.3.1 |
| badoptimizations | 2.4.1 |
| clumps | 26.2.1 |
| c2me-fabric | 0.4.2-alpha.0.66+26.2 |
| entityculling | 1.11.3 (mc26.2) |
| lambdynamiclights | 4.12.4+26.2 |
| shulkerboxtooltip | 5.4.3+26.2-fabric |
| lightweight-inventory-sorting | 1.1.7+26.2 |
| sodiumfpscapfix | 1.0.37 (26.2) |
| VulkanMod | 0.6.9-dev.526.2 (`libs/VulkanMod-0.6.9-dev.526.2.jar`) |

*(3 mod phụ chưa có bản 26.2 đã tạm lược khỏi `valkawExtras`: `greedy-meshing`, `chunksfadein`, `offlinemodefix`).*

## 4. Bản đồ API render 1.21.11 → 26.2 (ĐÃ PORT XONG)

- `GuiGraphics` → `GuiGraphicsExtractor` (`net.minecraft.client.gui.GuiGraphicsExtractor`)
- `Gui.render(GuiGraphics, DeltaTracker)` → `Gui.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`
- `Screen.render(GuiGraphics, ...)` → `Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`
- `drawString(...)` → `text(Font, ..., x, y, color, shadow)`
- `renderOutline(...)` → `outline(x1, y1, x2, y2, color)`
- `blit` / `blitSprite` → nhận `RenderPipelines.GUI_TEXTURED`
- `renderItem` → `item(ItemStack, x, y)`
- `pose()` trả `Matrix3x2fStack` (`pushMatrix`/`popMatrix`/`translate`/`scale` 2D)
- `LightTexture` → `Lighting` / `LightmapRenderState`
- `ChatFormatting.getColor()` / `getChar()` → `ValkawClient.formattingRgb(...)` / `ValkawClient.formattingCode(...)`
