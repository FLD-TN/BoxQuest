# BoxQuest

Nhiệm vụ đào block trong các khu mine. Người chơi chọn một nhiệm vụ trong menu `/quest`, đào đủ số block yêu cầu trong
đúng region rồi nhận tiền và phần thưởng bằng lệnh. Mỗi lúc chỉ làm được một nhiệm vụ.

Paper 1.21.1, Java 21. Cần WorldGuard và Vault. BoxCore không bắt buộc; có thì phần thưởng được nhân theo boost
`quest`.

## Tính năng

- Menu `/quest` phân trang, 28 nhiệm vụ mỗi trang. Đầu người chơi ở hàng trên hiện nhiệm vụ đang làm, tiến độ và số
  lần đã hoàn thành. Chuột phải vào nhiệm vụ đang làm để huỷ.
- Mỗi người tự bật / tắt trong menu: tự nhận lại nhiệm vụ khi xong, âm thanh, bossbar, thanh tiến độ trên actionbar.
  Actionbar mặc định chỉ hiện lúc đang đào để không đè lên plugin khác.
- Chỉ đếm block đào thật: sự kiện đã bị plugin khác huỷ (ví dụ CataMines chặn cúp yếu) thì không tính.
- Thưởng: `rewards.money` (tiền Vault) và tối đa 10 lệnh console. Có BoxCore thì tiền được nhân theo hệ số boost
  `quest` (cho phép số lẻ như x1.5); trong lệnh dùng `{boost:N}` để nhân N theo hệ số đó.
- Giao nhiệm vụ từ NPC: `/quest offer` mở màn hình xác nhận cho người chơi, `/quest assign` gán thẳng. Nhiệm vụ có
  `npc-only: true` không hiện trong menu, chỉ nhận được qua hai lệnh này.
- Plugin khác bắt được `vn.boxpvp.boxquest.api.QuestCompleteEvent` mỗi lần có người hoàn thành nhiệm vụ (sau khi đã trả
  thưởng). BoxPet dùng sự kiện này để cộng XP cho pet.

Tiến độ lưu trong `plugins/BoxQuest/data.db` (SQLite), ghi xuống đĩa theo chu kỳ `storage.flush-interval-seconds`.

## Viết nhiệm vụ (`quests.yml`)

```yaml
quests:
  stone:
    display: '&7Đào Đá'
    icon: STONE
    description: 'Đào x50 Đá'
    objective:
      type: MINE_BLOCK
      regions: [1stone]        # region WorldGuard hình hộp, một hoặc nhiều
      blocks: [STONE]          # loại block được đếm
    amount: 50
    rewards:
      money: 2500
      commands:
        - command: 'boxcore gem give {player} {boost:2} quest:{quest}'
          display: '&d{boost:2} Gem'
```

Biến trong lệnh: `{player}`, `{uuid}`, `{quest}`, `{multiplier}`, `{boost:N}`. Lệnh chạy ngay lúc người chơi vừa đào
xong block, nên đừng dùng lệnh kick, dịch chuyển hay đặt/xoá block. Nhiệm vụ sai cấu hình (region không tồn tại, lệnh
sai kiểu) bị bỏ qua và có lý do trong log.

## Lệnh

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/quest` (`/nv`, `/nhiemvu`) | `boxquest.use` | Mở menu nhiệm vụ |
| `/quest offer <người chơi> <mã>` | `boxquest.assign` | Mở màn hình nhận nhiệm vụ cho người chơi (dùng cho NPC) |
| `/quest assign <người chơi> <mã>` | `boxquest.assign` | Gán nhiệm vụ không cần hỏi |
| `/quest reload` | `boxquest.reload` | Đọc lại config, quests và messages |

`boxquest.admin` gồm `boxquest.reload` và `boxquest.assign`. Console luôn dùng được `offer` và `assign`.

## Build

```
./gradlew build
```

Ra file `build/libs/BoxQuest-<version>.jar`. Repo chỉ chứa mã nguồn, không có jar nào ngoài
`gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `boxcore-api-1.2.0.jar`: build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
