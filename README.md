# Nhóm 06 – Ứng dụng Chat Nhóm (Group Chat)

Học phần **Lập trình mạng máy tính** – Lớp 23DTHB1 – HUTECH.
Mô hình Client–Server, Socket TCP, server đa luồng, quản lý phòng, lưu lịch sử tin nhắn vào SQL Server.

## Cấu trúc

```
Nhom6-LTMMT/
├── chat-common/   Giao thức dùng chung (Protocol, ChatMessage, RoomInfo)
├── chat-server/   Server đa luồng + công cụ TestClient
├── chat-client/   Client Swing
├── database/      schema.sql
└── docs/          protocol.md (đọc trước khi code!)
```

## Phân công

| Thành viên | Phụ trách | Vị trí code |
|---|---|---|
| **Tạ Tuấn Phát** (NT) | Giao thức, lõi Server đa luồng, quản lý phòng, broadcast; review & merge | `chat-common`, `chat-server/.../server` |
| **Trần Lãng** | Mạng phía Client; JDBC + `JdbcMessageStore` (lưu/đọc lịch sử) | `chat-client/.../client/net`, `chat-server/.../server/store` |
| **Nguyễn Đình Quốc Bảo** | `schema.sql`; giao diện quản trị Server; kịch bản kiểm thử | `database/`, `chat-server/.../server/ui` |
| **Nguyễn Phan Hồng Huy** | Giao diện Client Swing; trình bày báo cáo | `chat-client/.../client/ui` |

**Cổng nối giữa các phần:**
- Bảo gắn giao diện quản trị qua `ServerListener` (nhớ dùng `SwingUtilities.invokeLater`).
- Lãng viết `JdbcMessageStore implements MessageStore`, rồi đổi 1 dòng trong `ServerMain`.
- Huy làm form, có các hàm như `appendMessage(...)`, `updateRoomList(...)` để Lãng gọi vào.

## Chạy thử (NetBeans)

1. **File → Open Project** → chọn thư mục `Nhom6-LTMMT` → tick **Open Required Projects**.
2. Chuột phải project cha **Nhom6-LTMMT** → **Clean and Build** (lần đầu cần mạng để tải Maven).
3. **Server có giao diện:** chuột phải `chat-server/.../server/ui/ServerAdminMain.java` → **Run File** → bấm **Start**.
   (Hoặc chạy console: `chat-server/.../server/ServerMain.java`.)
4. **Client giao diện:** chuột phải `chat-client/.../client/ClientMain.java` → **Run File**, nhập IP server, cổng 5000, tên hiển thị → **Kết nối**.
   Chạy `ClientMain` thêm lần nữa để mở client thứ 2 và chat qua lại.
5. Máy khác trong mạng LAN: nhập IP của máy chạy server (xem bằng `ipconfig`), nhớ cho phép Java qua Windows Firewall.

Lưu lịch sử vào SQL Server: chạy `database/schema.sql` trong SSMS, bật TCP/IP cho instance SQL Server,
copy `db.properties.example` thành `db.properties` và sửa url/user/password. Không có file này thì server tự lưu tạm trong RAM.

Lệnh trong cửa sổ server: `status`, `stop`.

## Đăng nhập / Đăng ký

- Lần đầu: ở màn hình đăng nhập bấm **"Chưa có tài khoản? Đăng ký"**, nhập tên, mật khẩu (từ 6 ký tự) 2 lần. Đăng ký xong vào chat luôn.
- Mật khẩu lưu trong SQL Server dưới dạng **đã băm PBKDF2 + salt** (bảng `Users`, cột `PasswordHash`, `PasswordSalt`), mở SSMS cũng không đọc được.
- Client nhớ IP, cổng và tên lần trước (không nhớ mật khẩu).
- `TestClient` / `ConsoleDemo`: `/register ten matKhau` hoặc `/login ten matKhau`.

## Giao diện client

- **Cột trái – Mọi người:** mọi tài khoản đã đăng ký, người online ở trên (chấm xanh), offline ở dưới (chấm xám). Bấm vào một người để mở **chat riêng**, kể cả người đang offline (số đỏ = tin riêng chưa đọc).
- **Giữa:** khung chat của phòng đang ở, hoặc khung chat riêng. Bấm tên phòng đang ở (cột phải) để quay lại chat phòng.
- **Cột phải:** khung **Phòng chat** (danh sách phòng, Tạo / Vào / Rời phòng) và khung **Thành viên phòng** (nháy đúp vào tên để nhắn riêng).

## Tin nhắn riêng

- Chỉ người gửi và người nhận thấy tin. Gửi được cả tin chữ lẫn file (tối đa 25 MB).
- Nhắn được cho **người đang offline**: tin được lưu lại, lúc họ đăng nhập sẽ thấy số đỏ đúng bằng số tin chưa đọc. Trạng thái đã đọc lưu ở cột `PrivateMessages.IsRead`.
- **Đã xem:** chat riêng hiện "✓ Đã xem" khi người kia đã mở tin cuối mình gửi; trong phòng hiện "✓ Đã xem: Lang, Huy" cho tin mới nhất.
- **Số đỏ của phòng:** phòng mình từng vào có tin mới (kể cả lúc mình offline) thì hiện số tin chưa đọc cạnh tên phòng (bảng `RoomReads`).
- Lịch sử tin riêng lưu trong bảng `PrivateMessages`, đăng nhập lại vẫn xem được.
- Đang chat riêng mà phòng có tin mới: tên phòng hiện số tin chưa đọc.

## Gửi file

- Trong phòng hoặc chat riêng, bấm **Gửi file** rồi chọn file (tối đa **25 MB**, mọi loại file). Nhấp vào tên file trong khung chat để chọn nơi lưu và tải về.
- Server lưu file trong thư mục `uploads/` cạnh nơi chạy server (đã chặn trong `.gitignore`).

## Nâng cấp CSDL

Máy đã tạo CSDL bằng `schema.sql` bản cũ: server **tự** thêm 3 cột file vào `Messages`, 2 cột mật khẩu vào `Users`, tạo bảng `PrivateMessages` và `RoomReads` khi khởi động (không mất dữ liệu cũ). Nếu server báo thiếu quyền thì mở SSMS chạy `database/nang-cap-csdl.sql` một lần.

Chi tiết giao thức: `docs/protocol.md` mục 6 (file), 7 (online, tin riêng), 8 (đăng nhập / đăng ký), 9 (đã xem, tin chưa đọc của phòng).

## Quy trình GitHub

1. Mỗi lần ngồi code: **Pull** `main` mới nhất.
2. Tạo nhánh riêng: `feature/<ten-phan>` (vd `feature/client-ui`, `feature/jdbc`).
3. Commit thường xuyên, message rõ ràng (vd `Them LoginFrame`).
4. Push nhánh → tạo **Pull Request** vào `main` → Phát review và merge.
5. **Không** push thẳng lên `main`, **không** sửa file của người khác khi chưa báo.
6. **Không** đưa mật khẩu SQL Server lên GitHub (để trong `db.properties`, đã nằm trong `.gitignore`).

## Tiến độ (03/10 – 28/11/2026)

| Tuần | Việc |
|---|---|
| 1–2 | Chốt giao thức, CSDL, phác thảo giao diện |
| 3–5 | Mỗi người code phần của mình |
| 6–7 | Tích hợp, kiểm thử nhiều client, chạy qua các mạng khác nhau |
| 8 | Hoàn thiện, viết báo cáo, chuẩn bị demo |
