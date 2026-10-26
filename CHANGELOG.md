# Changelog

## 0.3.1

### Thay đổi

- Lore nhiệm vụ chưa nhận bỏ dòng tiến độ và thanh tiến độ trống (luôn 0). Tiến độ chỉ hiện ở nhiệm vụ đang làm.
- Tiền thưởng ghi thống nhất `20.000$` ở mọi nơi: lore nhiệm vụ (trước là `$20.000`), màn hình xác nhận (trước là
  `20.000 Xu`) và tin nhắn chat (đã là `{money}$`). Dòng quy tắc trên đầu người chơi đổi "nhận Xu" thành "nhận tiền".
- `/quest offer` có cooldown theo từng người chơi (`config.yml` mục mới `offer.cooldown-seconds`, mặc định 1 giây,
  0 = tắt). Bấm NPC liên tục trong cooldown thì bỏ qua im lặng: không mở lại màn hình, không lặp tin báo lỗi.

### Hiệu năng

- Đầu người chơi trong menu `/quest` được dựng một lần cho mỗi lần mở menu; khi đào làm menu vẽ lại chỉ cập nhật tên
  và lore.

### Cấu hình

- `messages.yml`: sửa 6 dòng (`menu.quest.*.lore` dòng `{money}`, `menu.quest.available.lore` bỏ 3 dòng tiến độ,
  `accept-confirm.info.lore` dòng `{money}`, `menu.head.lore` dòng "nhận Xu"). **File trên server không tự đổi**: chép
  lại các dòng này bằng tay, hoặc xoá các khoá đó để lấy bản trong jar.
- `config.yml`: thêm `offer.cooldown-seconds` (thiếu thì dùng 1 giây).

## 0.3.0

### Mới

- **Giao nhiệm vụ bằng lệnh** (quyền mới `boxquest.assign`, nằm trong `boxquest.admin`; console luôn có):
  - `/quest offer <người chơi> <mã nhiệm vụ>`: mở màn hình xác nhận nhận nhiệm vụ cho người chơi. Dùng cho NPC,
    cài lệnh chạy bằng console. Bấm nhận hoặc không nhận đều đóng màn hình (không mở `/quest`). Người chơi đang làm
    nhiệm vụ khác thì không mở màn hình, báo lỗi vào chat người chơi.
  - `/quest assign <người chơi> <mã nhiệm vụ>`: gán thẳng, không hỏi. Đang làm nhiệm vụ khác thì không gán, báo cho
    cả người chơi và người gửi lệnh.
  - Có tab-complete tên người chơi và mã nhiệm vụ.
- **Tin nhắn chat khi nhận nhiệm vụ** (`feedback.accept`), dùng nguyên tên `display` trong quests.yml. Áp dụng cho
  nhận từ menu, NPC và `/quest assign`. Tự nhận lại khi hoàn thành không gửi tin này.
- Nhận nhiệm vụ khi đang làm nhiệm vụ khác hoặc đang làm đúng nhiệm vụ đó: báo lý do vào chat (`feedback.accept-busy`,
  `feedback.accept-same`).
- `quests.yml`: tuỳ chọn `npc-only: true` ẩn nhiệm vụ khỏi menu `/quest`, chỉ nhận được qua `offer` / `assign`. Người
  đang làm nhiệm vụ đó thấy nó ở ô đầu tiên trang 1 (xem tiến độ, chuột phải để huỷ).
- **Phân trang menu `/quest`**: bỏ giới hạn 21 nhiệm vụ. Mỗi trang 28 nhiệm vụ (7 x 4). Menu mở ở trang chứa nhiệm vụ
  đang làm; huỷ hoặc quay lại từ màn hình xác nhận thì giữ đúng trang đang xem.

### Thay đổi menu `/quest`

- Ô giữa hàng trên là **đầu người chơi**: nhiệm vụ hiện tại kèm tiến độ, số lần hoàn thành và các quy tắc chung
  (`menu.head`). Nhiệm vụ đang giữ đã bị xoá khỏi quests.yml thì chuột phải vào đầu để huỷ (trước đây là ô trạng thái).
- Bỏ dải kính xám và 3 ô kính xanh/đỏ báo trạng thái; hàng đó giờ là hàng nhiệm vụ thứ tư.
- Hàng dưới: Tự động nhận lại (45), Âm thanh (46) | Trang trước (48), Số trang (49), Trang sau (50) | Bossbar (52),
  Thanh tiến độ (53). Hết trang thì nút trước / sau là ô viền.

### Cấu hình

- `messages.yml`: mới `menu.head.*`, `menu.page.*`, `accept-confirm.close`, `feedback.accept*`, `command.usage-*`,
  `command.player-not-found`, `command.quest-not-found`, `command.target-not-loaded`, `command.assigned`,
  `command.assign-busy`. Bỏ `menu.status.*`, `toggle.indicator-*`, `log.quest.too-many` (file cũ còn các khoá này
  cũng không sao, có thể xoá). Khoá thiếu tự lấy từ bản trong jar.
- `config.yml`: thêm `gui.icons.page`, `previous-page`, `next-page`. Bỏ `gui.toggle-on`, `gui.toggle-off`,
  `gui.icons.no-quest` (không còn dùng).
- `plugin.yml` ghi đúng phiên bản (0.2.3 vẫn ghi 0.2.2).

### Không thay đổi

- Database, cơ chế đếm block và phần thưởng.

## 0.2.3

### Mới

- **Hệ số boost quest có thể lẻ** (x1.5, x2.5), khi BoxCore 1.4.0 khai báo `quest` với `decimal: true`.
  - Xu: nhân chính xác, làm tròn 2 chữ số thập phân (2.500 x1.5 = 3.750).
  - `{boost:N}` trong lệnh thưởng: lẻ 0,5 làm tròn lên ({boost:5} x1.5 = 8, {boost:3} x1.5 = 5).
  - `{multiplier}` trong lệnh in dạng `1.5`; menu vẫn hiện số chưa nhân và `{multiplier}` là 1 như cũ.
- `messages.yml`: khoá mới `feedback.money`, một dòng chat riêng báo số Xu nhận được (`&7Nhận được &e+{money} Xu`),
  gửi sau tin Gem và trước tin hoàn thành. Xu được cộng thẳng qua Vault nên plugin kinh tế không tự báo. Chỉ gửi khi Vault cộng
  thành công và số Xu > 0; đặt `''` để tắt. File `messages.yml` cũ tự lấy khoá này từ bản trong jar.
- Biên dịch với `boxcore-api-1.2.0.jar`. Vẫn chạy với BoxCore cũ (1.1.0 - 1.3.0): khi đó hệ số luôn nguyên như trước.

### Sửa lỗi / hiệu năng

- **TPS khi nhiều người hoàn thành nhiệm vụ**: database chuyển `synchronous=FULL` sang `NORMAL` (WAL). Lần ghi lúc
  hoàn thành, lúc thoát và flush định kỳ không còn chờ ổ đĩa xác nhận. Vẫn an toàn khi server crash / bị kill; chỉ khi
  mất điện hoặc OS sập mới có thể mất vài lần ghi cuối (tiến độ lùi về lần lưu trước, phải đào lại).
- Ghi lần hoàn thành thất bại: trạng thái trong RAM được khôi phục (không còn lưu một lần hoàn thành chưa trả thưởng
  ở lần flush sau); block kế tiếp tự thử hoàn thành lại. Log lỗi ghi database tối đa một dòng mỗi 10 giây.
- Flush định kỳ thất bại đúng lúc người chơi thoát: tiến độ của người đó được ghi lại ngay thay vì mất. Ghi lại vẫn
  lỗi thì log `log.database.lost` nêu UUID.
- RAM: mốc phiên bản ghi database của người đã thoát được dọn (trước đây tăng theo số người từng vào server).
- Thanh tiến độ action bar: đào nhiều block trong một tick (enchant nổ) giờ hiện số mới nhất ở tick sau, thay vì số
  của block đầu tiên cho tới lần gửi lại định kỳ.

### Không thay đổi

- `config.yml`, database. `quests.yml` chỉ đổi dòng chú thích về `{boost:N}`.

## 0.2.2

### Mới

- **Bossbar nhiệm vụ**: luôn hiện nhiệm vụ đang làm và tiến độ ở đầu màn hình, kể cả khi không đào, để người chơi
  không quên mình đang giữ nhiệm vụ nào. Thanh đầy dần theo `count/amount`; hoàn thành mà tự nhận lại thì về đầu
  thanh, không thì ẩn. Mỗi người tự bật/tắt trong menu.
- `config.yml`: mục `bossbar` (`color`, `style`, `update-ticks`) và `defaults.show-bossbar`. Màu mặc định GREEN, vì
  BoxCore đang dùng YELLOW (boost mining) và BLUE (boost quest).
- `messages.yml`: `bossbar.title`, `toggle.bossbar.*`, vài khoá log mới. Khoá thiếu tự lấy từ bản trong jar.

### Thay đổi menu

- Hàng dưới: ô 45 (góc trái) là nút **Tự động nhận lại** (không có ô kính báo trạng thái); ô 48 là nút **Bossbar**;
  ô 49, 50 giữ nguyên (Thanh tiến độ, Âm thanh).
- **Bỏ ô thống kê** (đầu người chơi). Dữ liệu `completions` và `total_money` vẫn lưu trong database. Khoá
  `menu.stats` trong `messages.yml` cũ không còn được dùng, có thể xoá.

### Database

- Tự thêm cột `show_bossbar` khi khởi động lần đầu, **bật** cho mọi người chơi cũ (`defaults.show-bossbar` chỉ áp
  dụng cho tài khoản mới). Hạ cấp về 0.2.1 vẫn đọc/ghi được database này.

## 0.2.1

### Mới

- `objective.regions`: một nhiệm vụ đào được ở nhiều region WorldGuard. Block nằm trong **bất kỳ** region nào đều được tính.
  ```yaml
  objective:
    type: MINE_BLOCK
    regions: [mine_cap_3, mine_cap_4]
    blocks: [STONE, COBBLESTONE]
  ```
- `region` và `regions` đều nhận một tên hoặc một danh sách tên. Khai báo cả hai thì gộp lại, tên trùng chỉ tính một lần.
- Một region trong danh sách không tồn tại trong WorldGuard: bỏ qua cả nhiệm vụ, kèm lý do trong log (như với `region`).
- Phần tử không phải tên region (số, rỗng, mục cấu hình con): bỏ qua cả nhiệm vụ, lý do `log.quest.reason.region-invalid`.

### Tương thích

- `region: a` chạy y hệt 0.2.0. Không cần sửa `quests.yml` hay `messages.yml` đang có.

## 0.2.0

Thưởng bằng lệnh console (đặc tả: `BOXQUEST_SPEC_UPDATE_01.md`).

### Mới

- `quests.yml`: khóa `rewards.commands` — tối đa 10 lệnh mỗi nhiệm vụ, chạy bằng **console** khi hoàn thành,
  ngay sau khi đã ghi đĩa và trả Xu. Mỗi phần tử là một chuỗi, hoặc map `command` + `display` (dòng hiện trong menu).
- Placeholder trong lệnh: `{player}`, `{uuid}`, `{quest}`, `{multiplier}`, `{boost:N}` (= N × hệ số boost `quest`
  của BoxCore, cùng hệ số với Xu). Trong menu, `{boost:N}` hiện N chưa nhân, giống Xu.
- Ví dụ trả Gem của BoxCore 1.3.0:
  ```yaml
  rewards:
    money: 20000
    commands:
      - command: 'boxcore gem give {player} {boost:50} quest:{quest}'
        display: '&b{boost:50} Gem'
  ```
  Tổng Gem đã nhận từ nhiệm vụ: `/boxcore gem check <player>` (reason `quest:<id>`).
- `rewards.money: 0` được phép: nhiệm vụ chỉ trả bằng lệnh, dòng Xu tự ẩn trong menu.
- Lệnh thất bại (lệnh không tồn tại, ví dụ BoxCore đang tắt, hoặc ném lỗi) ghi log **SEVERE** kèm nguyên văn lệnh
  để chạy lại bằng tay; các lệnh sau vẫn chạy, Xu vẫn được trả.
- Kiểm tra khi nạp: phần tử `rewards.commands` sai, lệnh rỗng/quá 256 ký tự, `{boost:…}` không hợp lệ làm **cả nhiệm vụ**
  bị bỏ qua kèm lý do trong log. Placeholder lạ và nhiệm vụ không có phần thưởng nào chỉ là cảnh báo.

### Chủ server cần làm tay

`messages.yml` đang có trên server **không** được tự sửa. Khóa mới (log, lý do lỗi) tự lấy từ bản trong jar, nhưng
để menu hiện phần thưởng lệnh, thêm các dòng `{reward}` sau (xem bản mặc định trong jar):

- `menu.quest.available.lore`, `active.lore`, `locked.lore`, `blocked.lore` — ngay dưới dòng `'&7• &a${money}'`:
  ```yaml
          - '&7• {reward}'
  ```
- `menu.status.active.lore` và `accept-confirm.info.lore` — ngay dưới dòng `'&7Phần thưởng: &e{money} Xu'`:
  ```yaml
        - '&7Phần thưởng: {reward}'
  ```

Không thêm thì plugin vẫn chạy bình thường, chỉ là menu không hiện phần thưởng lệnh (phần thưởng vẫn được trả).

`quests.yml` đang có trên server cũng không bị đổi; thêm `rewards.commands` vào nhiệm vụ nào muốn trả Gem.

### Thay đổi hành vi nhỏ

- Dòng lore chứa `{money}` bị bỏ khi nhiệm vụ có `money = 0` (trước đây hiện `$0`).
- Trong lore, một dòng chứa đồng thời `{money}` và `{reward}` chỉ mở rộng `{reward}`; `{money}` trên dòng đó thành rỗng.

### Tương thích

- `quests.yml` và `messages.yml` của 0.1.0 (không có `rewards.commands`) cho kết quả y hệt 0.1.0.
- Không đổi database, `config.yml`, API BoxCore (`boxcore-api-1.0.0.jar`).
- Hạ cấp về 0.1.0: bản cũ bỏ qua `rewards.commands` (chỉ trả Xu, không lỗi).
